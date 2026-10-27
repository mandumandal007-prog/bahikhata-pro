@file:OptIn(ExperimentalMaterial3Api::class)

package com.bahikhata.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bahikhata.app.data.Party
import com.bahikhata.app.data.Repo
import com.bahikhata.app.util.NumFmt
import com.bahikhata.app.util.S
import com.bahikhata.app.util.UiPrefs
import com.bahikhata.app.util.addDays
import com.bahikhata.app.util.compactRupees
import com.bahikhata.app.util.rupees
import com.bahikhata.app.util.startOfDay
import com.bahikhata.app.util.startOfMonth
import com.bahikhata.app.util.whatsapp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Amount text that respects "Hide amounts". */
fun amt(v: Double): String = if (UiPrefs.hideAmounts) NumFmt.symbol + " ••••" else rupees(v)
fun amtShort(v: Double): String = if (UiPrefs.hideAmounts) "••" else compactRupees(v)

private const val DAY = 86_400_000L

fun daysAgo(millis: Long): Int = ((startOfDay(System.currentTimeMillis()) - startOfDay(millis)) / DAY).toInt().coerceAtLeast(0)

/** Overdue party with how many days it is late. */
data class OverdueParty(val party: Party, val daysLate: Int)

fun overdueList(parties: List<Party>): List<OverdueParty> {
    val since = Repo.overdueSince()
    return parties.filter { it.balance > 0.005 && since.containsKey(it.id) }
        .map { OverdueParty(it, daysAgo(since[it.id]!!)) }
        .sortedWith(compareByDescending<OverdueParty> { it.daysLate }.thenByDescending { it.party.balance })
}

/** Receivable ageing: how long since the customer last paid (or since the first entry if never). */
data class AgeBucket(val label: String, val color: Color, var amount: Double = 0.0, var count: Int = 0)

fun ageing(parties: List<Party>): List<AgeBucket> {
    val lastPay = Repo.lastPaymentDates()
    val first = Repo.firstEntryDates()
    val b = listOf(
        AgeBucket("0–30 din", Color(0xFF34C759)),
        AgeBucket("31–60 din", Color(0xFFFFCC00)),
        AgeBucket("61–90 din", Color(0xFFFF9500)),
        AgeBucket("90+ din", Color(0xFFFF3B30)),
    )
    parties.filter { it.balance > 0.005 }.forEach { p ->
        val ref = lastPay[p.id] ?: first[p.id] ?: p.createdAt
        val d = daysAgo(ref)
        val i = when { d <= 30 -> 0; d <= 60 -> 1; d <= 90 -> 2; else -> 3 }
        b[i].amount += p.balance; b[i].count++
    }
    return b
}

// ---------------------------------------------------------------- home: aaj ka hisaab

@Composable
fun TodayCard(onOpenReports: () -> Unit) {
    val tick = Repo.tick
    val now = System.currentTimeMillis()
    val s = startOfDay(now); val e = addDays(s, 1)
    val ys = addDays(s, -1)
    val data = remember(tick, s) {
        val en = Repo.entriesBetween(s, e)
        val cash = Repo.cashList().filter { it.date >= s && it.date < e }
        val bills = Repo.bills().filter { it.date >= s && it.date < e }
        val yGot = Repo.entriesBetween(ys, s).filter { !it.gave }.sumOf { it.amount }
        doubleArrayOf(
            en.filter { it.gave }.sumOf { it.amount }, en.filter { !it.gave }.sumOf { it.amount },
            cash.filter { it.cashIn }.sumOf { it.amount }, cash.filter { !it.cashIn }.sumOf { it.amount },
            bills.sumOf { it.total }, (en.size + cash.size + bills.size).toDouble(), yGot
        )
    }
    val dateTxt = remember(s) { SimpleDateFormat("EEEE, d MMM", Locale.ENGLISH).format(Date(now)) }
    val tint = ios.tint
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.16f), tint.copy(alpha = 0.04f))))
            .border(0.5.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .clickable { onOpenReports() }.padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Aaj ka hisaab", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ios.label)
                Text(dateTxt + " · " + (if (data[5] == 0.0) "abhi koi entry nahi" else "${data[5].toInt()} entries"), fontSize = 12.sp, color = ios.secondaryLabel)
            }
            Icon(Icons.Outlined.Insights, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("Diye", amtShort(data[0]), GaveRed, Modifier.weight(1f))
            MiniStat("Mile", amtShort(data[1]), GotGreen, Modifier.weight(1f))
            MiniStat("Cash bacha", (if (data[2] - data[3] < 0) "-" else "") + amtShort(data[2] - data[3]), tint, Modifier.weight(1f))
            MiniStat("Bikri", amtShort(data[4]), Color(0xFF5856D6), Modifier.weight(1f))
        }
        if (data[1] > 0 || data[6] > 0) {
            Spacer(Modifier.height(8.dp))
            val diff = data[1] - data[6]
            val up = diff >= 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (up) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown, null,
                    tint = if (up) GotGreen else GaveRed, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Vasooli kal se " + (if (up) "${amt(diff)} zyada" else "${amt(-diff)} kam") + " (kal ${amt(data[6])})",
                    fontSize = 12.sp, color = ios.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(ios.cell).padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(label, fontSize = 11.sp, color = ios.secondaryLabel, maxLines = 1)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- home: due nikal gayi

@Composable
fun OverdueStrip(parties: List<Party>, onParty: (Long) -> Unit) {
    val tick = Repo.tick
    val list = remember(tick, parties) { overdueList(parties) }
    if (list.isEmpty()) return
    val total = list.sumOf { it.party.balance }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = IosOrange, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("${list.size} logon ki due nikal gayi · ${amt(total)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = IosOrange)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            list.take(12).forEach { o ->
                Row(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(ios.cell)
                        .border(0.5.dp, IosOrange.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .clickable { onParty(o.party.id) }.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InitialsAvatar(o.party.name, 30.dp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(o.party.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ios.label, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
                        Text("${amtShort(o.party.balance)} · ${lateText(o.daysLate)}", fontSize = 11.sp, color = GaveRed, maxLines = 1)
                    }
                }
            }
        }
    }
}

fun lateText(d: Int): String = when (d) { 0 -> "aaj due"; 1 -> "1 din late"; else -> "$d din late" }

// ---------------------------------------------------------------- reports: trend chart

data class TrendData(val labels: List<String>, val a: List<Double>, val b: List<Double>, val starts: List<Long>)

/** buckets: "7 din" (days), "8 hafte" (weeks), "6 mahine" (months). source: Khata (Diye/Mile) or Cashbook (Aaya/Gaya). */
fun trendData(span: String, khata: Boolean): TrendData {
    val now = System.currentTimeMillis()
    val starts: List<Long>; val ends: List<Long>; val labels: List<String>
    when (span) {
        "8 hafte" -> {
            val c = Calendar.getInstance().apply { timeInMillis = startOfDay(now); firstDayOfWeek = Calendar.MONDAY; set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) }
            val thisWeek = c.timeInMillis.let { if (it > now) addDays(it, -7) else it }
            starts = (7 downTo 0).map { addDays(thisWeek, -7 * it) }
            ends = starts.map { addDays(it, 7) }
            labels = starts.map { SimpleDateFormat("d/M", Locale.ENGLISH).format(Date(it)) }
        }
        "6 mahine" -> {
            val m0 = startOfMonth(now)
            starts = (5 downTo 0).map { addMonths(m0, -it) }
            ends = starts.map { addMonths(it, 1) }
            labels = starts.map { SimpleDateFormat("MMM", Locale.ENGLISH).format(Date(it)) }
        }
        else -> {
            starts = (6 downTo 0).map { addDays(startOfDay(now), -it) }
            ends = starts.map { addDays(it, 1) }
            labels = starts.map { SimpleDateFormat("EE", Locale.ENGLISH).format(Date(it)) }
        }
    }
    val from = starts.first(); val to = ends.last()
    val a = DoubleArray(starts.size); val b = DoubleArray(starts.size)
    fun idx(t: Long): Int { for (i in starts.indices) if (t >= starts[i] && t < ends[i]) return i; return -1 }
    if (khata) Repo.entriesBetween(from, to).forEach { r -> val i = idx(r.date); if (i >= 0) { if (r.gave) b[i] += r.amount else a[i] += r.amount } }
    else Repo.cashList().forEach { r -> if (r.date in from until to) { val i = idx(r.date); if (i >= 0) { if (r.cashIn) a[i] += r.amount else b[i] += r.amount } } }
    return TrendData(labels, a.toList(), b.toList(), starts)
}

/** Grouped bar chart; tap a bar group to see its numbers. a = green, b = red. */
@Composable
fun TrendChart(d: TrendData, aName: String, bName: String) {
    var sel by remember(d) { mutableIntStateOf(d.labels.lastIndex) }
    val max = (d.a + d.b).maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val grid = ios.separator
    val selBg = ios.fill
    val n = d.labels.size
    if (n == 0 || d.a.size < n || d.b.size < n) return
    Column {
        val si = sel.coerceIn(0, n - 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(d.labels[si], fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ios.label, modifier = Modifier.weight(1f))
            Text("$aName ${amt(d.a[si])}", fontSize = 13.sp, color = GotGreen, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            Text("$bName ${amt(d.b[si])}", fontSize = 13.sp, color = GaveRed, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.fillMaxWidth().height(150.dp).pointerInput(d) {
                detectTapGestures { o -> sel = (o.x / (size.width.toFloat() / n)).toInt().coerceIn(0, n - 1) }
            }
        ) {
            val slot = size.width / n
            for (k in 1..3) {
                val y = size.height * k / 4f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
            }
            drawRoundRect(selBg, Offset(si * slot + 2f, 0f), Size(slot - 4f, size.height), CornerRadius(12f, 12f))
            val bw = slot * 0.28f
            for (i in 0 until n) {
                val x = i * slot + slot / 2f - bw - 2f
                val ha = (d.a[i] / max * size.height * 0.92).toFloat().coerceAtLeast(if (d.a[i] > 0) 4f else 0f)
                val hb = (d.b[i] / max * size.height * 0.92).toFloat().coerceAtLeast(if (d.b[i] > 0) 4f else 0f)
                val alpha = if (i == si) 1f else 0.55f
                drawRoundRect(GotGreen.copy(alpha = alpha), Offset(x, size.height - ha), Size(bw, ha), CornerRadius(8f, 8f))
                drawRoundRect(GaveRed.copy(alpha = alpha), Offset(x + bw + 4f, size.height - hb), Size(bw, hb), CornerRadius(8f, 8f))
            }
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 2f)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            d.labels.forEachIndexed { i, l ->
                Text(l, Modifier.weight(1f), fontSize = 11.sp, color = if (i == si) ios.label else ios.secondaryLabel,
                    fontWeight = if (i == si) FontWeight.SemiBold else FontWeight.Normal, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("● $aName ${amt(d.a.sum())}", color = GotGreen, fontSize = 12.sp)
            Text("● $bName ${amt(d.b.sum())}", color = GaveRed, fontSize = 12.sp)
        }
    }
}

/** A horizontal stacked bar with a legend (payment-mode split, ageing). */
@Composable
fun StackedBar(parts: List<Triple<String, Double, Color>>) {
    val total = parts.sumOf { it.second }.takeIf { it > 0 } ?: return
    Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(ios.fill)) {
        parts.filter { it.second > 0 }.forEach { (_, v, c) -> Box(Modifier.weight((v / total).toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(c)) }
    }
    Spacer(Modifier.height(8.dp))
    parts.filter { it.second > 0 }.forEach { (l, v, c) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(c))
            Spacer(Modifier.width(8.dp))
            Text(l, fontSize = 14.sp, color = ios.label, modifier = Modifier.weight(1f))
            Text("${(v / total * 100).roundToInt()}%", fontSize = 13.sp, color = ios.secondaryLabel)
            Spacer(Modifier.width(10.dp))
            Text(amt(v), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ios.label)
        }
    }
}

val MODE_COLORS = mapOf(
    "CASH" to Color(0xFF34C759), "UPI" to Color(0xFF5856D6), "BANK" to Color(0xFF007AFF),
    "CARD" to Color(0xFFFF9500), "CHEQUE" to Color(0xFFAF52DE)
)

/** Overdue row with one-tap WhatsApp reminder. */
@Composable
fun OverdueRow(o: OverdueParty, index: Int, count: Int, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    GroupCell(index, count, onClick = onOpen, separatorInset = 56.dp, padding = PaddingValues(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
        InitialsAvatar(o.party.name, 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(o.party.name, fontSize = 16.sp, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(lateText(o.daysLate), fontSize = 12.sp, color = if (o.daysLate > 30) GaveRed else IosOrange, fontWeight = FontWeight.SemiBold)
        }
        Text(amt(o.party.balance), fontSize = 15.sp, color = GaveRed, fontWeight = FontWeight.SemiBold)
        if (o.party.phone.isNotBlank()) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(GotGreen.copy(alpha = 0.15f)).clickable {
                    whatsapp(ctx, o.party.phone, S.reminderText(o.party.name, rupees(o.party.balance), Repo.businessName(), true))
                },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Chat, "Reminder", tint = GotGreen, modifier = Modifier.size(18.dp)) }
        }
    }
}

/** Roznamcha (day book) row. */
data class DayRow(val time: Long, val title: String, val sub: String, val amount: Double, val inflow: Boolean?)

fun dayBook(day: Long): List<DayRow> {
    val e = addDays(day, 1)
    val rows = ArrayList<DayRow>()
    Repo.entriesBetween(day, e).forEach { r ->
        rows += DayRow(r.date, r.partyName, (if (r.gave) "Khata · Diye" else "Khata · Mile") + " · " + r.mode + (if (r.note.isNotBlank()) " · " + r.note else ""), r.amount, !r.gave)
    }
    Repo.cashList().filter { it.date >= day && it.date < e }.forEach { c ->
        rows += DayRow(c.date, c.category.ifBlank { "Cash" }, (if (c.cashIn) "Cashbook · Aaya" else "Cashbook · Gaya") + " · " + c.mode + (if (c.note.isNotBlank()) " · " + c.note else ""), c.amount, c.cashIn)
    }
    Repo.bills().filter { it.date >= day && it.date < e }.forEach { b ->
        rows += DayRow(b.date, "Bill ${b.number} · ${b.partyName}", if (b.paid) "Bill · Paid" else "Bill · Baaki", b.total, null)
    }
    return rows.sortedByDescending { it.time }
}
