package com.bahikhata.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.bahikhata.app.data.Repo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App settings (v1.0.39). Stored in the app's settings table, so they are part of the
 * normal backup/cloud sync and come back after a reinstall. Each one is read where it is used.
 */
object S {
    private fun g(k: String, d: String) = try { Repo.setting(k, d) } catch (e: Throwable) { d }
    private fun b(k: String, d: Boolean) = g(k, if (d) "1" else "0") == "1"
    fun set(k: String, v: String) { Repo.setSetting(k, v); refreshGlobals() }
    fun setB(k: String, v: Boolean) = set(k, if (v) "1" else "0")

    // ---- Khata
    val sortParties get() = g("sort_parties", "balance")          // balance | name | new | old | recent
    val khataFilter get() = g("khata_filter", "Customers")        // Customers | Suppliers | Sab
    val listPhone get() = b("list_phone", true)
    val listPhotos get() = b("list_photos", true)
    val listCompact get() = b("list_compact", false)
    val runningBal get() = b("running_bal", true)
    val confirmDelete get() = b("confirm_del", true)
    val defMode get() = g("def_mode", "CASH")
    val homeToday get() = b("home_today", true)        // Khata home: aaj ka hisaab card
    val homeOverdue get() = b("home_overdue", true)    // Khata home: due nikal gayi strip
    val defDueDays get() = g("def_due_days", "0").toIntOrNull() ?: 0
    val reminderTpl get() = g("reminder_tpl", "")
    val upiId get() = g("upi_id", "").trim()
    val reminderUpi get() = b("reminder_upi", true)
    val dueBadge get() = b("due_badge", true)

    // ---- Bill & PDF
    val invoicePrefix get() = g("invoice_prefix", "INV-")
    val defGst get() = g("def_gst", "0")
    val billPaid get() = b("bill_paid", true)
    val billReduceStock get() = b("bill_reduce_stock", true)
    val billToBooks get() = b("bill_to_books", true)
    val pdfPhoto get() = b("pdf_photo", true)
    val pdfSignatory get() = g("pdf_signatory", "").trim()
    val pdfFooter get() = g("pdf_footer", "").trim()
    val pdfUpi get() = b("pdf_upi", true)
    val pdfTheme get() = g("pdf_theme", "gold")                    // gold | blue | green | maroon | black

    // ---- Stock & Cashbook
    val defUnit get() = g("def_unit", "pcs")
    val defLow get() = g("def_low", "5")
    val lowFirst get() = b("stock_low_first", false)
    val stockSort get() = g("stock_sort", "name")                  // name | qty | value
    val cashMode get() = g("cash_mode", "CASH")

    // ---- Display & tab bar
    val tabStyle get() = g("tab_style", "floating")               // floating | classic
    val tabLabels get() = b("tab_labels", true)
    val defaultTab get() = g("default_tab", "KHATA")
    fun tabShown(name: String) = name == "KHATA" || b("tab_show_$name", true)
    val dateFmt get() = g("date_fmt", "dd MMM yyyy")
    val time24 get() = b("time_24", false)
    val showPaise get() = b("show_paise", true)

    // ---- Security
    val lockAfterSec get() = g("lock_after", "60").toLongOrNull() ?: 60L   // 0 | 60 | 300 | 900 | -1 (sirf app band hone par)
    val biometric get() = b("biometric", false)
    val secureScreen get() = b("secure_screen", false)
    val keepScreenOn get() = b("keep_on", false)

    // ---- Backup & sync
    val autoSync get() = b("auto_sync", true)
    val wifiOnly get() = b("wifi_only", false)
    val autoBackup get() = g("auto_backup", "off")                // off | daily | weekly
    val backupNudge get() = b("backup_nudge", true)
    val lastBackup get() = g("last_backup", "0").toLongOrNull() ?: 0L

    /** Push app-wide formats (date, time, paise) into the formatter objects. */
    fun refreshGlobals() {
        val df = dateFmt
        NumFmt.datePattern = if (df in DATE_FORMATS) df else "dd MMM yyyy"
        NumFmt.time24 = time24
        NumFmt.paise = showPaise
    }

    val DATE_FORMATS = listOf("dd MMM yyyy", "dd/MM/yyyy", "dd-MM-yyyy", "MM/dd/yyyy", "yyyy-MM-dd")

    /** Reminder text: user's template with {naam} {rakam} {dukan} {upi}, else the default line. */
    fun reminderText(name: String, amount: String, shop: String, due: Boolean): String {
        val tpl = reminderTpl.trim()
        var s = if (tpl.isNotEmpty()) tpl
            .replace("{naam}", name).replace("{name}", name)
            .replace("{rakam}", amount).replace("{amount}", amount)
            .replace("{dukan}", shop).replace("{shop}", shop)
            .replace("{upi}", upiId)
        else "Namaste $name ji, $shop me aapka $amount baaki hai. Kripya jald bhugtan karein. Dhanyavaad 🙏"
        if (due && reminderUpi && upiId.isNotEmpty() && !tpl.contains("{upi}")) s += "\nUPI: $upiId"
        return s
    }

    /** True when the phone is on Wi-Fi / an unmetered network. */
    fun onWifi(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) || !cm.isActiveNetworkMetered)
    } catch (e: Throwable) { true }

    fun markBackedUp() { try { Repo.setSetting("last_backup", System.currentTimeMillis().toString()) } catch (_: Throwable) {} }

    // ---------------------------------------------------------- auto backup (phone me)

    fun backupDir(ctx: Context): File =
        (ctx.getExternalFilesDir("Backups") ?: File(ctx.filesDir, "Backups")).apply { mkdirs() }

    fun autoBackups(ctx: Context): List<File> =
        backupDir(ctx).listFiles()?.filter { it.name.startsWith("bahikhata-auto-") && it.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** Runs off the main thread. Writes a dated backup when one is due; keeps the newest 7. */
    fun autoBackupIfDue(ctx: Context) {
        try {
            val mode = autoBackup
            if (mode == "off") return
            val every = if (mode == "weekly") 7L * 86_400_000L else 86_400_000L
            val last = g("last_auto_backup", "0").toLongOrNull() ?: 0L
            val now = System.currentTimeMillis()
            if (now - last < every - 3_600_000L) return
            val root = Repo.exportJson()
            if ((root.optJSONArray("parties")?.length() ?: 0) + (root.optJSONArray("cash")?.length() ?: 0) +
                (root.optJSONArray("items")?.length() ?: 0) + (root.optJSONArray("invoices")?.length() ?: 0) == 0) return
            PartyPhotos.exportWithPhotos(ctx, root)
            val name = "bahikhata-auto-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.ENGLISH).format(Date(now)) + ".json"
            val tmp = File(backupDir(ctx), "$name.tmp")
            tmp.writeText(root.toString())
            tmp.renameTo(File(backupDir(ctx), name))
            autoBackups(ctx).drop(7).forEach { it.delete() }
            Repo.setSetting("last_auto_backup", now.toString())
            Repo.setSetting("last_backup", now.toString())
        } catch (_: Throwable) {}
    }
}
