package com.bahikhata.app.util

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.bahikhata.app.data.Entry
import com.bahikhata.app.data.Party
import com.bahikhata.app.data.Repo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * v1.0.44 — sends an SMS straight from the phone's SIM (no Messages app), and waits for the
 * phone's own "sent" answer so the app can say "SMS bhej diya" or "SMS nahi gaya" truthfully.
 */
object SmsSender {
    sealed class Result {
        data object Sent : Result()
        /** Handed to the phone, but no answer within the wait (slow network). */
        data object Pending : Result()
        data class Failed(val reason: String) : Result()
    }

    /** App-wide scope for sends that must outlive a closing sheet (entry SMS). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun hasPhoneState(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    /** Number of SIM slots on this phone (no permission needed). */
    fun simSlots(ctx: Context): Int = try {
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (Build.VERSION.SDK_INT >= 30) tm.activeModemCount else @Suppress("DEPRECATION") tm.phoneCount
    } catch (e: Throwable) { 1 }

    /** Subscription chosen in Settings ("SMS kis SIM se"), or null for the phone's default SMS SIM. */
    private fun chosenSubId(ctx: Context): Int? {
        val slot = S.smsSim.toIntOrNull() ?: 0
        if (slot <= 0 || !hasPhoneState(ctx)) return null
        return try {
            SubscriptionManager.from(ctx).getActiveSubscriptionInfoForSimSlotIndex(slot - 1)?.subscriptionId
        } catch (e: Throwable) { null }
    }

    private fun manager(ctx: Context): SmsManager {
        val sub = chosenSubId(ctx)
        return if (Build.VERSION.SDK_INT >= 31) {
            val base = ctx.getSystemService(SmsManager::class.java)
            if (sub != null) base.createForSubscriptionId(sub) else base
        } else {
            @Suppress("DEPRECATION")
            if (sub != null) SmsManager.getSmsManagerForSubscriptionId(sub) else SmsManager.getDefault()
        }
    }

    private fun number(phone: String): String {
        val n = normPhone(phone)
        return if (n.isNotEmpty()) "+$n" else phone.filter { it.isDigit() || it == '+' }
    }

    private fun reason(code: Int): String = when (code) {
        SmsManager.RESULT_ERROR_NO_SERVICE -> "Network / signal nahi hai."
        SmsManager.RESULT_ERROR_RADIO_OFF -> "Phone flight mode me hai ya SIM band hai."
        SmsManager.RESULT_ERROR_NULL_PDU -> "Message ban nahi paya."
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "SIM ne SMS nahi bheja (balance / SIM check karein)."
        else -> "SIM ne SMS nahi bheja (code $code)."
    }

    suspend fun send(ctx: Context, phone: String, text: String, waitMs: Long = 30_000): Result {
        val app = ctx.applicationContext
        val num = number(phone)
        if (num.filter { it.isDigit() }.length < 3) return Result.Failed("Mobile number sahi nahi hai.")
        if (!hasPermission(app)) return Result.Failed("SMS bhejne ki permission nahi hai.")
        if (text.isBlank()) return Result.Failed("Message khaali hai.")
        val sm = try { manager(app) } catch (e: Throwable) { return Result.Failed("Is phone se SMS nahi bhej sakte (SIM nahi mili).") }
        val parts = try { sm.divideMessage(text) } catch (e: Throwable) { arrayListOf(text) }
        val n = parts.size.coerceAtLeast(1)
        val action = "${app.packageName}.SMS_SENT.${System.nanoTime()}"
        val done = CompletableDeferred<Result>()
        var ok = 0
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (resultCode == Activity.RESULT_OK) {
                    ok++
                    if (ok >= n) done.complete(Result.Sent)
                } else done.complete(Result.Failed(reason(resultCode)))
            }
        }
        val flags = android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
        val pis = ArrayList((0 until n).map { idx ->
            android.app.PendingIntent.getBroadcast(app, idx, Intent(action).setPackage(app.packageName), flags)
        })
        try {
            ContextCompat.registerReceiver(app, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (e: Throwable) { }
        return try {
            if (n == 1) sm.sendTextMessage(num, null, parts.firstOrNull() ?: text, pis[0], null)
            else sm.sendMultipartTextMessage(num, null, parts, pis, null)
            withTimeoutOrNull(waitMs) { done.await() } ?: Result.Pending
        } catch (e: SecurityException) {
            Result.Failed("SMS bhejne ki permission nahi hai.")
        } catch (e: Throwable) {
            Result.Failed("SMS nahi gaya: " + (e.message ?: "phone ne mana kar diya").take(80))
        } finally {
            try { app.unregisterReceiver(receiver) } catch (e: Throwable) { }
        }
    }

    /** Rough SMS count for a text (160 chars per SMS in plain English, 70 when it has Hindi/emoji). */
    fun partsCount(text: String): Int {
        if (text.isEmpty()) return 0
        val gsm = text.all { it.code < 128 && it != '`' && it != '^' && it != '{' && it != '}' && it != '\\' && it != '[' && it != ']' && it != '~' && it != '|' }
        val (one, multi) = if (gsm) 160 to 153 else 70 to 67
        return if (text.length <= one) 1 else (text.length + multi - 1) / multi
    }
}

/**
 * Reminder history per party (last 10), kept in the settings table so it rides the normal
 * backup and cloud sync. No database change. Value: "millis:VIA,millis:VIA".
 */
object ReminderLog {
    private fun key(id: Long) = "remlog_$id"

    fun add(partyId: Long, via: String) {
        try {
            val now = System.currentTimeMillis()
            val list = (listOf(now to via) + all(partyId)).take(10)
            Repo.setSetting(key(partyId), list.joinToString(",") { "${it.first}:${it.second}" })
        } catch (e: Throwable) { }
    }

    private fun parse(v: String): List<Pair<Long, String>> = v.split(",").mapNotNull { s ->
        val i = s.indexOf(':'); if (i <= 0) null else s.substring(0, i).toLongOrNull()?.let { it to s.substring(i + 1) }
    }

    fun all(partyId: Long): List<Pair<Long, String>> = try { parse(Repo.setting(key(partyId), "")) } catch (e: Throwable) { emptyList() }

    fun last(partyId: Long): Pair<Long, String>? = all(partyId).firstOrNull()

    /** partyId -> last reminder time, for the whole list in one query. */
    fun lastAll(): Map<Long, Long> = try {
        Repo.settingsWithPrefix("remlog_").mapNotNull { (k, v) ->
            val id = k.removePrefix("remlog_").toLongOrNull() ?: return@mapNotNull null
            parse(v).firstOrNull()?.let { id to it.first }
        }.toMap()
    } catch (e: Throwable) { emptyMap() }
}

object SmsTexts {
    private fun bal(p: Party) = when {
        p.balance > 0.005 -> "baaki ${rupees(p.balance)}"
        p.balance < -0.005 -> "aapka advance ${rupees(-p.balance)}"
        else -> "hisaab barabar"
    }

    /** Short statement for SMS: balance, last 3 entries, shop. */
    fun statement(p: Party, entries: List<Entry>, shop: String): String {
        val last = entries.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.id }).take(3)
        val sb = StringBuilder("Namaste ${p.name} ji, $shop ka hisaab: ${bal(p)}.")
        if (last.isNotEmpty()) {
            sb.append("\nPichhli entry:")
            last.forEach { e -> sb.append("\n${fmtDate(e.date)} ${if (e.gave) "Udhaar" else "Jama"} ${rupees(e.amount)}") }
        }
        if (p.balance > 0.005 && S.reminderUpi && S.upiId.isNotEmpty()) sb.append("\nUPI: ${S.upiId}")
        sb.append("\nDhanyavaad")
        return sb.toString()
    }

    /** SMS after a new entry. */
    fun entry(p: Party, gave: Boolean, amount: Double, shop: String): String =
        "Namaste ${p.name} ji, $shop: aapke khate me ${rupees(amount)} " +
            (if (gave) "udhaar likha gaya" else "jama hua") + ". Ab ${bal(p)}. Dhanyavaad"

    fun reminder(p: Party, shop: String): String =
        if (p.balance > 0.005) S.reminderText(p.name, rupees(p.balance), shop, true)
        else "Namaste ${p.name} ji, $shop ke saath aapka hisaab: ${bal(p)}. Dhanyavaad 🙏"
}

/** v1.0.44: optional SMS to the party after each new entry (Settings, OFF by default). */
object EntrySms {
    /** Direct SMS that survives a closing sheet (bill share): SIM if allowed, else the Messages app. */
    fun direct(ctx: Context, phone: String, text: String, name: String) {
        val app = ctx.applicationContext
        if (!S.smsDirect || !SmsSender.hasPermission(app)) { smsTo(ctx, phone, text); return }
        SmsSender.appScope.launch {
            val r = SmsSender.send(app, phone, text)
            val msg = if (r is SmsSender.Result.Failed) "SMS nahi gaya ($name): ${r.reason} Dobara try karein." else "SMS bhej diya ✅ $name"
            try { android.widget.Toast.makeText(app, msg, android.widget.Toast.LENGTH_LONG).show() } catch (e: Throwable) { }
        }
    }

    fun maybeSend(ctx: Context, partyId: Long, gave: Boolean, amount: Double) {
        try {
            if (!S.entrySms || !S.smsDirect) return
            val app = ctx.applicationContext
            if (!SmsSender.hasPermission(app)) return
            val p = Repo.party(partyId) ?: return
            if (p.phone.isBlank()) return
            val text = SmsTexts.entry(p, gave, amount, Repo.businessName())
            SmsSender.appScope.launch {
                val r = SmsSender.send(app, p.phone, text)
                val msg = if (r is SmsSender.Result.Failed) "Entry ka SMS nahi gaya (${p.name}): ${r.reason}" else "Entry ka SMS ${p.name} ko bhej diya ✅"
                try { android.widget.Toast.makeText(app, msg, android.widget.Toast.LENGTH_SHORT).show() } catch (e: Throwable) { }
            }
        } catch (e: Throwable) { }
    }
}
