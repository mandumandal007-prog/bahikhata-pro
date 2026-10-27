@file:OptIn(ExperimentalMaterial3Api::class)

package com.bahikhata.app.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bahikhata.app.data.Repo
import com.bahikhata.app.util.S
import com.bahikhata.app.util.fmtDate
import com.bahikhata.app.util.fmtDateTime
import com.bahikhata.app.util.rupees
import com.bahikhata.app.util.shareFile

/** Settings sub-pages (v1.0.39). Every row here changes real behaviour somewhere in the app. */
data class PrefGroup(val key: String, val title: String, val icon: ImageVector, val color: Color, val sub: String)

val PREF_GROUPS = listOf(
    PrefGroup("khata", "Khata aur Reminder", Icons.Filled.People, Color(0xFF007AFF), "List, sorting, entry, reminder message, UPI"),
    PrefGroup("bill", "Bill aur PDF", Icons.AutoMirrored.Filled.ReceiptLong, Color(0xFFFF9500), "Bill number, GST, PDF rang, sign, footer"),
    PrefGroup("stock", "Stock aur Cashbook", Icons.Filled.Inventory2, Color(0xFF34C759), "Unit, kam-stock seema, sorting, cash mode"),
    PrefGroup("display", "Tab bar aur Dikhawat", Icons.Filled.Dashboard, Color(0xFF5856D6), "Floating bar, tabs, date format, paise"),
    PrefGroup("security", "Suraksha", Icons.Filled.Shield, Color(0xFFFF3B30), "Auto-lock, fingerprint, screenshot block"),
    PrefGroup("backup", "Backup aur Sync", Icons.Filled.CloudSync, Color(0xFF32ADE6), "Auto sync, Wi-Fi only, auto backup")
)

@Composable
fun PrefsScreen(group: String, nav: Nav) {
    val g = PREF_GROUPS.firstOrNull { it.key == group } ?: PREF_GROUPS.first()
    @Suppress("UNUSED_VARIABLE") val tick = Repo.tick
    val backLabel = remember { nav.backLabel() }
    val behavior = rememberLargeTitle()
    Scaffold(
        modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { IosNavBar(g.title, behavior, backLabel = "Settings", onBack = { nav.back() }) }
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            when (g.key) {
                "khata" -> KhataPrefs()
                "bill" -> BillPrefs()
                "stock" -> StockPrefs()
                "display" -> DisplayPrefs()
                "security" -> SecurityPrefs()
                "backup" -> BackupPrefs()
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ------------------------------------------------------------------ row helpers

@Composable
private fun SwitchRow(i: Int, n: Int, icon: ImageVector, color: Color, title: String, sub: String?, key: String, def: Boolean, onChange: ((Boolean) -> Unit)? = null) {
    val on = Repo.setting(key, if (def) "1" else "0") == "1"
    GroupCell(i, n, separatorInset = 57.dp) {
        IconTile(icon, color); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, color = ios.label)
            if (sub != null) Text(sub, fontSize = 12.sp, color = ios.secondaryLabel)
        }
        IosSwitch(on, { v -> S.setB(key, v); onChange?.invoke(v) })
    }
}

@Composable
private fun ChoiceRow(i: Int, n: Int, icon: ImageVector, color: Color, title: String, key: String, def: String, options: List<Pair<String, String>>) {
    val cur = Repo.setting(key, def)
    var open by remember { mutableStateOf(false) }
    GroupCell(i, n, onClick = { open = true }, separatorInset = 57.dp) {
        IconTile(icon, color); Spacer(Modifier.width(12.dp))
        Text(title, Modifier.weight(1f), fontSize = 17.sp, color = ios.label)
        Text(options.firstOrNull { it.first == cur }?.second ?: cur, fontSize = 16.sp, color = ios.secondaryLabel, maxLines = 1)
        Chevron()
    }
    if (open) IosActionSheet(title, options.map { (v, label) ->
        SheetAction((if (v == cur) "✓ " else "") + label) { S.set(key, v) }
    }) { open = false }
}

@Composable
private fun TextRow(
    i: Int, n: Int, icon: ImageVector, color: Color, title: String, key: String, def: String,
    hint: String, help: String? = null, multiline: Boolean = false, keyboard: KeyboardType = KeyboardType.Text,
    valid: (String) -> Boolean = { true }
) {
    val cur = Repo.setting(key, def)
    var open by remember { mutableStateOf(false) }
    GroupCell(i, n, onClick = { open = true }, separatorInset = 57.dp) {
        IconTile(icon, color); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, color = ios.label)
            Text(cur.ifBlank { hint }, fontSize = 12.sp, color = ios.secondaryLabel, maxLines = 2)
        }
        Chevron()
    }
    if (open) {
        var v by remember { mutableStateOf(cur) }
        var bad by remember { mutableStateOf(false) }
        IosAlert(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    if (help != null) { Text(help, fontSize = 13.sp); Spacer(Modifier.height(8.dp)) }
                    IosField(v, { v = it; bad = false }, "", placeholder = hint, singleLine = !multiline, keyboard = keyboard)
                    if (bad) Text("Sahi value daalein", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = v.trim()
                    if (t.isNotEmpty() && !valid(t)) { bad = true; return@TextButton }
                    S.set(key, t); open = false
                }) { Text("Save", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel", fontSize = 17.sp) } }
        )
    }
}

// ------------------------------------------------------------------ pages

@Composable
private fun KhataPrefs() {
    SectionHeader("Home screen")
    SwitchRow(0, 2, Icons.Filled.Today, Color(0xFF007AFF), "Aaj ka hisaab card", "Khata ke upar aaj ke Diye / Mile / Cash / Bikri", "home_today", true)
    SwitchRow(1, 2, Icons.Filled.NotificationImportant, Color(0xFFFF9500), "Due nikal gayi wale log", "Home par late payment wale customers ki patti", "home_overdue", true)

    SectionHeader("Party list")
    ChoiceRow(0, 6, Icons.AutoMirrored.Filled.Sort, Color(0xFF007AFF), "Sort kaise karein", "sort_parties", "balance", listOf(
        "balance" to "Sabse zyada baaki", "name" to "Naam (A–Z)", "recent" to "Haal ki entry", "new" to "Naye pehle", "old" to "Purane pehle"))
    ChoiceRow(1, 6, Icons.Filled.FilterList, Color(0xFF5856D6), "Khulne par dikhayein", "khata_filter", "Customers", listOf(
        "Customers" to "Customers", "Suppliers" to "Suppliers", "Sab" to "Sab"))
    SwitchRow(2, 6, Icons.Filled.Phone, Color(0xFF34C759), "List me mobile number", null, "list_phone", true)
    SwitchRow(3, 6, Icons.Filled.AccountCircle, Color(0xFFFF9500), "List me party photo", "Off = sirf naam ke akshar", "list_photos", true)
    SwitchRow(4, 6, Icons.Filled.DensitySmall, Color(0xFF8E8E93), "Compact list", "Chhoti rows, ek screen me zyada party", "list_compact", false)
    SwitchRow(5, 6, Icons.Filled.EventBusy, Color(0xFFFF3B30), "Due nikal gayi ka nishaan", "Due date beet gayi to list me dikhe", "due_badge", true)

    SectionHeader("Entry")
    ChoiceRow(0, 4, Icons.Filled.Payments, Color(0xFF34C759), "Default payment mode", "def_mode", "CASH",
        PAY_MODES.map { it to it })
    ChoiceRow(1, 4, Icons.Filled.Event, Color(0xFFFF9500), "Diye par default due date", "def_due_days", "0", listOf(
        "0" to "Koi nahi", "7" to "7 din baad", "15" to "15 din baad", "30" to "30 din baad", "45" to "45 din baad", "60" to "60 din baad"))
    SwitchRow(2, 4, Icons.Filled.Timeline, Color(0xFF007AFF), "Running balance dikhayein", "Har entry ke neeche us waqt ka baaki", "running_bal", true)
    SwitchRow(3, 4, Icons.Filled.DeleteForever, Color(0xFFFF3B30), "Delete se pehle poochein", "Entry hatane se pehle confirm", "confirm_del", true)

    SectionHeader("Reminder message")
    TextRow(0, 3, Icons.Filled.Sms, Color(0xFF34C759), "Reminder ka message", "reminder_tpl", "",
        "Default: Namaste {naam} ji, {dukan} me aapka {rakam} baaki hai…",
        help = "Apna message likhein. {naam} {rakam} {dukan} {upi} apne aap bhar jayenge.", multiline = true)
    TextRow(1, 3, Icons.Filled.QrCode2, Color(0xFF5856D6), "Aapka UPI ID", "upi_id", "", "jaise mandu@okaxis",
        help = "Reminder aur PDF par dikhega taaki customer seedha UPI se bhej sake.",
        valid = { it.contains("@") && !it.contains(" ") })
    SwitchRow(2, 3, Icons.Filled.AlternateEmail, Color(0xFF007AFF), "Reminder me UPI ID jodein", null, "reminder_upi", true)
    val ex = S.reminderText("Ramesh", rupees(1500.0), Repo.businessName(), true)
    SectionFooter("Preview: $ex")
}

@Composable
private fun BillPrefs() {
    SectionHeader("Naya bill")
    TextRow(0, 5, Icons.Filled.Tag, Color(0xFFFF9500), "Bill number prefix", "invoice_prefix", "INV-", "INV-",
        help = "Agle bill ka number: prefix + 0001. Jaise MS- → MS-0012", valid = { it.length <= 12 })
    ChoiceRow(1, 5, Icons.Filled.Percent, Color(0xFF5856D6), "Default GST %", "def_gst", "0", listOf(
        "0" to "0%", "5" to "5%", "12" to "12%", "18" to "18%", "28" to "28%"))
    SwitchRow(2, 5, Icons.Filled.CheckCircle, Color(0xFF34C759), "Bill default 'Paid'", "Off = naya bill udhaar (unpaid) se shuru", "bill_paid", true)
    SwitchRow(3, 5, Icons.Filled.Inventory, Color(0xFF8E8E93), "Bill par stock ghatayein", "Default on/off", "bill_reduce_stock", true)
    SwitchRow(4, 5, Icons.Filled.MenuBook, Color(0xFF007AFF), "Bill ko khata/cashbook me jodein", "Default on/off", "bill_to_books", true)
    SectionFooter("Agla bill number: ${Repo.nextInvoiceNumber()}")

    SectionHeader("PDF (statement aur bill)")
    ChoiceRow(0, 5, Icons.Filled.Palette, Color(0xFFFFCC00), "PDF ka rang", "pdf_theme", "gold", listOf(
        "gold" to "Navy + Gold", "blue" to "Royal Blue", "green" to "Emerald Green", "maroon" to "Maroon + Gold", "black" to "Classic Black"))
    SwitchRow(1, 5, Icons.Filled.AccountCircle, Color(0xFFFF9500), "Statement me party photo", null, "pdf_photo", true)
    SwitchRow(2, 5, Icons.Filled.QrCode2, Color(0xFF5856D6), "PDF par UPI ID", "UPI ID Khata settings me daalein", "pdf_upi", true)
    TextRow(3, 5, Icons.Filled.Draw, Color(0xFF007AFF), "Sign ke neeche naam", "pdf_signatory", "", "Authorised Signatory",
        help = "Jaise: Mandu (Proprietor)")
    TextRow(4, 5, Icons.Filled.ShortText, Color(0xFF34C759), "Bill ki footer line", "pdf_footer", "", "Thank you for your business!",
        help = "Bill PDF ke neeche ek line. Jaise: Dhanyavaad, phir padharein 🙏")
}

@Composable
private fun StockPrefs() {
    SectionHeader("Stock")
    ChoiceRow(0, 4, Icons.Filled.Straighten, Color(0xFF34C759), "Naye item ki unit", "def_unit", "pcs",
        listOf("pcs", "kg", "g", "litre", "ml", "box", "dozen", "packet", "metre", "bag").map { it to it })
    TextRow(1, 4, Icons.Filled.Warning, Color(0xFFFF9500), "Kam stock ki seema", "def_low", "5", "5",
        help = "Naye item ke liye: itna ya kam bacha to 'Stock kam hai' dikhega.", keyboard = KeyboardType.Decimal,
        valid = { it.toDoubleOrNull()?.let { d -> d >= 0 } == true })
    ChoiceRow(2, 4, Icons.AutoMirrored.Filled.Sort, Color(0xFF007AFF), "Items ka order", "stock_sort", "name", listOf(
        "name" to "Naam (A–Z)", "qty" to "Kam quantity pehle", "value" to "Zyada value pehle"))
    SwitchRow(3, 4, Icons.Filled.PriorityHigh, Color(0xFFFF3B30), "Kam stock wale upar", null, "stock_low_first", false)

    SectionHeader("Cashbook")
    ChoiceRow(0, 1, Icons.Filled.Payments, Color(0xFF34C759), "Default payment mode", "cash_mode", "CASH", PAY_MODES.map { it to it })
}

@Composable
private fun DisplayPrefs() {
    SectionHeader("Tab bar")
    ChoiceRow(0, 3, Icons.Filled.CropFree, Color(0xFF5856D6), "Tab bar style", "tab_style", "floating", listOf(
        "floating" to "Floating (gol, iPhone jaisa)", "classic" to "Classic (neeche chipka)"))
    SwitchRow(1, 3, Icons.Filled.TextFields, Color(0xFF007AFF), "Tab ke naam dikhayein", "Off = sirf icon", "tab_labels", true)
    ChoiceRow(2, 3, Icons.Filled.Home, Color(0xFFFF9500), "App khulne par tab", "default_tab", "KHATA",
        Tab.entries.map { it.name to it.label })

    SectionHeader("Kaun se tab dikhein")
    val opt = listOf(Tab.CASH, Tab.STOCK, Tab.BILL, Tab.REPORTS)
    opt.forEachIndexed { i, t ->
        SwitchRow(i, opt.size, t.icon, listOf(Color(0xFF34C759), Color(0xFFFF9500), Color(0xFF5856D6), Color(0xFF007AFF))[i],
            t.label, null, "tab_show_${t.name}", true)
    }
    SectionFooter("Khata tab hamesha rehta hai. Band kiya tab ka data safe rehta hai.")

    SectionHeader("Tareekh aur rakam")
    val now = System.currentTimeMillis()
    ChoiceRow(0, 3, Icons.Filled.CalendarMonth, Color(0xFFFF3B30), "Date format", "date_fmt", "dd MMM yyyy",
        S.DATE_FORMATS.map { it to java.text.SimpleDateFormat(it, java.util.Locale.ENGLISH).format(java.util.Date(now)) })
    SwitchRow(1, 3, Icons.Filled.Schedule, Color(0xFF8E8E93), "24 ghante ka time", null, "time_24", false)
    SwitchRow(2, 3, Icons.Filled.CurrencyRupee, Color(0xFF34C759), "Paise dikhayein (.50)", "Off = poore rupaye me (sirf dikhawat)", "show_paise", true)
    SectionFooter("Example: ${fmtDateTime(now)}  ·  ${rupees(1250.5)}")
}

@Composable
private fun SecurityPrefs() {
    val hasPin = Repo.setting("pin", "").isNotEmpty()
    SectionHeader("App lock")
    ChoiceRow(0, 2, Icons.Filled.LockClock, Color(0xFFFF3B30), "Auto-lock", "lock_after", "60", listOf(
        "0" to "Turant", "60" to "1 minute baad", "300" to "5 minute baad", "900" to "15 minute baad", "-1" to "Sirf app band karne par"))
    if (Build.VERSION.SDK_INT >= 28)
        SwitchRow(1, 2, Icons.Filled.Fingerprint, Color(0xFF34C759), "Fingerprint / face unlock", "PIN screen par seedha finger se kholein", "biometric", false)
    else
        GroupCell(1, 2) { IconTile(Icons.Filled.Fingerprint, ios.gray); Spacer(Modifier.width(12.dp)); Text("Fingerprint: Android 9+ chahiye", fontSize = 15.sp, color = ios.secondaryLabel) }
    SectionFooter(if (hasPin) "PIN lock on hai." else "Ye tabhi kaam karenge jab Settings me PIN lock on ho.")

    SectionHeader("Privacy")
    SwitchRow(0, 2, Icons.Filled.ScreenLockPortrait, Color(0xFF5856D6), "Screenshot block", "Recent apps me bhi hisaab chhupa rahega", "secure_screen", false)
    SwitchRow(1, 2, Icons.Filled.LightMode, Color(0xFFFF9500), "Screen on rakhein", "App khula ho to screen band na ho", "keep_on", false)
}

@Composable
private fun BackupPrefs() {
    val ctx = LocalContext.current
    SectionHeader("Cloud sync")
    SwitchRow(0, 2, Icons.Filled.Sync, Color(0xFF007AFF), "Auto sync", "Har badlav ke baad apne aap cloud me", "auto_sync", true)
    SwitchRow(1, 2, Icons.Filled.Wifi, Color(0xFF34C759), "Sirf Wi-Fi par sync", "Mobile data bachaye (Settings ka 'Sync now' hamesha chalega)", "wifi_only", false)

    SectionHeader("Phone me auto backup")
    ChoiceRow(0, 2, Icons.Filled.Backup, Color(0xFF32ADE6), "Auto backup", "auto_backup", "off", listOf(
        "off" to "Band", "daily" to "Roz", "weekly" to "Har hafte"))
    SwitchRow(1, 2, Icons.Filled.NotificationsActive, Color(0xFFFF9500), "Backup yaad dilayein", "Login nahi hai aur 7 din se backup nahi to Khata me yaad", "backup_nudge", false.not())
    val files = remember(Repo.tick) { S.autoBackups(ctx) }
    val last = S.lastBackup
    if (files.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        GroupCell(0, 1, onClick = { shareFile(ctx, files.first(), "application/json", "Backup bhejein") }, separatorInset = 57.dp) {
            IconTile(Icons.Filled.Share, IosGreen); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Latest auto backup share karein", fontSize = 17.sp, color = ios.label)
                Text(fmtDateTime(files.first().lastModified()) + " · ${files.size} file phone me", fontSize = 12.sp, color = ios.secondaryLabel)
            }
            Chevron()
        }
    }
    if (files.isNotEmpty()) {
        var ask by remember { mutableStateOf(false) }
        Spacer(Modifier.height(8.dp))
        GroupCell(0, 1, onClick = { ask = true }) {
            Text("Latest auto backup se restore", Modifier.weight(1f), fontSize = 17.sp, color = IosRed)
        }
        if (ask) ConfirmDialog("Restore karein?", "Abhi ka saara data hat kar ${fmtDateTime(files.first().lastModified())} wala backup aa jayega.", "Restore",
            onConfirm = {
                val ok = try {
                    val obj = org.json.JSONObject(files.first().readText())
                    Repo.importJson(obj).also { if (it) { com.bahikhata.app.util.PartyPhotos.restoreFromBackup(ctx, obj); com.bahikhata.app.util.PartyPhotos.bumpVersion() } }
                } catch (e: Throwable) { false }
                android.widget.Toast.makeText(ctx, if (ok) "Restore ho gaya ✓" else "Restore fail hua", android.widget.Toast.LENGTH_LONG).show()
            },
            onDismiss = { ask = false })
    }
    SectionFooter(
        (if (last > 0) "Aakhri backup: ${fmtDate(last)}. " else "Abhi tak koi backup nahi. ") +
            "Auto backup app khulne par banta hai, aakhri 7 file rakhta hai (party photos ke saath). Restore: Settings → Backup file se restore."
    )
}
