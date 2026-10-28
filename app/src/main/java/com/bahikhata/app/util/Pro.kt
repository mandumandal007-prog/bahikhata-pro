package com.bahikhata.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bahikhata.app.data.Repo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// =============================================================== v1.0.45 helpers

/** JSON lists/maps kept in the settings table (so they ride backup + cloud sync, no schema change). */
object ProStore {
    fun arr(k: String): JSONArray = try { JSONArray(Repo.setting(k, "[]").ifBlank { "[]" }) } catch (_: Throwable) { JSONArray() }
    fun obj(k: String): JSONObject = try { JSONObject(Repo.setting(k, "{}").ifBlank { "{}" }) } catch (_: Throwable) { JSONObject() }
    fun put(k: String, v: Any) = Repo.setSetting(k, v.toString())
    fun list(a: JSONArray): List<JSONObject> = (0 until a.length()).mapNotNull { a.optJSONObject(it) }
}

/** Recycle bin: deleted rows are kept on THIS phone (a file) for 30 days, last 80 items. */
object Trash {
    private var file: File? = null
    fun init(ctx: Context) { file = File(ctx.applicationContext.filesDir, "recycle_bin.json") }

    @Synchronized fun all(): List<JSONObject> = try {
        val f = file
        if (f == null || !f.exists()) emptyList() else ProStore.list(JSONArray(f.readText()))
    } catch (_: Throwable) { emptyList() }

    @Synchronized private fun save(items: List<JSONObject>) {
        try { file?.writeText(JSONArray(items).toString()) } catch (_: Throwable) {}
    }

    @Synchronized fun add(kind: String, label: String, rows: List<JSONObject>) {
        if (rows.isEmpty() || file == null) return
        val cutoff = System.currentTimeMillis() - 30L * 86_400_000L
        val items = all().filter { it.optLong("ts") > cutoff }.toMutableList()
        items.add(0, JSONObject().apply {
            put("ts", System.currentTimeMillis()); put("kind", kind); put("label", label); put("rows", JSONArray(rows))
        })
        save(items.take(80))
    }

    @Synchronized fun remove(ts: Long) = save(all().filter { it.optLong("ts") != ts })
    @Synchronized fun clear() = save(emptyList())

    fun restore(item: JSONObject): Int {
        val a = item.optJSONArray("rows") ?: return 0
        val n = Repo.restoreRows(ProStore.list(a))
        remove(item.optLong("ts"))
        return n
    }
}

/** Calculator inside amount boxes: evaluates 250*4+30-10%, × ÷ − too. Returns null if bad. */
object Calc {
    fun eval(expr: String): Double? = try {
        val s = expr.replace("×", "*").replace("÷", "/").replace("−", "-").replace(",", "").replace(" ", "")
        if (s.isBlank()) null else {
            val nums = ArrayList<Double>(); val ops = ArrayList<Char>()
            var i = 0
            var cur = StringBuilder()
            fun pushNum() {
                if (cur.isEmpty()) throw IllegalArgumentException()
                var t = cur.toString(); var pct = false
                if (t.endsWith("%")) { pct = true; t = t.dropLast(1) }
                var v = t.toDouble()
                if (pct) v = if (nums.isNotEmpty() && ops.isNotEmpty() && (ops.last() == '+' || ops.last() == '-')) nums.last() * v / 100.0 else v / 100.0
                nums.add(v); cur = StringBuilder()
            }
            while (i < s.length) {
                val ch = s[i]
                if (ch.isDigit() || ch == '.' || ch == '%') cur.append(ch)
                else if (ch in "+-*/") {
                    if (cur.isEmpty() && ch == '-' && (nums.size == ops.size)) cur.append('-') else { pushNum(); ops.add(ch) }
                } else throw IllegalArgumentException()
                i++
            }
            pushNum()
            // * and / first
            val n2 = ArrayList<Double>(); val o2 = ArrayList<Char>()
            n2.add(nums[0])
            for (j in ops.indices) {
                val o = ops[j]; val b = nums[j + 1]
                when (o) {
                    '*' -> n2[n2.lastIndex] = n2.last() * b
                    '/' -> { if (b == 0.0) throw ArithmeticException(); n2[n2.lastIndex] = n2.last() / b }
                    else -> { o2.add(o); n2.add(b) }
                }
            }
            var r = n2[0]
            for (j in o2.indices) r = if (o2[j] == '+') r + n2[j + 1] else r - n2[j + 1]
            if (r.isFinite()) r else null
        }
    } catch (_: Throwable) { null }
}

fun upiUri(amount: Double, note: String = ""): String? {
    val upi = S.upiId
    if (upi.isBlank()) return null
    val sb = StringBuilder("upi://pay?pa=" + android.net.Uri.encode(upi) + "&pn=" + android.net.Uri.encode(Repo.businessName().take(40)) + "&cu=INR")
    if (amount > 0.0) sb.append("&am=" + String.format(Locale.ENGLISH, "%.2f", amount))
    if (note.isNotBlank()) sb.append("&tn=" + android.net.Uri.encode(note.take(50)))
    return sb.toString()
}

/** Shares several files at once (CSV export). */
fun shareFiles(ctx: Context, files: List<File>, mime: String, title: String) {
    try {
        val uris = ArrayList(files.map { FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", it) })
        val i = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mime
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Throwable) {
        android.widget.Toast.makeText(ctx, "Share nahi ho paya", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** Excel-friendly CSV files of all data. */
object CsvExport {
    private fun q(v: Any?): String {
        val s = (v ?: "").toString().replace("\r", " ").replace("\n", " ")
        return if (s.contains(',') || s.contains('"')) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
    private fun d(ms: Long) = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.ENGLISH).format(Date(ms))
    private fun n(v: Double) = String.format(Locale.ENGLISH, "%.2f", v)
    private fun write(dir: File, name: String, header: List<String>, rows: List<List<Any?>>): File {
        val f = File(dir, name)
        val sb = StringBuilder("\uFEFF")
        sb.append(header.joinToString(",") { q(it) }).append("\r\n")
        rows.forEach { r -> sb.append(r.joinToString(",") { q(it) }).append("\r\n") }
        f.writeText(sb.toString(), Charsets.UTF_8)
        return f
    }

    fun build(ctx: Context, which: Set<String>): List<File> {
        val dir = File(ctx.cacheDir, "csv").apply { deleteRecursively(); mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).format(Date())
        val out = ArrayList<File>()
        val parties = Repo.parties()
        val names = parties.associate { it.id to it.name }
        if ("parties" in which) out += write(dir, "parties-$stamp.csv", listOf("Naam", "Phone", "Type", "Opening", "Baaki (+ lena / - dena)", "Note"),
            parties.map { listOf(it.name, it.phone, if (it.type == "SUPPLIER") "Supplier" else "Customer", n(it.opening), n(it.balance), it.note) })
        if ("entries" in which) out += write(dir, "khata-entries-$stamp.csv", listOf("Date", "Party", "Diye/Mile", "Amount", "Mode", "Note", "Due date"),
            Repo.entriesFull(0L, Long.MAX_VALUE).map { listOf(d(it.date), names[it.partyId] ?: "", if (it.gave) "Diye" else "Mile", n(it.amount), it.mode, it.note, it.dueDate?.let { x -> d(x) } ?: "") })
        if ("cash" in which) out += write(dir, "cashbook-$stamp.csv", listOf("Date", "Aaya/Gaya", "Amount", "Category", "Mode", "Note"),
            Repo.cashList().map { listOf(d(it.date), if (it.cashIn) "Aaya" else "Gaya", n(it.amount), it.category, it.mode, it.note) })
        if ("stock" in which) out += write(dir, "stock-$stamp.csv", listOf("Item", "Unit", "Qty", "Kharid rate", "Bechne ka rate", "Kam stock seema"),
            Repo.items().map { listOf(it.name, it.unit, n(it.qty), n(it.purchasePrice), n(it.sellPrice), n(it.lowStock)) })
        if ("bills" in which) out += write(dir, "bills-$stamp.csv", listOf("Number", "Type", "Date", "Party", "Phone", "Subtotal", "Discount", "GST %", "Total", "Mila", "Baaki", "Paid"),
            Repo.invoices().map { listOf(it.number, if (it.isQuote) "Quotation" else "Bill", d(it.date), it.partyName, it.phone, n(it.subtotal), n(it.discount), n(it.gstPercent), n(it.total),
                n(if (it.paid) it.total else it.received), n(if (it.isQuote) 0.0 else (it.total - (if (it.paid) it.total else it.received)).coerceAtLeast(0.0)), if (it.paid) "Haan" else "Nahi") })
        return out
    }
}

/** Daily "Aaj ka hisaab" notification at the chosen hour. OFF by default. */
object DailySummary {
    private const val WORK = "daily_summary"
    private const val CH = "daily_summary"
    val on get() = Repo.setting("daily_sum", "0") == "1"
    val hour get() = Repo.setting("daily_sum_hour", "21").toIntOrNull() ?: 21

    fun sync(ctx: Context) {
        try {
            val wm = WorkManager.getInstance(ctx.applicationContext)
            if (!on) { wm.cancelUniqueWork(WORK); return }
            val now = Calendar.getInstance()
            val t = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23)); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
            if (!t.after(now)) t.add(Calendar.DAY_OF_YEAR, 1)
            val req = PeriodicWorkRequestBuilder<DailySummaryWorker>(1, TimeUnit.DAYS)
                .setInitialDelay((t.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L), TimeUnit.MILLISECONDS).build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
        } catch (_: Throwable) {}
    }

    fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun text(): Pair<String, String> {
        val start = startOfDay(System.currentTimeMillis())
        val es = Repo.entriesFull(start, start + 86_400_000L)
        val gave = es.filter { it.gave }.sumOf { it.amount }
        val got = es.filter { !it.gave }.sumOf { it.amount }
        val cash = Repo.cashList().filter { it.date >= start }
        val cin = cash.filter { it.cashIn }.sumOf { it.amount }
        val cout = cash.filter { !it.cashIn }.sumOf { it.amount }
        val overdue = Repo.overdueSince().size
        val lena = Repo.parties().filter { it.balance > 0 }.sumOf { it.balance }
        val title = "Aaj ka hisaab · ${es.size} entry"
        val body = "Mile ${rupees(got)} · Diye ${rupees(gave)}\nCash aaya ${rupees(cin)} · gaya ${rupees(cout)}\nKul lena baaki ${rupees(lena)}" +
            (if (overdue > 0) " · $overdue logon ki due nikli" else "")
        return title to body
    }

    fun show(ctx: Context) {
        if (!canNotify(ctx)) return
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) nm?.createNotificationChannel(NotificationChannel(CH, "Roz ka hisaab", NotificationManager.IMPORTANCE_DEFAULT))
            val (t, b) = text()
            val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            val pi = launch?.let { PendingIntent.getActivity(ctx, 45, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
            val n = NotificationCompat.Builder(ctx, CH)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle(t).setContentText(b.lineSequence().first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(b))
                .setAutoCancel(true)
                .apply { if (pi != null) setContentIntent(pi) }
                .build()
            NotificationManagerCompat.from(ctx).notify(4501, n)
        } catch (_: SecurityException) {} catch (_: Throwable) {}
    }
}

class DailySummaryWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try { Repo.init(applicationContext); if (DailySummary.on) DailySummary.show(applicationContext) } catch (_: Throwable) {}
        return Result.success()
    }
}

/** Voice entry: "Ramesh ko 500 diye" -> (name, amount, gave). */
object VoiceParse {
    private val GAVE = listOf("diye", "diya", "dia", "udhar", "udhaar", "de", "gave", "दिए", "दिया", "दिये", "उधार", "दे")
    private val GOT = listOf("mile", "mila", "liye", "liya", "jama", "wapas", "received", "मिले", "मिला", "लिए", "लिया", "जमा", "वापस")
    private val FILL = setOf("ko", "ne", "se", "ka", "ki", "ke", "rupaye", "rupees", "rupee", "rs", "rupay", "₹", "को", "ने", "से", "रुपये", "रुपए", "का", "की", "के")
    private val HI_NUM = mapOf("एक" to 1, "दो" to 2, "तीन" to 3, "चार" to 4, "पांच" to 5, "पाँच" to 5, "छह" to 6, "सात" to 7, "आठ" to 8, "नौ" to 9, "दस" to 10,
        "सौ" to 100, "हज़ार" to 1000, "हजार" to 1000, "sau" to 100, "hazar" to 1000, "hazaar" to 1000)

    data class Result(val name: String, val amount: Double, val gave: Boolean?)

    fun parse(text: String): Result {
        val t = text.lowercase(Locale.ROOT).replace(",", "")
        val words = t.split(Regex("\\s+")).filter { it.isNotBlank() }
        var amount = 0.0
        val numIdx = words.indexOfFirst { w -> w.replace("₹", "").toDoubleOrNull() != null }
        if (numIdx >= 0) {
            amount = words[numIdx].replace("₹", "").toDouble()
            val next = words.getOrNull(numIdx + 1)
            if (next != null && HI_NUM[next] != null && HI_NUM[next]!! >= 100) amount *= HI_NUM[next]!!
        } else {
            var acc = 0.0; var cur = 0.0
            words.forEach { w -> HI_NUM[w]?.let { v -> if (v >= 100) { cur = (if (cur == 0.0) 1.0 else cur) * v; if (v >= 1000) { acc += cur; cur = 0.0 } } else cur += v } }
            amount = acc + cur
        }
        val gave = when {
            words.any { it in GOT } -> false
            words.any { it in GAVE } -> true
            else -> null
        }
        val stop = numIdx.takeIf { it >= 0 } ?: words.indexOfFirst { it in GAVE || it in GOT || HI_NUM.containsKey(it) }.takeIf { it >= 0 } ?: words.size
        val nameWords = words.take(stop).filter { it !in FILL && it !in GAVE && it !in GOT }
        val orig = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val name = nameWords.joinToString(" ") { w -> orig.firstOrNull { it.lowercase(Locale.ROOT) == w } ?: w.replaceFirstChar { c -> c.uppercase() } }
        return Result(name.trim(), amount, gave)
    }
}

/** Digital visiting card image (offline). */
object VisitingCard {
    fun make(style: Int): Bitmap {
        val w = 1080; val h = 640
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val (c1, c2, acc) = when (style) {
            1 -> Triple(0xFF0B6E4F.toInt(), 0xFF08A045.toInt(), 0xFFFFFFFF.toInt())
            2 -> Triple(0xFF6A040F.toInt(), 0xFF9D0208.toInt(), 0xFFFFD166.toInt())
            else -> Triple(0xFF14213D.toInt(), 0xFF274472.toInt(), 0xFFFCA311.toInt())
        }
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), c1, c2, Shader.TileMode.CLAMP) }
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 36f, 36f, bg)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        val name = Repo.businessName()
        p.textSize = if (name.length > 18) 62f else 78f; p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        c.drawText(name.take(28), 60f, 140f, p)
        val bar = Paint().apply { color = acc }
        c.drawRect(60f, 172f, 260f, 180f, bar)
        p.typeface = Typeface.DEFAULT; p.textSize = 40f
        var y = 260f
        val phone = Repo.setting("phone", "")
        val address = Repo.setting("address", "")
        val gstin = Repo.setting("gstin", "")
        if (phone.isNotBlank()) { c.drawText("📞  $phone", 60f, y, p); y += 66f }
        if (address.isNotBlank()) {
            address.chunked(30).take(2).forEachIndexed { i, line -> c.drawText(if (i == 0) "📍  $line" else "      $line", 60f, y, p); y += 56f }
            y += 10f
        }
        if (gstin.isNotBlank()) { c.drawText("GSTIN: $gstin", 60f, y, p); y += 66f }
        val upi = S.upiId
        if (upi.isNotBlank()) { c.drawText("UPI: $upi", 60f, y, p) }
        val uri = upiUri(0.0)
        if (uri != null) {
            Qr.bitmap(uri, 300)?.let { q ->
                val r = RectF(w - 360f, h - 400f, w - 40f, h - 80f)
                c.drawRoundRect(RectF(r.left - 10, r.top - 10, r.right + 10, r.bottom + 10), 20f, 20f, Paint().apply { color = 0xFFFFFFFF.toInt() })
                c.drawBitmap(q, null, r, null)
                p.textSize = 30f; p.color = acc
                c.drawText("Scan karke pay karein", w - 370f, h - 40f, p)
            }
        }
        p.textSize = 26f; p.color = 0xAAFFFFFF.toInt()
        c.drawText("BahiKhata Pro", 60f, h - 40f, p)
        return bmp
    }

    fun saveTemp(ctx: Context, bmp: Bitmap, name: String): File {
        val f = File(ctx.cacheDir, name)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }
}

/** Big UPI QR image with party name and amount, for sharing. */
fun paymentQrImage(title: String, amount: Double, uri: String): Bitmap {
    val w = 900; val h = 1200
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(0xFFFFFFFF.toInt())
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF111111.toInt(); textAlign = Paint.Align.CENTER }
    p.textSize = 54f; p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    c.drawText(Repo.businessName().take(26), w / 2f, 110f, p)
    p.textSize = 36f; p.typeface = Typeface.DEFAULT; p.color = 0xFF555555.toInt()
    c.drawText(title.take(34), w / 2f, 170f, p)
    Qr.bitmap(uri, 640)?.let { c.drawBitmap(it, null, RectF(130f, 220f, 770f, 860f), null) }
    p.color = 0xFF111111.toInt(); p.textSize = 72f; p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    if (amount > 0) c.drawText(rupees(amount), w / 2f, 970f, p)
    p.textSize = 34f; p.typeface = Typeface.DEFAULT; p.color = 0xFF555555.toInt()
    c.drawText("UPI: " + S.upiId, w / 2f, 1040f, p)
    c.drawText("Kisi bhi UPI app se scan karke pay karein", w / 2f, 1100f, p)
    return bmp
}
