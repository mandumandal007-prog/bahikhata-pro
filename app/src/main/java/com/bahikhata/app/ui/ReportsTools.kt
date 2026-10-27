@file:OptIn(ExperimentalMaterial3Api::class)

package com.bahikhata.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bahikhata.app.data.Repo
import com.bahikhata.app.util.addDays
import com.bahikhata.app.util.dayLabel
import com.bahikhata.app.util.fmtDate
import com.bahikhata.app.util.money
import com.bahikhata.app.util.rupees
import com.bahikhata.app.util.shareText
import com.bahikhata.app.util.startOfDay
import com.bahikhata.app.util.startOfMonth
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.pow

@Composable
fun ReportsScreen() {
    val ctx = LocalContext.current
    val tick = Repo.tick
    var period by remember { mutableStateOf("Is mahine") }
    val now = System.currentTimeMillis()
    val (start, end) = when (period) {
        "Aaj" -> startOfDay(now) to addDays(startOfDay(now), 1)
        "7 din" -> addDays(startOfDay(now), -6) to addDays(startOfDay(now), 1)
        "Is mahine" -> startOfMonth(now) to addMonths(startOfMonth(now), 1)
        "Pichhla mahina" -> addMonths(startOfMonth(now), -1) to startOfMonth(now)
        else -> 0L to Long.MAX_VALUE
    }
    val entries = remember(tick, start, end) { Repo.entriesBetween(start, end) }
    val cash = remember(tick, start, end) { Repo.cashList().filter { it.date >= start && it.date < end } }
    val bills = remember(tick, start, end) { Repo.invoices().filter { it.date >= start && it.date < end } }
    val parties = remember(tick) { Repo.parties() }
    val items = remember(tick) { Repo.items() }

    val gave = entries.filter { it.gave }.sumOf { it.amount }
    val got = entries.filter { !it.gave }.sumOf { it.amount }
    val cIn = cash.filter { it.cashIn }.sumOf { it.amount }
    val cOut = cash.filter { !it.cashIn }.sumOf { it.amount }
    val billTotal = bills.sumOf { it.total }
    val billGst = bills.sumOf { it.gstAmount }
    val receivable = parties.filter { it.balance > 0 }.sumOf { it.balance }
    val payable = parties.filter { it.balance < 0 }.sumOf { -it.balance }
    val expenseByCat = cash.filter { !it.cashIn }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
        .entries.sortedByDescending { it.value }
    val topDebtors = parties.filter { it.balance > 0 }.sortedByDescending { it.balance }.take(5)
    val lowStock = items.filter { it.qty <= it.lowStock }

    // chart: last 7 days cash in/out
    val days = (6 downTo 0).map { addDays(startOfDay(now), -it) }
    val allCash = remember(tick) { Repo.cashList() }
    val dayIn = days.map { d -> allCash.filter { it.cashIn && it.date >= d && it.date < addDays(d, 1) }.sumOf { it.amount } }
    val dayOut = days.map { d -> allCash.filter { !it.cashIn && it.date >= d && it.date < addDays(d, 1) }.sumOf { it.amount } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ChipsRow(listOf("Aaj", "7 din", "Is mahine", "Pichhla mahina", "Sab"), period, { period = it })

        SectionCard("Udhaar khata ($period)") {
            Row {
                StatBox("Diye (Gave)", rupees(gave), GaveRed, Modifier.weight(1f))
                StatBox("Mile (Got)", rupees(got), GotGreen, Modifier.weight(1f))
            }
            Text("${entries.size} entries", style = MaterialTheme.typography.bodySmall)
        }
        SectionCard("Cashbook ($period)") {
            Row {
                StatBox("Aaya", rupees(cIn), GotGreen, Modifier.weight(1f))
                StatBox("Gaya", rupees(cOut), GaveRed, Modifier.weight(1f))
                StatBox("Bacha", rupees(cIn - cOut), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
        }
        SectionCard("Bills ($period)") {
            KeyValue("Kul bills", bills.size.toString())
            KeyValue("Kul bikri", rupees(billTotal), bold = true)
            KeyValue("Usme GST", rupees(billGst))
            KeyValue("Baaki (unpaid)", rupees(bills.filter { !it.paid }.sumOf { it.total }), color = GaveRed)
        }
        SectionCard("Pichhle 7 din: cash aaya / gaya") {
            BarChart(days.map { dayLabel(it) }, dayIn, dayOut)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("■ Aaya", color = GotGreen, style = MaterialTheme.typography.labelMedium)
                Text("■ Gaya", color = GaveRed, style = MaterialTheme.typography.labelMedium)
            }
        }
        SectionCard("Kul baaki (abhi tak)") {
            KeyValue("Aapko milenge", rupees(receivable), bold = true, color = GaveRed)
            KeyValue("Aapko dene hain", rupees(payable), bold = true, color = GotGreen)
            if (topDebtors.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Sabse zyada baaki", fontSize = 13.sp, color = ios.secondaryLabel)
                topDebtors.forEach { KeyValue(it.name, rupees(it.balance)) }
            }
        }
        if (expenseByCat.isNotEmpty()) SectionCard("Kharch kahan hua ($period)") {
            val max = expenseByCat.maxOf { it.value }.coerceAtLeast(1.0)
            expenseByCat.forEach { (cat, v) ->
                Text("$cat — ${rupees(v)}", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(
                    progress = { (v / max).toFloat() }, color = GaveRed, trackColor = ios.fill,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), drawStopIndicator = {}, gapSize = 0.dp
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        SectionCard("Stock") {
            KeyValue("Items", items.size.toString())
            KeyValue("Stock value (kharid)", rupees(items.sumOf { it.qty * it.purchasePrice }))
            KeyValue("Kam stock items", lowStock.size.toString(), color = if (lowStock.isEmpty()) GotGreen else GaveRed)
            lowStock.take(5).forEach { Text("• ${it.name}: ${money(it.qty)} ${it.unit}", style = MaterialTheme.typography.bodySmall) }
        }
        Button(onClick = {
            val text = buildString {
                append("*${Repo.businessName()} — Report ($period)*\n")
                append("Khata: Diye ${rupees(gave)}, Mile ${rupees(got)}\n")
                append("Cashbook: Aaya ${rupees(cIn)}, Gaya ${rupees(cOut)}, Bacha ${rupees(cIn - cOut)}\n")
                append("Bills: ${bills.size}, Bikri ${rupees(billTotal)}\n")
                append("Kul lena: ${rupees(receivable)}, Kul dena: ${rupees(payable)}\n")
                append("(${fmtDate(now)})")
            }
            shareText(ctx, text)
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Share, null); Spacer(Modifier.width(8.dp)); Text("Report share karein")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun BarChart(labels: List<String>, a: List<Double>, b: List<Double>) {
    val max = (a + b).maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val n = labels.size
        val slot = size.width / n
        val bw = slot * 0.3f
        drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), 2f)
        for (i in 0 until n) {
            val x = i * slot + slot * 0.15f
            val ha = (a[i] / max * size.height * 0.95).toFloat()
            val hb = (b[i] / max * size.height * 0.95).toFloat()
            drawRoundRect(GotGreen, Offset(x, size.height - ha), Size(bw, ha), CornerRadius(6f, 6f))
            drawRoundRect(GaveRed, Offset(x + bw + 4f, size.height - hb), Size(bw, hb), CornerRadius(6f, 6f))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        labels.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall) }
    }
}

// ------------------------------------------------------------------- tools

@Composable
fun ToolsScreen(nav: Nav) {
    val backLabel = remember { nav.backLabel() }
    val behavior = rememberLargeTitle()
    Scaffold(
        modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { IosNavBar("Calculators", behavior, backLabel = backLabel, onBack = { nav.back() }) }
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolCard("GST Calculator", Icons.Filled.Percent, true) { GstCalc() }
            ToolCard("Simple / Compound Interest (Byaj)", Icons.Filled.Savings) { InterestCalc() }
            ToolCard("EMI Calculator (Loan)", Icons.Filled.AccountBalance) { EmiCalc() }
            ToolCard("Discount Calculator", Icons.Filled.LocalOffer) { DiscountCalc() }
            ToolCard("Profit / Loss (Munafa)", Icons.Filled.TrendingUp) { ProfitCalc() }
            ToolCard("Rate per unit / Kaun sasta", Icons.Filled.Scale) { RateCalc() }
        }
    }
}

@Composable
private fun ToolCard(title: String, icon: ImageVector, startOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(startOpen) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ios.cell).animateContentSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                IconTile(icon, ios.tint)
                Spacer(Modifier.width(12.dp))
                Text(title, Modifier.weight(1f), fontSize = 17.sp, color = ios.label)
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ChevronRight, null, tint = ios.tertiaryLabel)
            }
            if (open) {
                Spacer(Modifier.height(12.dp))
                CompositionLocalProvider(LocalOnCell provides true) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
                }
            }
        }
    }
}

@Composable
private fun ResultBox(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = ios.tint.copy(alpha = 0.10f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), content = content)
    }
}

@Composable
private fun GstCalc() {
    var amt by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("18") }
    var add by remember { mutableStateOf(true) }
    NumField(amt, { amt = it }, "Rakam", Modifier.fillMaxWidth(), prefix = "₹")
    ChipsRow(listOf("3", "5", "12", "18", "28"), rate, { rate = it })
    NumField(rate, { rate = it }, "GST %", Modifier.fillMaxWidth(), suffix = "%")
    ChipsRow(listOf("GST jodo (+)", "GST nikalo (-)"), if (add) "GST jodo (+)" else "GST nikalo (-)", { add = it.startsWith("GST jodo") })
    val a = amt.toAmount(); val r = rate.toAmount()
    val base = if (add) a else a * 100 / (100 + r)
    val g = base * r / 100
    ResultBox {
        KeyValue("Bina GST (base)", rupees(base))
        KeyValue("CGST ${money(r / 2)}%", rupees(g / 2))
        KeyValue("SGST ${money(r / 2)}%", rupees(g / 2))
        KeyValue("Kul GST", rupees(g))
        KeyValue("Total", rupees(base + g), bold = true)
    }
}

@Composable
private fun InterestCalc() {
    var p by remember { mutableStateOf("") }
    var r by remember { mutableStateOf("") }
    var t by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("Saal") }
    var rateMonthly by remember { mutableStateOf(false) }
    NumField(p, { p = it }, "Mool dhan (Principal)", Modifier.fillMaxWidth(), prefix = "₹")
    NumField(r, { r = it }, if (rateMonthly) "Byaj dar (% har mahina)" else "Byaj dar (% saalana)", Modifier.fillMaxWidth(), suffix = "%")
    ChipsRow(listOf("% saalana", "% mahina (rupaya sainkda)"), if (rateMonthly) "% mahina (rupaya sainkda)" else "% saalana", { rateMonthly = it != "% saalana" })
    NumField(t, { t = it }, "Samay", Modifier.fillMaxWidth())
    ChipsRow(listOf("Saal", "Mahine", "Din"), unit, { unit = it })
    val years = when (unit) { "Mahine" -> t.toAmount() / 12; "Din" -> t.toAmount() / 365; else -> t.toAmount() }
    val annual = if (rateMonthly) r.toAmount() * 12 else r.toAmount()
    val P = p.toAmount()
    val si = P * annual * years / 100
    val ci = P * (1 + annual / 100).pow(years) - P
    ResultBox {
        KeyValue("Simple byaj", rupees(si), bold = true)
        KeyValue("Kul (SI)", rupees(P + si))
        KeyValue("Compound byaj (saalana)", rupees(ci))
        KeyValue("Kul (CI)", rupees(P + ci))
    }
}

@Composable
private fun EmiCalc() {
    var p by remember { mutableStateOf("") }
    var r by remember { mutableStateOf("") }
    var n by remember { mutableStateOf("") }
    var inYears by remember { mutableStateOf(false) }
    NumField(p, { p = it }, "Loan rakam", Modifier.fillMaxWidth(), prefix = "₹")
    NumField(r, { r = it }, "Byaj dar (% saalana)", Modifier.fillMaxWidth(), suffix = "%")
    NumField(n, { n = it }, if (inYears) "Samay (saal)" else "Samay (mahine)", Modifier.fillMaxWidth())
    ChipsRow(listOf("Mahine", "Saal"), if (inYears) "Saal" else "Mahine", { inYears = it == "Saal" })
    val P = p.toAmount(); val mr = r.toAmount() / 12 / 100
    val months = (if (inYears) n.toAmount() * 12 else n.toAmount())
    val emi = when {
        months <= 0 -> 0.0
        mr == 0.0 -> P / months
        else -> P * mr * (1 + mr).pow(months) / ((1 + mr).pow(months) - 1)
    }
    ResultBox {
        KeyValue("Mahine ki EMI", rupees(emi), bold = true)
        KeyValue("Kul byaj", rupees((emi * months - P).coerceAtLeast(0.0)))
        KeyValue("Kul bhugtan", rupees(emi * months))
    }
}

@Composable
private fun DiscountCalc() {
    var mrp by remember { mutableStateOf("") }
    var d by remember { mutableStateOf("") }
    var d2 by remember { mutableStateOf("") }
    NumField(mrp, { mrp = it }, "MRP / Daam", Modifier.fillMaxWidth(), prefix = "₹")
    NumField(d, { d = it }, "Discount %", Modifier.fillMaxWidth(), suffix = "%")
    NumField(d2, { d2 = it }, "Extra discount % (optional)", Modifier.fillMaxWidth(), suffix = "%")
    val m = mrp.toAmount()
    val after1 = m * (1 - d.toAmount() / 100)
    val fin = after1 * (1 - d2.toAmount() / 100)
    ResultBox {
        KeyValue("Bachat", rupees(m - fin))
        KeyValue("Asli discount", if (m > 0) money((m - fin) / m * 100) + "%" else "0%")
        KeyValue("Dene wali rakam", rupees(fin), bold = true)
    }
}

@Composable
private fun ProfitCalc() {
    var cost by remember { mutableStateOf("") }
    var sell by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    NumField(cost, { cost = it }, "Kharid daam (1 ka)", Modifier.fillMaxWidth(), prefix = "₹")
    NumField(sell, { sell = it }, "Bikri daam (1 ka)", Modifier.fillMaxWidth(), prefix = "₹")
    NumField(qty, { qty = it }, "Kitne pieces", Modifier.fillMaxWidth())
    val c = cost.toAmount(); val s = sell.toAmount(); val q = qty.toAmount().takeIf { it > 0 } ?: 1.0
    val pr = (s - c) * q
    ResultBox {
        KeyValue(if (pr >= 0) "Munafa" else "Nuksaan", rupees(abs(pr)), bold = true, color = if (pr >= 0) GotGreen else GaveRed)
        KeyValue("Munafa % (kharid par)", if (c > 0) money((s - c) / c * 100) + "%" else "-")
        KeyValue("Margin % (bikri par)", if (s > 0) money((s - c) / s * 100) + "%" else "-")
    }
    Text("Kitne % munafa chahiye? Bikri daam:", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(10, 20, 30, 50).forEach { m -> Text("$m% → ${rupees(c * (1 + m / 100.0))}", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun RateCalc() {
    var p1 by remember { mutableStateOf("") }
    var q1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var q2 by remember { mutableStateOf("") }
    Text("Pack A", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumField(p1, { p1 = it }, "Daam", Modifier.weight(1f), prefix = "₹")
        NumField(q1, { q1 = it }, "Matra (kg/g/pcs)", Modifier.weight(1f))
    }
    Text("Pack B (compare, optional)", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumField(p2, { p2 = it }, "Daam", Modifier.weight(1f), prefix = "₹")
        NumField(q2, { q2 = it }, "Matra", Modifier.weight(1f))
    }
    val r1 = if (q1.toAmount() > 0) p1.toAmount() / q1.toAmount() else 0.0
    val r2 = if (q2.toAmount() > 0) p2.toAmount() / q2.toAmount() else 0.0
    ResultBox {
        KeyValue("A: 1 unit ka rate", rupees(r1), bold = true)
        if (r2 > 0) {
            KeyValue("B: 1 unit ka rate", rupees(r2), bold = true)
            Text(
                when {
                    r1 <= 0 -> ""
                    r1 < r2 -> "Pack A sasta hai (${money((r2 - r1) / r2 * 100)}% kam)"
                    r2 < r1 -> "Pack B sasta hai (${money((r1 - r2) / r1 * 100)}% kam)"
                    else -> "Dono barabar"
                }, fontWeight = FontWeight.Bold
            )
        }
    }
}
