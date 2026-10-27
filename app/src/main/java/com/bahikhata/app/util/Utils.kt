package com.bahikhata.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Indian style digit grouping: 1234567.5 -> 12,34,567.50 */
private fun groupIndian(value: Long): String {
    var s = value.toString()
    if (s.length <= 3) return s
    val last3 = s.takeLast(3)
    var rest = s.dropLast(3)
    val parts = ArrayList<String>()
    while (rest.length > 2) {
        parts.add(0, rest.takeLast(2))
        rest = rest.dropLast(2)
    }
    if (rest.isNotEmpty()) parts.add(0, rest)
    return parts.joinToString(",") + "," + last3
}

/** Formats an amount with Indian grouping and at most 2 decimals. */
fun money(n: Double): String {
    val neg = n < 0
    val a = abs(n)
    val cents = if (NumFmt.paise) Math.round(a * 100) else Math.round(a) * 100
    val whole = cents / 100
    val paise = (cents % 100).toInt()
    val g = if (NumFmt.indian) groupIndian(whole) else String.format(Locale.ENGLISH, "%,d", whole)
    val body = if (paise == 0) g else g + "." + paise.toString().padStart(2, '0')
    return (if (neg) "-" else "") + body
}

fun rupees(n: Double): String = NumFmt.symbol + money(n)

fun compactRupees(n: Double): String {
    val a = abs(n)
    return when {
        a >= 10000000 -> "₹" + money(a / 10000000.0).trimEnd('0').trimEnd('.') + "Cr"
        a >= 100000 -> "₹" + money(a / 100000.0).trimEnd('0').trimEnd('.') + "L"
        a >= 1000 -> "₹" + money(a / 1000.0).trimEnd('0').trimEnd('.') + "K"
        else -> "₹" + money(a)
    }
}

fun fmtDate(millis: Long): String =
    SimpleDateFormat(NumFmt.datePattern, Locale.ENGLISH).format(Date(millis))

fun fmtDateTime(millis: Long): String =
    SimpleDateFormat(NumFmt.datePattern + (if (NumFmt.time24) ", HH:mm" else ", hh:mm a"), Locale.ENGLISH).format(Date(millis))

fun dayLabel(millis: Long): String =
    SimpleDateFormat("EE", Locale.ENGLISH).format(Date(millis))

fun monthLabel(millis: Long): String =
    SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(Date(millis))

fun startOfDay(millis: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun addDays(millis: Long, days: Int): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    c.add(Calendar.DAY_OF_YEAR, days)
    return c.timeInMillis
}

fun startOfMonth(millis: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    c.set(Calendar.DAY_OF_MONTH, 1)
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun shareFile(ctx: Context, file: File, mime: String, title: String) {
    try {
        val uri: Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, title))
    } catch (e: Exception) {
        // Ignore — no sharing app installed.
    }
}

fun shareText(ctx: Context, text: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(Intent.createChooser(intent, "Share"))
    } catch (e: Exception) {
    }
}

/** Opens WhatsApp chat with the given number (or WhatsApp picker if the number is empty). */
fun whatsapp(ctx: Context, phone: String, message: String) {
    val withCc = normPhone(phone)
    val url = if (withCc.isNotEmpty()) {
        "https://wa.me/$withCc?text=" + Uri.encode(message)
    } else {
        "https://wa.me/?text=" + Uri.encode(message)
    }
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        shareText(ctx, message)
    }
}

/** Digits with country code (default India 91), or "" when the number is too short. */
fun normPhone(phone: String): String {
    var d = phone.filter { it.isDigit() }
    if (d.startsWith("00")) d = d.drop(2)
    if (d.length == 11 && d.startsWith("0")) d = d.drop(1)
    return when {
        d.length == 10 -> "91$d"
        d.length > 10 -> d
        else -> ""
    }
}

/** Sends a file straight to this number's WhatsApp chat; falls back to wa.me text + normal share. */
fun whatsappFile(ctx: Context, file: File, mime: String, phone: String, text: String) {
    val num = normPhone(phone)
    val uri: Uri = try { FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file) } catch (e: Exception) { return }
    for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
        try {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = mime
                setPackage(pkg)
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
                if (num.isNotEmpty()) putExtra("jid", "$num@s.whatsapp.net")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(i)
            return
        } catch (e: Exception) {
        }
    }
    whatsapp(ctx, phone, text)
}

/** Opens the SMS app to this number with the text filled in (SMS cannot carry a PDF). */
fun smsTo(ctx: Context, phone: String, text: String) {
    val num = normPhone(phone).ifEmpty { phone.filter { it.isDigit() } }
    try {
        ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num")).apply { putExtra("sms_body", text) })
    } catch (e: Exception) {
        shareText(ctx, text)
    }
}

fun call(ctx: Context, phone: String) {
    val digits = phone.filter { it.isDigit() }
    if (digits.isEmpty()) return
    try {
        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")))
    } catch (e: Exception) {
    }
}
