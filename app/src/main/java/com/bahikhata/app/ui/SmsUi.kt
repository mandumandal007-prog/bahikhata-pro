package com.bahikhata.app.ui

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bahikhata.app.data.Party
import com.bahikhata.app.data.Repo
import com.bahikhata.app.util.Pdf
import com.bahikhata.app.util.ReminderLog
import com.bahikhata.app.util.S
import com.bahikhata.app.util.SmsSender
import com.bahikhata.app.util.SmsTexts
import com.bahikhata.app.util.rupees
import com.bahikhata.app.util.smsTo
import com.bahikhata.app.util.whatsapp
import com.bahikhata.app.util.whatsappFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// v1.0.44 — direct SMS (one tap, no Messages app) and one-tap WhatsApp statement.

data class SmsJob(val phone: String, val text: String, val name: String, val partyId: Long?, val via: String = "SMS")

class SmsController {
    internal var onSend: (SmsJob) -> Unit = {}
    internal var onQuiet: (SmsJob) -> Unit = {}
    internal var onPerm: ((Boolean) -> Unit) -> Unit = { it(false) }

    /** Tap on an SMS button: direct send (asks permission once), else the Messages app. */
    fun send(phone: String, text: String, name: String, partyId: Long? = null) = onSend(SmsJob(phone, text, name, partyId))

    /** Extra SMS beside another action (WhatsApp): only when direct SMS is on and already allowed. */
    fun sendIfAllowed(phone: String, text: String, name: String, partyId: Long? = null) = onQuiet(SmsJob(phone, text, name, partyId))

    fun withPermission(cb: (Boolean) -> Unit) = onPerm(cb)
}

private fun toast(ctx: Context, s: String) = try { Toast.makeText(ctx, s, Toast.LENGTH_LONG).show() } catch (e: Throwable) { }

@Composable
fun rememberSmsSender(): SmsController {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = remember { SmsController() }
    var rationale by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var waiting by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var sending by remember { mutableStateOf<SmsJob?>(null) }
    var failed by remember { mutableStateOf<Pair<SmsJob, String>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) Repo.setSetting("sms_perm_denied", "1")
        val cb = waiting
        waiting = null
        cb?.invoke(ok)
    }

    fun go(job: SmsJob, showWait: Boolean) {
        if (showWait) sending = job
        scope.launch {
            val r = SmsSender.send(ctx, job.phone, job.text)
            if (showWait) sending = null
            if (r is SmsSender.Result.Failed) failed = job to r.reason
            else {
                job.partyId?.let { ReminderLog.add(it, job.via) }
                toast(ctx, "SMS bhej diya ✅ ${job.name}" + if (r is SmsSender.Result.Pending) " (network dheema hai, thodi der me pahunchega)" else "")
            }
        }
    }

    c.onPerm = { cb ->
        when {
            SmsSender.hasPermission(ctx) -> cb(true)
            Repo.setting("sms_perm_denied", "0") == "1" -> cb(false)
            else -> rationale = cb
        }
    }
    c.onSend = { job ->
        if (!S.smsDirect) smsTo(ctx, job.phone, job.text)
        else c.onPerm { ok -> if (ok) go(job, true) else smsTo(ctx, job.phone, job.text) }
    }
    c.onQuiet = { job -> if (S.smsDirect && SmsSender.hasPermission(ctx)) go(job, false) }

    rationale?.let { cb ->
        IosAlert(
            onDismissRequest = { rationale = null; cb(false) },
            title = { Text("SMS seedha bhejein?") },
            text = { Text("Tap karte hi customer ko SMS chala jayega — Messages app kholne ki zaroorat nahi. Iske liye agle screen par \"Allow\" dabayein. SMS aapke SIM se jayega (normal SMS charge lag sakta hai).") },
            confirmButton = {
                TextButton(onClick = { rationale = null; waiting = cb; launcher.launch(Manifest.permission.SEND_SMS) }) {
                    Text("Theek hai", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { rationale = null; Repo.setSetting("sms_perm_denied", "1"); cb(false) }) { Text("Abhi nahi", fontSize = 17.sp) }
            }
        )
    }
    sending?.let { job ->
        Dialog(onDismissRequest = { sending = null }) {
            Row(Modifier.clip(RoundedCornerShape(14.dp)).background(ios.alertBg).padding(horizontal = 22.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = ios.tint)
                Spacer(Modifier.width(14.dp))
                Text("SMS bhej rahe hain… ${job.name}", fontSize = 16.sp, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    failed?.let { (job, why) ->
        IosAlert(
            onDismissRequest = { failed = null },
            icon = { Icon(Icons.Filled.ErrorOutline, null, tint = GaveRed, modifier = Modifier.size(32.dp)) },
            title = { Text("SMS nahi gaya") },
            text = { Text("${job.name}: $why\nSignal aur SIM dekh kar dobara bhejein.") },
            confirmButton = { TextButton(onClick = { failed = null; go(job, true) }) { Text("Dobara bhejein", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { failed = null; smsTo(ctx, job.phone, job.text) }) { Text("Messages app", fontSize = 17.sp) } }
        )
    }
    return c
}

/**
 * One tap WhatsApp: makes the party's statement PDF and opens WhatsApp straight in that
 * party's chat with the PDF and the reminder text ready (the user only presses Send —
 * WhatsApp does not let any app press Send for you). If "WhatsApp ke saath SMS" is on and
 * SMS is allowed, a short SMS statement also goes automatically.
 */
fun whatsappStatement(ctx: Context, party: Party, sms: SmsController?) {
    val shop = Repo.businessName()
    val entries = try { Repo.entriesOf(party.id) } catch (e: Throwable) { emptyList() }
    val text = SmsTexts.reminder(party, shop)
    val file = try { Pdf.statement(ctx, party, entries, shop, Repo.setting("phone", "")) } catch (e: Throwable) { null }
    if (file != null) whatsappFile(ctx, file, "application/pdf", party.phone, text) else whatsapp(ctx, party.phone, text)
    ReminderLog.add(party.id, "WhatsApp")
    if (sms != null && S.waSms) sms.sendIfAllowed(party.phone, SmsTexts.statement(party, entries, shop), party.name, null)
}

/**
 * Sabko reminder (v1.0.44): SMS mode sends to everyone automatically, one by one with a
 * 2-second gap and a progress bar (after a confirm). WhatsApp mode opens each chat with the
 * statement PDF ready; the user presses Send and comes back for the next one.
 */
@Composable
fun BulkReminderSheet(parties: List<Party>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val shop = remember { Repo.businessName() }
    val sms = rememberSmsSender()
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val all = remember { parties.filter { it.phone.isNotBlank() && it.balance > 0.005 } }
    val noPhone = parties.size - all.size
    var queue by remember { mutableStateOf(all) }
    var mode by remember { mutableStateOf(if (S.smsDirect) Repo.setting("bulk_mode", "SMS") else "WA") }
    var idx by remember { mutableIntStateOf(0) }
    var sent by remember { mutableIntStateOf(0) }
    var fails by remember { mutableStateOf(listOf<Pair<Party, String>>()) }
    var running by remember { mutableStateOf<Job?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var manualSms by remember { mutableStateOf(false) }
    var stopNote by remember { mutableStateOf<String?>(null) }
    val started = idx > 0 || running != null
    val progress by animateFloatAsState(if (queue.isEmpty()) 1f else idx.toFloat() / queue.size, label = "bulk")

    fun runAuto() {
        stopNote = null
        running = scope.launch {
            var streak = 0
            while (idx < queue.size) {
                val p = queue[idx]
                val r = SmsSender.send(ctx, p.phone, SmsTexts.reminder(p, shop))
                if (r is SmsSender.Result.Failed) {
                    fails = fails + (p to r.reason); streak++
                } else { sent++; streak = 0; ReminderLog.add(p.id, "SMS") }
                idx++
                if (streak >= 3) { stopNote = "Lagatar 3 SMS fail hue — signal / SIM check karein. Baaki rok diye."; break }
                if (idx < queue.size) delay(2_000)
            }
            haptic()
            running = null
        }
    }

    IosSheet(
        onDismissRequest = { running?.cancel(); onDismiss() },
        title = { Text("Sabko reminder", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!started) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(ios.fill).padding(2.dp)) {
                    listOf("SMS" to "SMS (apne aap)", "WA" to "WhatsApp + PDF").forEach { (k, label) ->
                        val on = mode == k
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(7.dp)).background(if (on) ios.sheetCell else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { mode = k; Repo.setSetting("bulk_mode", k); haptic() }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(label, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = ios.label)
                        }
                    }
                }
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = GotGreen, trackColor = ios.separator)
                Text(if (queue.isEmpty()) "Kisi ka mobile number nahi hai." else "${minOf(idx + if (idx < queue.size) 1 else 0, queue.size)} / ${queue.size}  ·  bheje: $sent" + (if (fails.isNotEmpty()) "  ·  fail: ${fails.size}" else ""),
                    fontSize = 13.sp, color = ios.secondaryLabel)
                if (idx < queue.size) {
                    val p = queue[idx]
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ios.sheetCell).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        PartyAvatar(p.id, p.name, 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (running != null) "SMS ja raha hai…" else p.phone, fontSize = 13.sp, color = ios.secondaryLabel)
                        }
                        Text(rupees(p.balance), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = GaveRed)
                    }
                    when {
                        running != null -> BigButton("Rokein", GaveRed, Modifier.fillMaxWidth()) { running?.cancel(); running = null }
                        mode == "SMS" && !manualSms -> BigButton(if (idx == 0) "Sabko SMS bhejein (${queue.size})" else "Aage bhejein (${queue.size - idx})", GotGreen, Modifier.fillMaxWidth()) {
                            sms.withPermission { ok -> if (ok) confirm = true else manualSms = true }
                        }
                        else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BigButton(if (mode == "SMS") "SMS kholein" else "WhatsApp bhejein", GotGreen, Modifier.weight(1.6f)) {
                                haptic()
                                if (mode == "SMS") { smsTo(ctx, p.phone, SmsTexts.reminder(p, shop)); ReminderLog.add(p.id, "SMS") }
                                else whatsappStatement(ctx, p, null)
                                sent++; idx++
                            }
                            Box(Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp)).background(ios.sheetCell)
                                .clickable { idx++ }, contentAlignment = Alignment.Center) { Text("Chhodein", color = ios.tint, fontSize = 16.sp) }
                        }
                    }
                    if (mode == "SMS" && manualSms && running == null) Text("SMS ki permission nahi hai, isliye har SMS Messages app me khulega. Settings > Khata aur Reminder me \"SMS seedha bhejein\" chalu karke permission dein.",
                        fontSize = 12.sp, color = ios.secondaryLabel)
                    if (mode == "WA" && running == null) Text("WhatsApp us party ki chat me statement PDF ke saath khulega — bas Send dabayein aur wapas aayein.",
                        fontSize = 12.sp, color = ios.secondaryLabel)
                } else if (queue.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.CheckCircle, null, Modifier.size(48.dp), tint = GotGreen)
                        Spacer(Modifier.height(6.dp))
                        Text("Ho gaya! $sent logon ko reminder bheja.", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ios.label, textAlign = TextAlign.Center)
                        if (fails.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            fails.take(5).forEach { (p, why) -> Text("✗ ${p.name}: $why", fontSize = 12.sp, color = GaveRed, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            Spacer(Modifier.height(8.dp))
                            BigButton("Fail wale dobara (${fails.size})", IosOrange, Modifier.fillMaxWidth()) {
                                queue = fails.map { it.first }; fails = emptyList(); idx = 0; sent = 0; confirm = true
                            }
                        }
                    }
                }
                stopNote?.let { Text(it, fontSize = 13.sp, color = GaveRed) }
                if (noPhone > 0) Text("$noPhone party ka number nahi hai, unhe chhod diya.", fontSize = 12.sp, color = ios.secondaryLabel)
            }
        },
        confirmButton = { TextButton(onClick = { running?.cancel(); onDismiss() }) { Text(if (idx >= queue.size) "Done" else "Band karein", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) } }
    )
    if (confirm) IosAlert(
        onDismissRequest = { confirm = false },
        title = { Text("${queue.size - idx} logon ko SMS bhejein?") },
        text = { Text("Har ek ko unke baaki amount ka reminder SMS jayega, 2-2 second ke gap par. SMS aapke SIM se jayega (normal SMS charge lag sakta hai). Beech me \"Rokein\" daba sakte hain.") },
        confirmButton = { TextButton(onClick = { confirm = false; runAuto() }) { Text("Haan, bhejo", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel", fontSize = 17.sp) } }
    )
}
