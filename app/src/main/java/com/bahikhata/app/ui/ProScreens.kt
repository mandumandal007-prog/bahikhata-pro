@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bahikhata.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bahikhata.app.data.Party
import com.bahikhata.app.data.Repo
import com.bahikhata.app.util.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// =================================================================== v1.0.45 Business tools

private data class ProTool(val key: String, val title: String, val sub: String, val icon: ImageVector, val color: Color)

private val PRO_TOOLS = listOf(
    ProTool("voice", "Bol kar entry 🎤", "\"Ramesh ko 500 diye\" bolo, entry ban jayegi", Icons.Filled.Mic, Color(0xFFFF2D55)),
    ProTool("kharcha", "Kharcha tracker", "Category wise kharcha + mahine ka budget", Icons.Filled.Payments, Color(0xFFFF9500)),
    ProTool("calendar", "Vasooli calendar", "Kis din kiska paisa aana hai", Icons.Filled.CalendarMonth, Color(0xFF007AFF)),
    ProTool("byaj", "Byaj (late payment)", "Due nikalne par byaj jodein", Icons.Filled.Percent, Color(0xFFAF52DE)),
    ProTool("groups", "Party groups", "Gaon / line / type ke hisaab se group", Icons.Filled.Groups, Color(0xFF34C759)),
    ProTool("staff", "Staff hazri + salary", "Roz ki hazri, advance, mahine ki salary", Icons.Filled.Badge, Color(0xFF5856D6)),
    ProTool("galla", "Galla milan", "Note gin kar cash milayein", Icons.Filled.Savings, Color(0xFF30B0C7)),
    ProTool("items", "Item-wise bikri + munafa", "Kaunsa maal sabse zyada bika", Icons.Filled.Inventory2, Color(0xFFFF3B30)),
    ProTool("qr", "Payment QR", "Party ke liye amount wala UPI QR", Icons.Filled.QrCode2, Color(0xFF0A84FF)),
    ProTool("card", "Visiting card", "Dukan ka digital card banao aur bhejo", Icons.Filled.ContactPage, Color(0xFFFFCC00)),
    ProTool("csv", "Excel / CSV export", "Saara data Excel file me", Icons.Filled.TableChart, Color(0xFF1D6F42)),
    ProTool("notify", "Roz ka hisaab notification", "Har raat aaj ka total phone par", Icons.Filled.NotificationsActive, Color(0xFFFF9F0A)),
    ProTool("poster", "Offer poster ✨", "Sale, tyohar, naya maal, Scan & Pay poster", Icons.Filled.Campaign, Color(0xFFE85D04)),
    ProTool("msgs", "Tyohar / offer message", "Ready message: Diwali, Holi, offer…", Icons.Filled.Celebration, Color(0xFFB5179E)),
    ProTool("diary", "Dukan diary", "Yaad rakhne wali baatein, kaam ki list", Icons.Filled.EditNote, Color(0xFF8E6E53)),
    ProTool("trash", "Recycle bin", "Delete hui entry wapas laao (30 din)", Icons.Filled.DeleteSweep, Color(0xFF8E8E93)),
)

@Composable
fun ProScreen(key: String, nav: Nav) {
    when (key) {
        "voice" -> VoiceEntryScreen(nav)
        "kharcha" -> KharchaScreen(nav)
        "calendar" -> DueCalendarScreen(nav)
        "byaj" -> ByajScreen(nav)
        "groups" -> GroupsScreen(nav)
        "staff" -> StaffScreen(nav)
        "galla" -> GallaScreen(nav)
        "items" -> ItemSalesScreen(nav)
        "qr" -> PayQrScreen(nav)
        "card" -> VisitingCardScreen(nav)
        "csv" -> CsvScreen(nav)
        "notify" -> NotifyScreen(nav)
        "trash" -> TrashScreen(nav)
        "poster" -> PosterScreen(nav)
        "msgs" -> ReadyMsgScreen(nav)
        "diary" -> DiaryScreen(nav)
        else -> ProHub(nav)
    }
}

@Composable
private fun ProPage(title: String, nav: Nav, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    val backLabel = remember { nav.backLabel() }
    val behavior = rememberLargeTitle()
    Scaffold(
        modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { IosNavBar(title, behavior, backLabel = backLabel, onBack = { nav.back() }, actions = actions) }
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = content
        )
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ios.cell).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CompositionLocalProvider(LocalOnCell provides true) { content() }
    }
}

@Composable
private fun Hint(text: String) = Text(text, fontSize = 13.sp, color = ios.secondaryLabel)

@Composable
private fun Big(text: String, color: Color = ios.label) = Text(text, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)

private fun toast(ctx: android.content.Context, s: String) = Toast.makeText(ctx, tr(s), Toast.LENGTH_SHORT).show()

@Composable
private fun ProHub(nav: Nav) {
    ProPage("Business tools", nav) {
        Hint("v1.0.45 ke naye tools. Sab offline chalte hain.")
        PRO_TOOLS.forEachIndexed { i, t ->
            GroupCell(i, PRO_TOOLS.size, onClick = { nav.go(Screen.Pro(t.key)) }, separatorInset = 57.dp) {
                IconTile(t.icon, t.color); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, fontSize = 17.sp, color = ios.label)
                    Text(t.sub, fontSize = 12.sp, color = ios.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Chevron()
            }
        }
        SectionFooter("Amount box me 🧮 calculator bhi hai: 250×4+30 jaisa hisaab seedha box me. App lock: Settings > App lock (PIN).")
    }
}

// ------------------------------------------------------------------ calculator (amount boxes)

@Composable
fun CalcDialog(start: String, onDismiss: () -> Unit, onUse: (String) -> Unit) {
    var expr by remember { mutableStateOf(start) }
    val result = Calc.eval(expr)
    IosAlert(
        onDismissRequest = onDismiss,
        title = { Text("Calculator") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(expr.ifEmpty { "0" }, Modifier.fillMaxWidth(), textAlign = TextAlign.End, fontSize = 22.sp, color = ios.label, maxLines = 2)
                Text(if (result != null) "= " + money(result) else " ", Modifier.fillMaxWidth(), textAlign = TextAlign.End, fontSize = 16.sp, color = ios.secondaryLabel)
                val rows = listOf(listOf("7", "8", "9", "÷"), listOf("4", "5", "6", "×"), listOf("1", "2", "3", "−"), listOf(".", "0", "%", "+"), listOf("C", "⌫"))
                rows.forEach { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        r.forEach { k ->
                            val op = k in listOf("÷", "×", "−", "+")
                            Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (op) ios.tint.copy(alpha = 0.15f) else ios.fill)
                                .clickable {
                                    expr = when (k) { "C" -> ""; "⌫" -> expr.dropLast(1); else -> (expr + k).take(60) }
                                }, contentAlignment = Alignment.Center) {
                                Text(k, fontSize = 20.sp, color = if (op) ios.tint else ios.label, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val r = result
                if (r != null && r >= 0) onUse(String.format(Locale.ENGLISH, "%.2f", r).removeSuffix(".00")) else onDismiss()
            }) { Text("Use karein", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontSize = 17.sp) } }
    )
}

// ------------------------------------------------------------------ 1. voice entry

@Composable
private fun VoiceEntryScreen(nav: Nav) {
    val ctx = LocalContext.current
    var heard by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var gave by remember { mutableStateOf(true) }
    var saved by remember { mutableStateOf<String?>(null) }
    val parties = remember(Repo.tick) { Repo.parties() }
    fun onText(txt: String) {
        heard = txt
        val p = VoiceParse.parse(txt)
        val match = parties.firstOrNull { it.name.equals(p.name, true) }
            ?: parties.firstOrNull { p.name.isNotBlank() && (it.name.startsWith(p.name, true) || p.name.startsWith(it.name, true)) }
        name = match?.name ?: p.name
        amount = if (p.amount > 0) p.amount.plain() else ""
        p.gave?.let { gave = it }
        saved = null
    }
    var listening by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    var micDenied by remember { mutableStateOf(false) }
    var noService by remember { mutableStateOf(false) }
    var askMic by remember { mutableStateOf(false) }
    val recognizer = remember {
        try { if (android.speech.SpeechRecognizer.isRecognitionAvailable(ctx)) android.speech.SpeechRecognizer.createSpeechRecognizer(ctx) else null } catch (_: Throwable) { null }
    }
    DisposableEffect(Unit) { onDispose { try { recognizer?.destroy() } catch (_: Throwable) {} } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val txt = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!txt.isNullOrBlank()) onText(txt)
    }
    fun speechIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Boliye: Ramesh ko 500 diye")
    }
    /** fallback: the Google voice popup */
    fun popup() {
        try { launcher.launch(speechIntent()) } catch (_: Throwable) { noService = true }
    }
    fun startInApp() {
        val r = recognizer ?: return popup()
        try {
            r.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) { listening = true; partial = ""; noService = false }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { listening = false }
                override fun onError(error: Int) {
                    listening = false
                    when (error) {
                        android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> micDenied = true
                        android.speech.SpeechRecognizer.ERROR_NO_MATCH, android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> toast(ctx, "Kuch suna nahi, mic dabakar dobara bolein")
                        else -> popup()
                    }
                }
                override fun onResults(results: android.os.Bundle?) {
                    listening = false
                    results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { onText(it) }
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { partial = it }
                }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            r.startListening(speechIntent())
            listening = true
        } catch (_: Throwable) { listening = false; popup() }
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { micDenied = false; startInApp() } else micDenied = true
    }
    fun hasMic() = androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
    fun listen() {
        if (listening) { try { recognizer?.stopListening() } catch (_: Throwable) {}; listening = false; return }
        if (recognizer == null) { popup(); return }
        if (hasMic()) startInApp() else askMic = true
    }
    if (askMic) ConfirmDialog("Mic ki permission", "Bol kar entry ke liye mic chahiye. Awaaz sirf entry likhne ke liye use hoti hai, kahin bheji ya save nahi hoti.", "Permission do", danger = false,
        onConfirm = { askMic = false; micPerm.launch(Manifest.permission.RECORD_AUDIO) }, onDismiss = { askMic = false })
    ProPage("Bol kar entry", nav) {
        Card {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(Color(0xFFFF2D55)).clickable { listen() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Mic, "Bolein", tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }
            Text(if (listening) "Sun raha hoon… bolte rahiye 🎤" else "Mic dabao aur bolo", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                color = if (listening) Color(0xFFFF2D55) else ios.label, fontSize = 17.sp, fontWeight = if (listening) FontWeight.SemiBold else FontWeight.Normal)
            if (listening && partial.isNotBlank()) Text("\"$partial\"", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = ios.secondaryLabel)
            if (listening) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFFFF2D55), trackColor = ios.fill)
            Hint("Jaise: \"Ramesh ko 500 diye\", \"Suresh se 1200 mile\", \"Mohan 300 udhar\". Naya naam ho to party apne aap ban jayegi.")
        }
        if (micDenied) Card {
            Text("Mic ki permission band hai", fontWeight = FontWeight.SemiBold, color = ios.label)
            Hint("Settings > Permissions > Microphone > Allow karein, phir wapas aakar mic dabayein.")
            Button(onClick = {
                try { ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", ctx.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
            }, Modifier.fillMaxWidth()) { Text("Settings kholo") }
            OutlinedButton(onClick = { micDenied = false; popup() }, Modifier.fillMaxWidth()) { Text("Google popup se bolein") }
        }
        if (noService) Card {
            Text("Is phone me awaaz pehchanne wali service nahi mili", fontWeight = FontWeight.SemiBold, color = ios.label)
            Hint("Play Store se \"Google\" app install/update karein, ya Settings > Apps me \"Speech Services by Google\" chalu karein. Phir dobara mic dabayein.")
            Button(onClick = {
                val u = android.net.Uri.parse("market://details?id=com.google.android.googlequicksearchbox")
                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: Throwable) { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.googlequicksearchbox")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {} }
            }, Modifier.fillMaxWidth()) { Text("Google app kholo / install karo") }
        }
        if (heard.isNotBlank()) {
            Card {
                Hint("Suna: \"$heard\"")
                IosField(name, { name = it }, "Party ka naam")
                NumField(amount, { amount = it }, "Amount", prefix = "₹")
                IosSegmented(listOf("Diye (udhar)", "Mile (jama)"), if (gave) "Diye (udhar)" else "Mile (jama)", { gave = it.startsWith("Diye") })
                val exists = parties.firstOrNull { it.name.equals(name.trim(), true) }
                Hint(if (exists != null) "Purani party: ${exists.name} · baaki ${rupees(exists.balance)}" else if (name.isNotBlank()) "Nayi party banegi: ${name.trim()}" else "")
                Button(onClick = {
                    val amt = amount.toAmount()
                    if (name.isBlank() || amt <= 0) { toast(ctx, "Naam aur amount dono chahiye"); return@Button }
                    val pid = exists?.id ?: Repo.addParty(name.trim(), "", "CUSTOMER", 0.0, "")
                    Repo.addEntry(pid, gave, amt, S.defMode, "Voice entry", System.currentTimeMillis(), null)
                    saved = "${name.trim()} · ${if (gave) "Diye" else "Mile"} ${rupees(amt)} ✓"
                    heard = ""
                }, Modifier.fillMaxWidth()) { Text("Save karein") }
            }
        }
        saved?.let { Card { Text("Entry save ho gayi: $it", color = IosGreen, fontWeight = FontWeight.SemiBold) } }
    }
}

// ------------------------------------------------------------------ 2. kharcha tracker

private val EXP_CATS = listOf("Kiraya", "Bijli", "Salary", "Chai-Nashta", "Transport", "Maal kharid", "Mobile/Net", "Repair", "Ghar ka", "Other")

private fun monthStart(offset: Int): Long = Calendar.getInstance().apply {
    timeInMillis = startOfMonth(System.currentTimeMillis()); add(Calendar.MONTH, offset)
}.timeInMillis

private fun monthName(ms: Long) = SimpleDateFormat("MMMM yyyy", Locale("en", "IN")).format(Date(ms))

@Composable
private fun KharchaScreen(nav: Nav) {
    val ctx = LocalContext.current
    val tick = Repo.tick
    var off by remember { mutableStateOf(0) }
    var add by remember { mutableStateOf(false) }
    var budgetCat by remember { mutableStateOf<String?>(null) }
    val start = monthStart(off); val end = monthStart(off + 1); val pStart = monthStart(off - 1)
    val all = remember(tick) { Repo.cashList().filter { !it.cashIn } }
    val month = all.filter { it.date in start until end }
    val prev = all.filter { it.date in pStart until start }.sumOf { it.amount }
    val total = month.sumOf { it.amount }
    val budgets = remember(tick) { ProStore.obj("exp_budgets") }
    val byCat = month.groupBy { it.category.ifBlank { "Other" } }.mapValues { e -> e.value.sumOf { it.amount } }.toList().sortedByDescending { it.second }
    ProPage("Kharcha", nav, actions = { NavIcon(Icons.Filled.Add, "Kharcha jodein") { add = true } }) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NavIcon(Icons.Filled.ChevronLeft, "Pichhla") { off-- }
                Text(monthName(start), Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, color = ios.label)
                NavIcon(Icons.Filled.ChevronRight, "Agla") { if (off < 0) off++ }
            }
            Big(rupees(total), IosRed)
            val diff = total - prev
            Hint(if (prev <= 0) "Is mahine ka kul kharcha" else if (diff > 0) "Pichhle mahine se ${rupees(diff)} zyada" else "Pichhle mahine se ${rupees(-diff)} kam 👍")
            Button(onClick = { add = true }, Modifier.fillMaxWidth()) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Kharcha jodein") }
        }
        SectionHeader("Category wise")
        if (byCat.isEmpty()) Hint("Is mahine koi kharcha nahi. Cashbook ke \"Gaya\" bhi yahan dikhte hain.")
        byCat.forEach { (cat, amt) ->
            val b = budgets.optDouble(cat, 0.0)
            Card(Modifier.clickable { budgetCat = cat }) {
                Row { Text(cat, Modifier.weight(1f), color = ios.label, fontSize = 16.sp); Text(rupees(amt), fontWeight = FontWeight.SemiBold, color = ios.label) }
                val frac = if (b > 0) (amt / b).toFloat() else (if (total > 0) (amt / total).toFloat() else 0f)
                LinearProgressIndicator(progress = { frac.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = if (b > 0 && amt > b) IosRed else ios.tint, trackColor = ios.fill)
                Hint(if (b > 0) (if (amt > b) "Budget ${rupees(b)} se ${rupees(amt - b)} zyada!" else "Budget ${rupees(b)} · ${rupees(b - amt)} bacha") else "Budget set karne ke liye tap karein")
            }
        }
        SectionHeader("Is mahine ke kharche")
        month.take(60).forEach { e ->
            Card { Row { Column(Modifier.weight(1f)) { Text(e.category, color = ios.label); Hint(fmtDate(e.date) + (if (e.note.isNotBlank()) " · " + e.note else "")) }; Text(rupees(e.amount), color = IosRed) } }
        }
    }
    if (add) {
        var amt by remember { mutableStateOf("") }
        var cat by remember { mutableStateOf(EXP_CATS[0]) }
        var note by remember { mutableStateOf("") }
        val cats = remember { (EXP_CATS + all.map { it.category }.filter { it.isNotBlank() && it != "General" }).distinct() }
        IosSheet(onDismissRequest = { add = false }, title = { Text("Naya kharcha") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumField(amt, { amt = it }, "Amount", prefix = "₹")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        cats.forEach { c -> IosPill(c, c == cat, { cat = c }) }
                    }
                    IosField(note, { note = it }, "Note (optional)")
                    Hint("Ye cashbook me \"Gaya\" entry ban jayegi.")
                }
            },
            confirmButton = { TextButton(onClick = {
                val a = amt.toAmount()
                if (a <= 0) toast(ctx, "Amount daalein") else { Repo.addCash(false, a, cat, note.trim(), S.cashMode, System.currentTimeMillis()); add = false; toast(ctx, "Kharcha jud gaya ✓") }
            }) { Text("Save", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) } },
            dismissButton = { TextButton(onClick = { add = false }) { Text("Cancel", fontSize = 17.sp) } })
    }
    budgetCat?.let { cat ->
        var v by remember { mutableStateOf(budgets.optDouble(cat, 0.0).plain()) }
        IosAlert(onDismissRequest = { budgetCat = null }, title = { Text("$cat ka mahine ka budget") },
            text = { NumField(v, { v = it }, "", prefix = "₹") },
            confirmButton = { TextButton(onClick = {
                val o = ProStore.obj("exp_budgets"); val a = v.toAmount()
                if (a > 0) o.put(cat, a) else o.remove(cat)
                ProStore.put("exp_budgets", o); budgetCat = null
            }) { Text("Save", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) } },
            dismissButton = { TextButton(onClick = { budgetCat = null }) { Text("Cancel", fontSize = 17.sp) } })
    }
}

// ------------------------------------------------------------------ 3. vasooli calendar

@Composable
private fun DueCalendarScreen(nav: Nav) {
    val ctx = LocalContext.current
    val tick = Repo.tick
    var off by remember { mutableStateOf(0) }
    var sel by remember { mutableStateOf<Long?>(null) }
    val parties = remember(tick) { Repo.parties().associateBy { it.id } }
    val dues = remember(tick) {
        // one row per party per due day, only parties that still owe money
        Repo.dueEntries().filter { (parties[it.first]?.balance ?: 0.0) > 0.005 }
            .groupBy { startOfDay(it.third) to it.first }.map { (k, v) -> Triple(k.second, v.sumOf { it.second }, k.first) }
    }
    val start = monthStart(off); val end = monthStart(off + 1)
    val inMonth = dues.filter { it.third in start until end }
    val byDay = inMonth.groupBy { it.third }
    val today = startOfDay(System.currentTimeMillis())
    ProPage("Vasooli calendar", nav) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NavIcon(Icons.Filled.ChevronLeft, "Pichhla") { off--; sel = null }
                Text(monthName(start), Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, color = ios.label)
                NavIcon(Icons.Filled.ChevronRight, "Agla") { off++; sel = null }
            }
            Row { listOf("S", "M", "T", "W", "T", "F", "S").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = ios.secondaryLabel) } }
            val cal = Calendar.getInstance().apply { timeInMillis = start }
            val firstDow = cal.get(Calendar.DAY_OF_WEEK) - 1
            val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val cells = firstDow + days
            for (w in 0 until (cells + 6) / 7) {
                Row {
                    for (d in 0 until 7) {
                        val n = w * 7 + d - firstDow + 1
                        if (n < 1 || n > days) Spacer(Modifier.weight(1f).height(fitDp(48.dp))) else {
                            val dayMs = Calendar.getInstance().apply { timeInMillis = start; set(Calendar.DAY_OF_MONTH, n) }.let { startOfDay(it.timeInMillis) }
                            val list = byDay[dayMs]
                            val amt = list?.sumOf { it.second } ?: 0.0
                            val bg = when { sel == dayMs -> ios.tint; list != null && dayMs < today -> IosRed.copy(alpha = 0.18f); list != null -> IosGreen.copy(alpha = 0.18f); else -> Color.Transparent }
                            Column(Modifier.weight(1f).height(fitDp(48.dp)).padding(2.dp).clip(RoundedCornerShape(8.dp)).background(bg).clickable { sel = dayMs },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("$n", fontSize = 14.sp, color = if (sel == dayMs) Color.White else if (dayMs == today) ios.tint else ios.label,
                                    fontWeight = if (dayMs == today) FontWeight.Bold else FontWeight.Normal)
                                if (list != null) Text(compactRupees(amt), fontSize = 9.sp, maxLines = 1, color = if (sel == dayMs) Color.White else ios.secondaryLabel)
                            }
                        }
                    }
                }
            }
            Hint("Hara = aane wala, laal = tareekh nikal gayi. Due date entry me \"Diye\" ke saath daalein.")
        }
        val showList = if (sel != null) byDay[sel!!].orEmpty() else inMonth.sortedBy { it.third }
        SectionHeader(if (sel != null) "${fmtDate(sel!!)} ki vasooli" else "Is mahine ki vasooli · ${rupees(inMonth.sumOf { it.second })}")
        if (showList.isEmpty()) Hint("Koi due date nahi.")
        showList.forEach { (pid, amt, day) ->
            val p = parties[pid] ?: return@forEach
            Card(Modifier.clickable { nav.go(Screen.PartyDetail(pid)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PartyAvatar(p.id, p.name, 36.dp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Hint("Due ${fmtDate(day)} · ${rupees(amt)} · kul baaki ${rupees(p.balance)}")
                    }
                    if (p.phone.isNotBlank()) NavIcon(Icons.Filled.Chat, "WhatsApp") {
                        whatsapp(ctx, p.phone, "Namaste ${p.name}, ${Repo.businessName()} ka ${rupees(p.balance)} baaki hai. Kripya jaldi bhej dein 🙏")
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 4. byaj on overdue

@Composable
private fun ByajScreen(nav: Nav) {
    val ctx = LocalContext.current
    val tick = Repo.tick
    var rate by remember { mutableStateOf(Repo.setting("byaj_rate", "2")) }
    var ask by remember { mutableStateOf<Pair<Party, Double>?>(null) }
    val since = remember(tick) { Repo.overdueSince() }
    val r = rate.toAmount()
    val now = System.currentTimeMillis()
    val rows = remember(tick, r) {
        Repo.parties().filter { it.balance > 0.005 && since.containsKey(it.id) }.map { p ->
            val days = ((now - since[p.id]!!) / 86_400_000L).coerceAtLeast(0)
            Triple(p, days, Math.round(p.balance * r / 100.0 * days / 30.0 * 100.0) / 100.0)
        }.sortedByDescending { it.third }
    }
    ProPage("Byaj (late payment)", nav) {
        Card {
            NumField(rate, { rate = it; Repo.setSetting("byaj_rate", it) }, "Byaj dar (% har mahine)", suffix = "% / mahina")
            Hint("Simple byaj: baaki × dar × (due ke baad ke din ÷ 30). Sirf dikhata hai; aap \"Jodein\" dabayenge tabhi khate me judega.")
            Big(rupees(rows.sumOf { it.third }), Color(0xFFAF52DE))
            Hint("${rows.size} logon par kul banta byaj")
        }
        if (rows.isEmpty()) Hint("Kisi ki due date nahi nikli. 🎉")
        rows.forEach { (p, days, amt) ->
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { nav.go(Screen.PartyDetail(p.id)) }) {
                        Text(p.name, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Hint("Baaki ${rupees(p.balance)} · $days din late")
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(rupees(amt), fontWeight = FontWeight.SemiBold, color = Color(0xFFAF52DE))
                        TextButton(onClick = { ask = p to amt }, enabled = amt >= 1) { Text("Jodein") }
                    }
                }
            }
        }
    }
    ask?.let { (p, amt) ->
        ConfirmDialog("Byaj jodein?", "${p.name} ke khate me ${rupees(amt)} byaj \"Diye\" entry ke roop me judega.", "Jodein", danger = false,
            onConfirm = { Repo.addEntry(p.id, true, amt, "CASH", "Byaj ${rate}%/mahina", System.currentTimeMillis(), null); toast(ctx, "Byaj jud gaya ✓") },
            onDismiss = { ask = null })
    }
}

// ------------------------------------------------------------------ 5. party groups

@Composable
private fun GroupsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val tick = Repo.tick
    val groups = remember(tick) { ProStore.arr("pgroups").let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } }
    val map = remember(tick) { ProStore.obj("pgroup_map") }
    val parties = remember(tick) { Repo.parties() }
    var sel by remember(groups) { mutableStateOf(groups.firstOrNull()) }
    var newG by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    var delG by remember { mutableStateOf(false) }
    ProPage("Party groups", nav, actions = { NavIcon(Icons.Filled.Add, "Naya group") { newG = true } }) {
        if (groups.isEmpty()) {
            Card {
                Text("Abhi koi group nahi", color = ios.label, fontWeight = FontWeight.SemiBold)
                Hint("Jaise \"Gaon wale\", \"Line 2\", \"Wholesale\", \"Rishtedaar\". Group me kitna baaki hai ek nazar me.")
                Button(onClick = { newG = true }, Modifier.fillMaxWidth()) { Text("Naya group banayein") }
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                groups.forEach { g -> IosPill("$g (${parties.count { map.optString(it.id.toString()) == g }})", g == sel, { sel = g }) }
            }
            sel?.let { g ->
                val members = parties.filter { map.optString(it.id.toString()) == g }
                Card {
                    Text(g, fontWeight = FontWeight.SemiBold, color = ios.label, fontSize = 18.sp)
                    Row {
                        Column(Modifier.weight(1f)) { Hint("Lena hai"); Text(rupees(members.filter { it.balance > 0 }.sumOf { it.balance }), color = IosGreen, fontWeight = FontWeight.SemiBold) }
                        Column(Modifier.weight(1f)) { Hint("Dena hai"); Text(rupees(-members.filter { it.balance < 0 }.sumOf { it.balance }), color = IosRed, fontWeight = FontWeight.SemiBold) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pick = true }, Modifier.weight(1f)) { Text("Party jodein / hatayein") }
                        TextButton(onClick = { delG = true }) { Text("Group hatayein", color = IosRed) }
                    }
                }
                members.sortedByDescending { it.balance }.forEach { p ->
                    GroupCell(0, 1, onClick = { nav.go(Screen.PartyDetail(p.id)) }) {
                        PartyAvatar(p.id, p.name, 34.dp); Spacer(Modifier.width(10.dp))
                        Text(p.name, Modifier.weight(1f), color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(rupees(kotlin.math.abs(p.balance)), color = if (p.balance >= 0) IosGreen else IosRed)
                    }
                }
                if (members.isEmpty()) Hint("Is group me koi party nahi. \"Party jodein\" dabayein.")
            }
        }
    }
    if (newG) {
        var n by remember { mutableStateOf("") }
        IosAlert(onDismissRequest = { newG = false }, title = { Text("Naya group") }, text = { IosField(n, { n = it.take(30) }, "", placeholder = "Group ka naam") },
            confirmButton = { TextButton(onClick = {
                val name = n.trim()
                if (name.isNotEmpty() && name !in groups) { ProStore.put("pgroups", JSONArray(groups + name)); sel = name }
                newG = false
            }) { Text("Banayein", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) } },
            dismissButton = { TextButton(onClick = { newG = false }) { Text("Cancel", fontSize = 17.sp) } })
    }
    if (pick && sel != null) {
        val g = sel!!
        var q by remember { mutableStateOf("") }
        IosSheet(onDismissRequest = { pick = false }, title = { Text("$g me party") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IosSearchField(q, { q = it }, "Naam khojein")
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        parties.filter { q.isBlank() || it.name.contains(q, true) }.forEach { p ->
                            val cur = map.optString(p.id.toString())
                            Row(Modifier.fillMaxWidth().clickable {
                                val m = ProStore.obj("pgroup_map")
                                if (cur == g) m.remove(p.id.toString()) else m.put(p.id.toString(), g)
                                ProStore.put("pgroup_map", m)
                            }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(cur == g, null)
                                Text(p.name, Modifier.weight(1f), color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (cur.isNotBlank() && cur != g) Hint(cur)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pick = false }) { Text("Ho gaya", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) } })
    }
    if (delG && sel != null) {
        val g = sel!!
        ConfirmDialog("\"$g\" group hatayein?", "Sirf group hatega. Party aur unka hisaab safe rahega.", "Hatayein",
            onConfirm = {
                val m = ProStore.obj("pgroup_map"); val ks = m.keys().asSequence().toList()
                ks.forEach { if (m.optString(it) == g) m.remove(it) }
                ProStore.put("pgroup_map", m); ProStore.put("pgroups", JSONArray(groups - g)); sel = null
                toast(ctx, "Group hata diya")
            }, onDismiss = { delG = false })
    }
}

// ------------------------------------------------------------------ 6. staff attendance + salary

private fun ymKey(ms: Long) = SimpleDateFormat("yyyyMM", Locale.ENGLISH).format(Date(ms))

@Composable
private fun StaffScreen(nav: Nav) {
    val ctx = LocalContext.current
    val tick = Repo.tick
    var day by remember { mutableStateOf(startOfDay(System.currentTimeMillis())) }
    var addStaff by remember { mutableStateOf(false) }
    var adv by remember { mutableStateOf<JSONObject?>(null) }
    var delStaff by remember { mutableStateOf<JSONObject?>(null) }
    val staff = remember(tick) { ProStore.list(ProStore.arr("staff")) }
    val ym = ymKey(day)
    val att = remember(tick, ym) { ProStore.obj("att_$ym") }
    val advances = remember(tick) { ProStore.list(ProStore.arr("staff_adv")) }
    val dom = Calendar.getInstance().apply { timeInMillis = day }.get(Calendar.DAY_OF_MONTH)
    val daysInMonth = Calendar.getInstance().apply { timeInMillis = day }.getActualMaximum(Calendar.DAY_OF_MONTH)
    val mStart = startOfMonth(day); val mEnd = Calendar.getInstance().apply { timeInMillis = mStart; add(Calendar.MONTH, 1) }.timeInMillis
    fun mark(sid: String, v: String) {
        val o = ProStore.obj("att_$ym"); val k = "${sid}_$dom"
        if (o.optString(k) == v) o.remove(k) else o.put(k, v)
        ProStore.put("att_$ym", o)
    }
    ProPage("Staff hazri", nav, actions = { NavIcon(Icons.Filled.PersonAdd, "Staff jodein") { addStaff = true } }) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NavIcon(Icons.Filled.ChevronLeft, "Pichhla din") { day = addDays(day, -1) }
                Text(SimpleDateFormat("EEE, d MMM yyyy", Locale("en", "IN")).format(Date(day)), Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, color = ios.label)
                NavIcon(Icons.Filled.ChevronRight, "Agla din") { if (day < startOfDay(System.currentTimeMillis())) day = addDays(day, 1) }
            }
            if (staff.isEmpty()) {
                Hint("Apne staff ka naam aur mahine ki salary jodein. Roz P / A / ½ lagayein, salary apne aap banegi.")
                Button(onClick = { addStaff = true }, Modifier.fillMaxWidth()) { Text("Staff jodein") }
            }
        }
        staff.forEach { s ->
            val sid = s.optString("id")
            val cur = att.optString("${sid}_$dom")
            var p = 0; var h = 0; var a = 0
            for (d in 1..daysInMonth) when (att.optString("${sid}_$d")) { "P" -> p++; "H" -> h++; "A" -> a++ }
            val salary = s.optDouble("salary", 0.0)
            val earned = salary / daysInMonth * (p + 0.5 * h)
            val advSum = advances.filter { it.optString("sid") == sid && it.optLong("date") in mStart until mEnd }.sumOf { it.optDouble("amt", 0.0) }
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InitialsAvatar(s.optString("name"), 36.dp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.optString("name"), color = ios.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Hint("Salary ${rupees(salary)}/mahina")
                    }
                    listOf("P" to IosGreen, "H" to IosOrange, "A" to IosRed).forEach { (k, col) ->
                        Box(Modifier.padding(start = 6.dp).size(40.dp).clip(CircleShape).background(if (cur == k) col else col.copy(alpha = 0.15f)).clickable { mark(sid, k) },
                            contentAlignment = Alignment.Center) { Text(if (k == "H") "½" else k, color = if (cur == k) Color.White else col, fontWeight = FontWeight.Bold) }
                    }
                }
                Hint("${monthName(mStart)}: Hazir $p · Aadha din $h · Chhutti $a")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bani salary ${rupees(earned)}", color = ios.label)
                        Hint("Advance ${rupees(advSum)} · Dena baaki ${rupees((earned - advSum).coerceAtLeast(0.0))}")
                    }
                    TextButton(onClick = { adv = s }) { Text("Advance") }
                    NavIcon(Icons.Filled.DeleteOutline, "Hatayein") { delStaff = s }
                }
            }
        }
        if (staff.isNotEmpty()) Hint("P = hazir, ½ = aadha din, A = chhutti. Dobara dabane se hat jata hai. Salary = salary ÷ mahine ke din × hazri.")
    }
    if (addStaff) {
        var n by remember { mutableStateOf("") }; var sal by remember { mutableStateOf("") }; var ph by remember { mutableStateOf("") }
        IosAlert(onDismissRequest = { addStaff = false }, title = { Text("Naya staff") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosField(n, { n = it.take(40) }, "", placeholder = "Naam")
                NumField(sal, { sal = it }, "", prefix = "₹", suffix = "/mahina")
                IosField(ph, { ph = it.filter { c -> c.isDigit() || c == '+' }.take(15) }, "", placeholder = "Mobile (optional)", keyboard = androidx.compose.ui.text.input.KeyboardType.Phone)
            } },
            confirmButton = { TextButton(onClick = {
                if (n.isBlank()) toast(ctx, "Naam daalein") else {
                    val a = ProStore.arr("staff")
                    a.put(JSONObject().apply { put("id", System.currentTimeMillis().toString()); put("name", n.trim()); put("salary", sal.toAmount()); put("phone", ph) })
                    ProStore.put("staff", a); addStaff = false
                }
            }) { Text("Save", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) } },
            dismissButton = { TextButton(onClick = { addStaff = false }) { Text("Cancel", fontSize = 17.sp) } })
    }
    adv?.let { s ->
        var amt by remember { mutableStateOf("") }; var toCash by remember { mutableStateOf(true) }
        IosAlert(onDismissRequest = { adv = null }, title = { Text("${s.optString("name")} ko advance") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField(amt, { amt = it }, "", prefix = "₹")
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Cashbook me bhi likhein", Modifier.weight(1f), color = ios.label); IosSwitch(toCash, { toCash = it }) }
            } },
            confirmButton = { TextButton(onClick = {
                val a = amt.toAmount()
                if (a > 0) {
                    val arr = ProStore.arr("staff_adv")
                    arr.put(JSONObject().apply { put("sid", s.optString("id")); put("amt", a); put("date", day + 12 * 3_600_000L) })
                    ProStore.put("staff_adv", arr)
                    if (toCash) Repo.addCash(false, a, "Salary", "Advance: " + s.optString("name"), "CASH", System.currentTimeMillis())
                    toast(ctx, "Advance likh liya ✓")
                }
                adv = null
            }) { Text("Save", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) } },
            dismissButton = { TextButton(onClick = { adv = null }) { Text("Cancel", fontSize = 17.sp) } })
    }
    delStaff?.let { s ->
        ConfirmDialog("${s.optString("name")} ko hatayein?", "Is staff ki list se naam hat jayega.", "Hatayein",
            onConfirm = { ProStore.put("staff", JSONArray(staff.filter { it.optString("id") != s.optString("id") })) }, onDismiss = { delStaff = null })
    }
}

// ------------------------------------------------------------------ 7. galla milan (cash counter)

private val NOTES = listOf(500, 200, 100, 50, 20, 10, 5, 2, 1)

@Composable
private fun GallaScreen(nav: Nav) {
    val ctx = LocalContext.current
    val tick = Repo.tick
    val saved = remember { ProStore.obj("galla_last") }
    val counts = remember { mutableStateMapOf<Int, String>().apply { NOTES.forEach { n -> val v = saved.optInt(n.toString(), 0); if (v > 0) put(n, v.toString()) } } }
    var askAdj by remember { mutableStateOf(false) }
    val total = NOTES.sumOf { n -> (counts[n]?.toLongOrNull() ?: 0L) * n }.toDouble()
    val book = remember(tick) { Repo.cashList().filter { it.mode == "CASH" }.sumOf { if (it.cashIn) it.amount else -it.amount } }
    val diff = total - book
    ProPage("Galla milan", nav) {
        Card {
            Hint("Galle me har note kitne hain, likhein. App jod kar cashbook se milayega.")
            NOTES.forEach { n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("₹$n ×", Modifier.width(80.dp), color = ios.label, fontSize = 17.sp)
                    IosField(counts[n] ?: "", { v -> counts[n] = v.filter { it.isDigit() }.take(5) }, "", Modifier.weight(1f),
                        keyboard = androidx.compose.ui.text.input.KeyboardType.Number, placeholder = "0")
                    Text(rupees(((counts[n]?.toLongOrNull() ?: 0L) * n).toDouble()), Modifier.width(110.dp), textAlign = TextAlign.End, color = ios.secondaryLabel)
                }
            }
        }
        Card {
            Row { Text("Galle me kul", Modifier.weight(1f), color = ios.label); Big(rupees(total)) }
            Row { Text("Cashbook ke hisaab se (Cash)", Modifier.weight(1f), color = ios.secondaryLabel); Text(rupees(book), color = ios.label) }
            val ok = kotlin.math.abs(diff) < 0.5
            Text(if (ok) "Hisaab barabar ✅" else if (diff > 0) "Galle me ${rupees(diff)} zyada hai" else "Galle me ${rupees(-diff)} kam hai",
                color = if (ok) IosGreen else IosRed, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val o = JSONObject(); NOTES.forEach { n -> counts[n]?.toIntOrNull()?.let { if (it > 0) o.put(n.toString(), it) } }
                    o.put("ts", System.currentTimeMillis()); ProStore.put("galla_last", o); toast(ctx, "Ginti save ho gayi ✓")
                }, Modifier.weight(1f)) { Text("Ginti save") }
                if (!ok) Button(onClick = { askAdj = true }, Modifier.weight(1f)) { Text("Farak jodein") }
            }
            OutlinedButton(onClick = { NOTES.forEach { counts.remove(it) } }, Modifier.fillMaxWidth()) { Text("Saaf karein") }
        }
    }
    if (askAdj) ConfirmDialog("Farak cashbook me jodein?", "Cashbook me ${rupees(kotlin.math.abs(diff))} ki \"${if (diff > 0) "Aaya" else "Gaya"}\" entry (Galla milan) banegi, taaki hisaab barabar ho.", "Jodein", danger = false,
        onConfirm = { Repo.addCash(diff > 0, kotlin.math.abs(diff), "Galla milan", "Note ginti se farak", "CASH", System.currentTimeMillis()); toast(ctx, "Cashbook barabar ✓") },
        onDismiss = { askAdj = false })
}

// ------------------------------------------------------------------ 8. item-wise sales + profit

@Composable
private fun ItemSalesScreen(nav: Nav) {
    val tick = Repo.tick
    var period by remember { mutableStateOf("Is mahine") }
    val start = when (period) { "Is mahine" -> monthStart(0); "3 mahine" -> monthStart(-2); "Is saal" -> Calendar.getInstance().apply { timeInMillis = monthStart(0); set(Calendar.MONTH, 0) }.timeInMillis; else -> 0L }
    val bills = remember(tick) { Repo.bills() }
    val stock = remember(tick) { Repo.items().associateBy { it.name.trim().lowercase(Locale.ROOT) } }
    data class Agg(val name: String, var qty: Double = 0.0, var sale: Double = 0.0, var cost: Double = 0.0, var costKnown: Boolean = true, var bills: Int = 0)
    val rows = remember(tick, start) {
        val m = LinkedHashMap<String, Agg>()
        bills.filter { it.date >= start }.forEach { b ->
            val k0 = (b.subtotal).takeIf { it > 0 } ?: 1.0
            val billDiscFactor = ((b.subtotal - b.discount).coerceAtLeast(0.0)) / k0
            b.lines.forEach { l ->
                val k = l.name.trim().lowercase(Locale.ROOT); if (k.isEmpty()) return@forEach
                val a = m.getOrPut(k) { Agg(l.name.trim()) }
                a.qty += l.qty; a.sale += l.amount * billDiscFactor; a.bills++
                val st = stock[k]
                if (st != null && st.purchasePrice > 0) a.cost += st.purchasePrice * l.qty else a.costKnown = false
            }
        }
        m.values.sortedByDescending { it.sale }
    }
    ProPage("Item-wise bikri", nav) {
        IosPills(listOf("Is mahine", "3 mahine", "Is saal", "Sab"), period, { period = it })
        val sale = rows.sumOf { it.sale }
        val known = rows.filter { it.costKnown }
        Card {
            Row {
                Column(Modifier.weight(1f)) { Hint("Kul bikri (bina GST)"); Big(rupees(sale)) }
                Column(Modifier.weight(1f)) { Hint("Andaazan munafa"); Big(rupees(known.sumOf { it.sale - it.cost }), IosGreen) }
            }
            Hint("Munafa sirf un items ka jinka kharid rate Stock me likha hai (naam same hona chahiye).")
        }
        if (rows.isEmpty()) Hint("Is samay me koi bill nahi.")
        rows.forEachIndexed { i, a ->
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}.", Modifier.width(30.dp), color = ios.secondaryLabel)
                    Column(Modifier.weight(1f)) {
                        Text(a.name, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Hint("${a.qty.plain().ifEmpty { "0" }} bika · ${a.bills} bill")
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(rupees(a.sale), fontWeight = FontWeight.SemiBold, color = ios.label)
                        Text(if (a.costKnown) "Munafa " + rupees(a.sale - a.cost) else "Kharid rate nahi", fontSize = 12.sp,
                            color = if (!a.costKnown) ios.tertiaryLabel else if (a.sale - a.cost >= 0) IosGreen else IosRed)
                    }
                }
                if (sale > 0) LinearProgressIndicator(progress = { (a.sale / sale).toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = ios.tint, trackColor = ios.fill)
            }
        }
    }
}

// ------------------------------------------------------------------ 9. payment QR

@Composable
private fun PayQrScreen(nav: Nav) {
    val ctx = LocalContext.current
    val tick = Repo.tick
    var upi by remember { mutableStateOf(S.upiId) }
    var party by remember { mutableStateOf<Party?>(null) }
    var amount by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    val parties = remember(tick) { Repo.parties().filter { it.balance > 0.005 }.sortedByDescending { it.balance } }
    ProPage("Payment QR", nav) {
        if (S.upiId.isBlank()) {
            Card {
                Text("Pehle apna UPI ID daalein", color = ios.label, fontWeight = FontWeight.SemiBold)
                IosField(upi, { upi = it.trim().take(60) }, "", placeholder = "jaise 98xxxxxx@ybl")
                Button(onClick = { if (upi.contains("@")) S.set("upi_id", upi) else toast(ctx, "Sahi UPI ID daalein (@ ke saath)") }, Modifier.fillMaxWidth()) { Text("Save") }
            }
        } else {
            val amt = amount.toAmount()
            val title = party?.let { "Payment: ${it.name}" } ?: "Payment"
            val uri = upiUri(amt, party?.let { "${Repo.businessName()} - ${it.name}" } ?: "")
            if (uri != null) {
                val bmp = remember(uri, title) { paymentQrImage(title, amt, uri) }
                Card {
                    Image(bmp.asImageBitmap(), "QR", Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(10.dp)))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val f = VisitingCard.saveTemp(ctx, bmp, "payment-qr.png")
                            val ph = party?.phone.orEmpty()
                            if (ph.isNotBlank()) whatsappFile(ctx, f, "image/png", ph, "Namaste ${party?.name}, ${rupees(amt)} is QR se pay kar dein 🙏")
                            else shareFile(ctx, f, "image/png", "Payment QR")
                        }, Modifier.weight(1f)) { Text(if (party?.phone.isNullOrBlank()) "Share" else "WhatsApp pe bhejo") }
                    }
                    Hint("Customer ko phone par ye QR dikhayein ya bhejein. Amount pehle se bhara hota hai.")
                }
            }
            Card {
                NumField(amount, { amount = it }, "Amount (khaali = customer khud bharega)", prefix = "₹")
                if (party != null) TextButton(onClick = { party = null; amount = "" }) { Text("Party hatayein (${party?.name})") }
            }
            SectionHeader("Jinse lena hai")
            IosSearchField(q, { q = it }, "Party khojein")
            parties.filter { q.isBlank() || it.name.contains(q, true) }.take(40).forEach { p ->
                GroupCell(0, 1, onClick = { party = p; amount = p.balance.plain() }) {
                    PartyAvatar(p.id, p.name, 32.dp); Spacer(Modifier.width(10.dp))
                    Text(p.name, Modifier.weight(1f), color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(rupees(p.balance), color = IosGreen)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 10. visiting card (editor, v1.0.46)

@Composable
private fun VisitingCardScreen(nav: Nav) {
    val ctx = LocalContext.current
    val start = remember { VCard.current(ctx) }
    val values = remember { mutableStateMapOf<String, String>().apply { putAll(start.values) } }
    val show = remember { mutableStateMapOf<String, Boolean>().apply { putAll(start.show) } }
    var style by remember { mutableStateOf(start.style) }
    var pos by remember { mutableStateOf(start.photoPos) }
    var round by remember { mutableStateOf(start.roundPhoto) }
    var qr by remember { mutableStateOf(start.showQr) }
    var photo by remember { mutableStateOf(start.photo) }
    var photoMenu by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    fun cfg() = VCard.Cfg(values.toMap(), show.toMap(), style, pos, round, qr, photo)
    val bmp = remember(values.toMap(), show.toMap(), style, pos, round, qr, photo) { VCard.render(cfg()) }

    fun setPhoto(f: java.io.File?) {
        if (f == null) { Toast.makeText(ctx, "Photo nahi khul payi", Toast.LENGTH_SHORT).show(); return }
        try { f.copyTo(VCard.photoFile(ctx), overwrite = true); f.delete() } catch (_: Throwable) {}
        photo = PartyPhotos.loadFile(VCard.photoFile(ctx)); if (pos == "none") pos = "left"
    }
    val camFile = remember { java.io.File(ctx.cacheDir, "vcard_camera.jpg") }
    val camUri = remember { androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", camFile) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) setPhoto(PartyPhotos.prepare(ctx, uri)) }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) setPhoto(PartyPhotos.prepare(ctx, camUri)) }

    ProPage("Visiting card", nav) {
        Card {
            Image(bmp.asImageBitmap(), "Visiting card", Modifier.fillMaxWidth().aspectRatio(1080f / 640f).clip(RoundedCornerShape(10.dp)))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    VCard.save(cfg()); saved = true
                    shareFile(ctx, VisitingCard.saveTemp(ctx, bmp, "visiting-card.png"), "image/png", "Visiting card")
                }, Modifier.weight(1f)) { Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text("Share") }
                OutlinedButton(onClick = {
                    VCard.save(cfg()); saved = true
                    val f = saveCardToGallery(ctx, bmp)
                    Toast.makeText(ctx, if (f) "Card save ho gaya (Gallery > Pictures) ✅" else "Card save ho gaya ✅", Toast.LENGTH_SHORT).show()
                }, Modifier.weight(1f)) { Text("Save") }
            }
        }
        SectionLabel("Photo / logo")
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val p = photo
                Box(Modifier.size(64.dp).clip(if (round) CircleShape else RoundedCornerShape(12.dp)).background(ios.fill).clickable { photoMenu = true }, contentAlignment = Alignment.Center) {
                    if (p != null) Image(p.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    else Icon(Icons.Filled.AddAPhoto, null, tint = ios.tint)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    TextButton(onClick = { photoMenu = true }) { Text(if (p == null) "Photo / logo jodein" else "Photo badlein") }
                }
            }
            Text("Photo kahan ho", fontSize = 13.sp, color = ios.secondaryLabel)
            IosSegmented(VCard.PHOTO_POS.map { it.second }, (VCard.PHOTO_POS.firstOrNull { it.first == pos } ?: VCard.PHOTO_POS[0]).second,
                { v -> pos = VCard.PHOTO_POS.first { it.second == v }.first })
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Gol photo", Modifier.weight(1f), color = ios.label); IosSwitch(round, { round = it }) }
        }
        SectionLabel("Design / rang")
        Card {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VCard.TEMPLATES.forEachIndexed { i, t ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { style = i }) {
                        Box(
                            Modifier.size(64.dp, 40.dp).clip(RoundedCornerShape(8.dp))
                                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(t.c1), Color(t.c2))))
                                .border(if (style == i) 3.dp else 1.dp, if (style == i) ios.tint else ios.separator, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) { Box(Modifier.size(20.dp, 4.dp).background(Color(t.accent))) }
                        Text(t.name, fontSize = 12.sp, color = ios.label)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("UPI QR code dikhao", Modifier.weight(1f), color = ios.label); IosSwitch(qr, { qr = it }) }
        }
        SectionLabel("Card ki details (switch se dikhao / chhupao)")
        Card {
            VCard.FIELDS.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        IosField(values[f.key].orEmpty(), { values[f.key] = it.take(if (f.key == "address" || f.key == "extra") 120 else 60) }, "", placeholder = f.label,
                            keyboard = when (f.key) { "phone", "phone2" -> androidx.compose.ui.text.input.KeyboardType.Phone; "email" -> androidx.compose.ui.text.input.KeyboardType.Email; else -> androidx.compose.ui.text.input.KeyboardType.Text })
                    }
                    Spacer(Modifier.width(8.dp))
                    IosSwitch(show[f.key] ?: true, { show[f.key] = it })
                }
            }
            Button(onClick = { VCard.save(cfg()); saved = true; Toast.makeText(ctx, "Card save ho gaya ✅", Toast.LENGTH_SHORT).show() }, Modifier.fillMaxWidth()) { Text("Card save karein") }
            Hint("Khali chhodne par wo line card par nahi aati. Shuru me dukan ka naam, phone, pata, GSTIN aur UPI Settings se bhar diye gaye hain.")
        }
    }
    if (photoMenu) {
        val actions = mutableListOf(
            SheetAction("Photo lo (camera)") { try { camFile.parentFile?.mkdirs(); camFile.createNewFile(); take.launch(camUri) } catch (_: Exception) {} },
            SheetAction("Gallery se chuno") { try { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } catch (_: Exception) {} },
        )
        if (photo != null) actions.add(SheetAction("Photo hatao", destructive = true) { VCard.photoFile(ctx).delete(); photo = null })
        IosActionSheet("Card ki photo / logo", actions) { photoMenu = false }
    }
}

@Composable
private fun SectionLabel(t: String) = Text(tr(t).uppercase(), fontSize = 13.sp, color = ios.secondaryLabel, modifier = Modifier.padding(start = 8.dp, top = 6.dp))

private fun saveCardToGallery(ctx: android.content.Context, bmp: android.graphics.Bitmap): Boolean = try {
    if (android.os.Build.VERSION.SDK_INT >= 29) {
        val v = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "visiting-card-${System.currentTimeMillis()}.png")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BahiKhata")
        }
        val uri = ctx.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
        uri?.let { u -> ctx.contentResolver.openOutputStream(u)?.use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } } != null
    } else false
} catch (e: Throwable) { false }

// ------------------------------------------------------------------ 11. CSV export

@Composable
private fun CsvScreen(nav: Nav) {
    val ctx = LocalContext.current
    val opts = listOf("parties" to "Party list + baaki", "entries" to "Khata entries", "cash" to "Cashbook", "stock" to "Stock", "bills" to "Bills / quotations")
    val sel = remember { mutableStateMapOf<String, Boolean>().apply { opts.forEach { put(it.first, true) } } }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ProPage("Excel / CSV export", nav) {
        Hint("CSV file Excel, Google Sheets aur WPS me seedha khulti hai. Hindi naam bhi sahi dikhte hain.")
        opts.forEachIndexed { i, (k, label) ->
            GroupCell(i, opts.size, onClick = { sel[k] = !(sel[k] ?: false) }) {
                Text(label, Modifier.weight(1f), color = ios.label, fontSize = 17.sp)
                IosSwitch(sel[k] == true, { sel[k] = it })
            }
        }
        Button(onClick = {
            val which = sel.filter { it.value }.keys
            if (which.isEmpty()) { toast(ctx, "Kam se kam ek chunein"); return@Button }
            busy = true
            scope.launch {
                val files = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { try { CsvExport.build(ctx, which) } catch (_: Throwable) { emptyList() } }
                busy = false
                if (files.isEmpty()) toast(ctx, "File nahi bani") else shareFiles(ctx, files, "text/csv", "BahiKhata Excel/CSV")
            }
        }, Modifier.fillMaxWidth(), enabled = !busy) { Icon(Icons.Filled.TableChart, null); Spacer(Modifier.width(6.dp)); Text(if (busy) "Ban rahi hai…" else "CSV banao aur bhejo") }
        Hint("Ye sirf copy hai. Poora backup/restore ke liye Settings > Backup use karein.")
    }
}

// ------------------------------------------------------------------ 12. daily summary notification

@Composable
private fun NotifyScreen(nav: Nav) {
    val ctx = LocalContext.current
    val tick = Repo.tick
    val on = remember(tick) { DailySummary.on }
    val hour = remember(tick) { DailySummary.hour }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { Repo.setSetting("daily_sum", "1"); DailySummary.sync(ctx); toast(ctx, "Roz ${hourLabel(DailySummary.hour)} baje hisaab aayega ✓") }
        else toast(ctx, "Notification ki permission nahi mili")
    }
    fun turnOn() {
        if (Build.VERSION.SDK_INT >= 33 && !DailySummary.canNotify(ctx)) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        else { Repo.setSetting("daily_sum", "1"); DailySummary.sync(ctx); toast(ctx, "Roz ${hourLabel(hour)} baje hisaab aayega ✓") }
    }
    ProPage("Roz ka hisaab", nav) {
        GroupCell(0, 1) {
            IconTile(Icons.Filled.NotificationsActive, Color(0xFFFF9F0A)); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Roz ka hisaab notification", fontSize = 17.sp, color = ios.label)
                Hint(if (on) "Roz ${hourLabel(hour)} baje" else "Off")
            }
            IosSwitch(on, { v -> if (v) turnOn() else { Repo.setSetting("daily_sum", "0"); DailySummary.sync(ctx) } })
        }
        SectionHeader("Kis time")
        IosPills(listOf(7, 9, 13, 18, 20, 21, 22).map { hourLabel(it) }, hourLabel(hour), { lbl ->
            val h = listOf(7, 9, 13, 18, 20, 21, 22).first { hourLabel(it) == lbl }
            Repo.setSetting("daily_sum_hour", h.toString()); DailySummary.sync(ctx)
        })
        val (t, b) = remember(tick) { DailySummary.text() }
        SectionHeader("Aaj ka notification aisa dikhega")
        Card { Text(t, fontWeight = FontWeight.SemiBold, color = ios.label); Text(b, color = ios.secondaryLabel, fontSize = 14.sp) }
        OutlinedButton(onClick = {
            if (!DailySummary.canNotify(ctx) && Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS) else { DailySummary.show(ctx); toast(ctx, "Test notification bheja") }
        }, Modifier.fillMaxWidth()) { Text("Abhi test karein") }
        Hint("Internet ki zaroorat nahi. Kuch phones (Xiaomi, Oppo, Vivo) me battery saver band karna pad sakta hai.")
    }
}

private fun hourLabel(h: Int): String = when { h == 0 -> "12 AM"; h < 12 -> "$h AM"; h == 12 -> "12 PM"; else -> "${h - 12} PM" }

// ------------------------------------------------------------------ 13. recycle bin

@Composable
private fun TrashScreen(nav: Nav) {
    val ctx = LocalContext.current
    var ver by remember { mutableStateOf(0) }
    val items = remember(ver) { Trash.all() }
    var clear by remember { mutableStateOf(false) }
    ProPage("Recycle bin", nav) {
        Hint("Delete ki hui party, entry, cashbook, stock item aur bill yahan 30 din rehte hain (sirf is phone me).")
        if (items.isEmpty()) Card { Text("Recycle bin khaali hai 🗑️", color = ios.secondaryLabel, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        items.forEach { it ->
            Card {
                Text(it.optString("label"), color = ios.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val n = it.optJSONArray("rows")?.length() ?: 0
                Hint("Delete: ${fmtDateTime(it.optLong("ts"))}" + (if (it.optString("kind") == "party") " · ${n - 1} entry saath me" else ""))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val r = Trash.restore(it); ver++
                        toast(ctx, if (r > 0) "Wapas aa gaya ✓" else "Pehle se maujood hai")
                    }, Modifier.weight(1f)) { Text("Wapas laao") }
                    OutlinedButton(onClick = { Trash.remove(it.optLong("ts")); ver++ }) { Text("Hatao", color = IosRed) }
                }
            }
        }
        if (items.isNotEmpty()) TextButton(onClick = { clear = true }, Modifier.fillMaxWidth()) { Text("Sab khaali karein", color = IosRed) }
    }
    if (clear) ConfirmDialog("Recycle bin khaali karein?", "Ye cheezein phir wapas nahi aayengi.", "Khaali karein", onConfirm = { Trash.clear(); ver++ }, onDismiss = { clear = false })
}


// =================================================================== v1.0.46 extras

private data class PosterKind(val key: String, val name: String, val title: String, val sub: String, val details: String)
private val POSTER_KINDS = listOf(
    PosterKind("offer", "Offer / Sale", "MEGA SALE", "Har saman par 20% tak chhoot", "Sirf is hafte\nJaldi aayein, stock seemit hai"),
    PosterKind("festival", "Tyohar badhai", "Shubh Deepawali", "Aapko aur aapke parivar ko dher saari shubhkamnayein", "Tyohar ki khaas chhoot dukan par"),
    PosterKind("new", "Naya maal", "Naya maal aa gaya!", "Taaza stock, sahi daam", "Aaj hi dukan par aakar dekhein"),
    PosterKind("timing", "Dukan timing", "Dukan ka samay", "Subah 9 baje se raat 9 baje tak", "Ravivar: dopahar 2 baje tak"),
    PosterKind("pay", "Scan & Pay (UPI)", "Scan karein, pay karein", "Kisi bhi UPI app se", ""),
)

private fun renderPoster(ctx: android.content.Context, kind: String, title: String, sub: String, details: String, style: Int): android.graphics.Bitmap {
    val w = 1080; val h = 1350
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val t = VCard.TEMPLATES[style.coerceIn(0, VCard.TEMPLATES.size - 1)]
    val bg = android.graphics.Paint().apply { shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), t.c1, t.c2, android.graphics.Shader.TileMode.CLAMP) }
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
    val dec = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = t.accent; alpha = 45 }
    c.drawCircle(0f, 0f, 320f, dec); c.drawCircle(w.toFloat(), h * 0.62f, 260f, dec)
    val tp = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = t.text }
    fun block(s: String, size: Float, bold: Boolean, y: Float, color: Int = t.text, maxLines: Int = 4): Float {
        if (s.isBlank()) return y
        tp.textSize = size; tp.color = color; tp.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        val l = android.text.StaticLayout.Builder.obtain(s, 0, s.length, tp, w - 160).setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(maxLines).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        c.save(); c.translate(80f, y); l.draw(c); c.restore(); return y + l.height
    }
    // photo / logo from the visiting card
    var y = 90f
    PartyPhotos.loadFile(VCard.photoFile(ctx))?.let { p ->
        val s = minOf(p.width, p.height); val sq = android.graphics.Bitmap.createBitmap(p, (p.width - s) / 2, (p.height - s) / 2, s, s)
        val r = android.graphics.RectF(w / 2f - 90f, y, w / 2f + 90f, y + 180f)
        c.drawOval(android.graphics.RectF(r.left - 6, r.top - 6, r.right + 6, r.bottom + 6), android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = t.accent })
        c.save(); c.clipPath(android.graphics.Path().apply { addOval(r, android.graphics.Path.Direction.CW) }); c.drawBitmap(sq, null, r, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)); c.restore()
        y += 210f
    }
    y = block(VCard.value("shop"), 48f, true, y, t.text, 2) + 50f
    y = block(title, if (title.length > 18) 92f else 120f, true, y, t.accent, 3) + 30f
    y = block(sub, 54f, false, y, t.text, 4) + 40f
    if (kind == "pay") {
        val upi = VCard.value("upi").ifBlank { S.upiId }
        if (upi.contains("@")) {
            val uri = "upi://pay?pa=" + android.net.Uri.encode(upi) + "&pn=" + android.net.Uri.encode(VCard.value("shop").ifBlank { "Shop" }) + "&cu=INR"
            Qr.bitmap(uri, 520)?.let { q ->
                val r = android.graphics.RectF(w / 2f - 260f, y, w / 2f + 260f, y + 520f)
                c.drawRoundRect(android.graphics.RectF(r.left - 20, r.top - 20, r.right + 20, r.bottom + 20), 30f, 30f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
                c.drawBitmap(q, null, r, null); y += 560f
            }
            y = block("UPI: $upi", 40f, false, y, t.text, 1) + 20f
        } else y = block("UPI ID Settings / Visiting card me daalein", 40f, false, y, t.text, 2)
    }
    block(details, 46f, false, y, t.text, 5)
    val phone = VCard.value("phone"); val addr = VCard.value("address")
    val foot = listOf(if (phone.isNotBlank()) "📞 $phone" else "", addr).filter { it.isNotBlank() }.joinToString("\n")
    if (foot.isNotBlank()) {
        c.drawRect(0f, h - 190f, w.toFloat(), h.toFloat(), android.graphics.Paint().apply { color = 0x55000000 })
        block(foot, 38f, false, h - 165f, 0xFFFFFFFF.toInt(), 3)
    }
    return bmp
}

@Composable
private fun PosterScreen(nav: Nav) {
    val ctx = LocalContext.current
    var kind by remember { mutableStateOf(POSTER_KINDS[0]) }
    var title by remember { mutableStateOf(tr(kind.title)) }
    var sub by remember { mutableStateOf(tr(kind.sub)) }
    var details by remember { mutableStateOf(tr(kind.details)) }
    var style by remember { mutableStateOf(Repo.setting("poster_style", "6").toIntOrNull() ?: 6) }
    val bmp = remember(kind, title, sub, details, style) { renderPoster(ctx, kind.key, title, sub, details, style) }
    ProPage("Offer poster", nav) {
        Card {
            Image(bmp.asImageBitmap(), "Poster", Modifier.fillMaxWidth().aspectRatio(1080f / 1350f).clip(RoundedCornerShape(10.dp)))
            Button(onClick = { shareFile(ctx, VisitingCard.saveTemp(ctx, bmp, "poster.png"), "image/png", "Poster") }, Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text("WhatsApp status / share")
            }
        }
        Card {
            Text("Poster ka type", fontSize = 13.sp, color = ios.secondaryLabel)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                POSTER_KINDS.forEach { k -> IosPill(k.name, k == kind, { kind = k; title = tr(k.title); sub = tr(k.sub); details = tr(k.details) }) }
            }
            Text("Rang", fontSize = 13.sp, color = ios.secondaryLabel)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VCard.TEMPLATES.forEachIndexed { i, t ->
                    Box(Modifier.size(44.dp).clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(t.c1), Color(t.c2))))
                        .border(if (style == i) 3.dp else 1.dp, if (style == i) ios.tint else ios.separator, CircleShape)
                        .clickable { style = i; Repo.setSetting("poster_style", i.toString()) })
                }
            }
            IosField(title, { title = it.take(60) }, "", placeholder = "Bada heading")
            IosField(sub, { sub = it.take(120) }, "", placeholder = "Chhoti line")
            IosField(details, { details = it.take(200) }, "", placeholder = "Aur details (nayi line ke liye Enter)")
            Hint("Dukan ka naam, photo/logo, phone aur pata Visiting card se aate hain.")
        }
    }
}

private val READY_MSGS = listOf(
    "Diwali" to "🪔 Shubh Deepawali! {dukan} ki taraf se aapko aur aapke parivar ko dher saari shubhkamnayein. Tyohar par khaas chhoot — zaroor aayein 🙏",
    "Holi" to "🎨 Holi ki hardik shubhkamnayein! {dukan} par rang, gulal aur mithai sab uplabdh. Happy Holi 🙏",
    "Eid" to "🌙 Eid Mubarak! {dukan} ki taraf se aapko dher saari khushiyan 🙏",
    "Naya saal" to "🎉 Naya saal mubarak ho! {dukan} ki taraf se aapka saal khushiyon bhara ho 🙏",
    "Chhath / Puja" to "🙏 Puja ki hardik shubhkamnayein! Puja ka saara saman {dukan} par uplabdh hai.",
    "Offer" to "🔥 Khaas offer! {dukan} par is hafte har saman par chhoot. Jaldi aayein, stock seemit hai.",
    "Naya maal" to "🆕 Naya maal aa gaya hai! {dukan} par aakar dekhein — taaza stock, sahi daam.",
    "Dukan band" to "🙏 Soochna: {dukan} kal band rahegi. Parso se dukan roz ki tarah khulegi.",
    "Dhanyavaad" to "🙏 {dukan} se kharidari ke liye dhanyavaad! Aapka bharosa hi hamari taakat hai. Phir aaiyega.",
    "Payment request" to "Namaste 🙏 {dukan} ka baaki hisaab jab sahi lage tab chuka dein. UPI se bhi bhej sakte hain. Dhanyavaad.",
    "Home delivery" to "🛵 Ab {dukan} se ghar baithe saman mangayein! Call ya WhatsApp karein, hum ghar tak pahunchayenge.",
    "Janamdin" to "🎂 Janamdin ki dher saari badhai! {dukan} ki taraf se aapko khaas shubhkamnayein 🙏",
)

@Composable
private fun ReadyMsgScreen(nav: Nav) {
    val ctx = LocalContext.current
    val shop = remember { VCard.value("shop").ifBlank { Repo.businessName() } }
    var editing by remember { mutableStateOf<String?>(null) }
    ProPage("Tyohar / offer message", nav) {
        Hint("Message chuno, badlo aur WhatsApp ya SMS se bhejo. {dukan} ki jagah aapki dukan ka naam aata hai.")
        READY_MSGS.forEach { (name, body) ->
            val text = tr(body).replace("{dukan}", shop)
            Card(Modifier.clickable { editing = text }) {
                Text(name, fontWeight = FontWeight.SemiBold, color = ios.label)
                androidx.compose.material3.Text(text, color = ios.secondaryLabel, fontSize = 14.sp)
            }
        }
    }
    editing?.let { start ->
        var t by remember(start) { mutableStateOf(start) }
        IosAlert(onDismissRequest = { editing = null }, title = { Text("Message bhejein") },
            text = { IosField(t, { t = it.take(600) }, "", placeholder = "Message") },
            confirmButton = { TextButton(onClick = { shareText(ctx, t); editing = null }) { Text("Bhejein") } },
            dismissButton = { TextButton(onClick = {
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("msg", t)); toast(ctx, "Copy ho gaya ✅"); editing = null
            }) { Text("Copy") } })
    }
}

@Composable
private fun DiaryScreen(nav: Nav) {
    val tick = Repo.tick
    val notes = remember(tick) {
        try { val a = JSONArray(Repo.setting("diary_notes", "[]")); (0 until a.length()).map { a.getJSONObject(it) } } catch (_: Throwable) { emptyList() }
    }
    fun save(list: List<JSONObject>) = Repo.setSetting("diary_notes", JSONArray(list).toString())
    var txt by remember { mutableStateOf("") }
    ProPage("Dukan diary", nav) {
        Card {
            IosField(txt, { txt = it.take(300) }, "", placeholder = "Yaad rakhne wali baat (jaise: Ramesh ko maal bhejna)")
            Button(onClick = {
                if (txt.isNotBlank()) { save(listOf(JSONObject().put("t", txt.trim()).put("d", System.currentTimeMillis()).put("done", false)) + notes); txt = "" }
            }, Modifier.fillMaxWidth()) { Text("Jodein") }
        }
        if (notes.isEmpty()) Hint("Abhi koi note nahi. Order, maal mangwana, kisi ko call karna — sab yahan likh lo.")
        notes.forEachIndexed { i, n ->
            val done = n.optBoolean("done")
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = done, onCheckedChange = { v -> save(notes.mapIndexed { j, o -> if (j == i) JSONObject(o.toString()).put("done", v) else o }) })
                    Column(Modifier.weight(1f)) {
                        androidx.compose.material3.Text(n.optString("t"), color = if (done) ios.secondaryLabel else ios.label,
                            textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                        Text(fmtDate(n.optLong("d")), fontSize = 12.sp, color = ios.secondaryLabel)
                    }
                    IconButton(onClick = { save(notes.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Filled.Delete, "Hatao", tint = IosRed) }
                }
            }
        }
    }
}
