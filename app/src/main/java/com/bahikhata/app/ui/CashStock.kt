@file:OptIn(ExperimentalMaterial3Api::class)

package com.bahikhata.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bahikhata.app.data.CashEntry
import com.bahikhata.app.data.Repo
import com.bahikhata.app.data.StockItem
import com.bahikhata.app.util.addDays
import com.bahikhata.app.util.fmtDate
import com.bahikhata.app.util.money
import com.bahikhata.app.util.monthLabel
import com.bahikhata.app.util.rupees
import com.bahikhata.app.util.startOfMonth
import java.util.Calendar

val CASH_IN_CATS = listOf("Sale", "Payment mila", "Udhaar wapas", "Other")
val CASH_OUT_CATS = listOf("Purchase", "Rent", "Salary", "Bijli", "Transport", "Chai-Nashta", "Ghar kharch", "Other")

fun addMonths(millis: Long, n: Int): Long = Calendar.getInstance().apply { timeInMillis = millis; add(Calendar.MONTH, n) }.timeInMillis

@Composable
fun CashbookScreen() {
    val tick = Repo.tick
    val all = remember(tick) { Repo.cashList() }
    var month by remember { mutableStateOf(startOfMonth(System.currentTimeMillis())) }
    var add by remember { mutableStateOf<Boolean?>(null) }
    var del by remember { mutableStateOf<CashEntry?>(null) }
    val end = addMonths(month, 1)
    val inMonth = all.filter { it.date >= month && it.date < end }
    val mIn = inMonth.filter { it.cashIn }.sumOf { it.amount }
    val mOut = inMonth.filter { !it.cashIn }.sumOf { it.amount }
    val inHand = all.sumOf { if (it.cashIn) it.amount else -it.amount }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)) {
            item {
                SectionCard {
                    StatBox("Kul balance (cash in hand)", rupees(inHand), if (inHand >= 0) GotGreen else GaveRed)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { month = addMonths(month, -1) }) { Icon(Icons.Filled.ChevronLeft, "Pichhla", tint = ios.tint) }
                        Text(monthLabel(month), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        IconButton(onClick = { month = addMonths(month, 1) }) { Icon(Icons.Filled.ChevronRight, "Agla", tint = ios.tint) }
                    }
                    Row {
                        StatBox("Aaya (In)", rupees(mIn), GotGreen, Modifier.weight(1f))
                        StatBox("Gaya (Out)", rupees(mOut), GaveRed, Modifier.weight(1f))
                        StatBox("Bacha", rupees(mIn - mOut), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (inMonth.isEmpty()) item { EmptyState(Icons.Filled.AccountBalanceWallet, "Is mahine koi entry nahi") }
            val byDay = inMonth.groupBy { fmtDate(it.date) }
            byDay.forEach { (day, list) ->
                item(key = "h$day") {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp)) {
                        Text(day.uppercase(), Modifier.weight(1f), fontSize = 13.sp, color = ios.secondaryLabel)
                        val net = list.sumOf { if (it.cashIn) it.amount else -it.amount }
                        Text((if (net >= 0) "+" else "") + money(net), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = if (net >= 0) GotGreen else GaveRed)
                    }
                }
                itemsIndexed(list, key = { _, e -> e.id }) { i, e ->
                    GroupCell(i, list.size, onClick = { del = e }, separatorInset = 56.dp) {
                        IconTile(if (e.cashIn) Icons.Filled.SouthWest else Icons.Filled.NorthEast, if (e.cashIn) GotGreen else GaveRed)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.category, fontSize = 17.sp, color = ios.label)
                            Text(listOf(e.mode, e.note).filter { it.isNotBlank() }.joinToString(" · "),
                                fontSize = 13.sp, color = ios.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text((if (e.cashIn) "+" else "-") + rupees(e.amount), fontWeight = FontWeight.SemiBold,
                            color = if (e.cashIn) GotGreen else GaveRed)
                    }
                }
            }
        }
        Column(Modifier.background(ios.barBg)) {
            Hairline()
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("CASH IN (Aaya)", GotGreen, Modifier.weight(1f)) { add = true }
                BigButton("CASH OUT (Gaya)", GaveRed, Modifier.weight(1f)) { add = false }
            }
        }
    }

    add?.let { isIn -> CashDialog(isIn, onDismiss = { add = null }) }
    del?.let { e ->
        ConfirmDialog("Entry delete karein?", "${e.category} • ${rupees(e.amount)} • ${fmtDate(e.date)}", "Delete",
            onConfirm = { Repo.deleteCash(e.id) }, onDismiss = { del = null })
    }
}

@Composable
fun CashDialog(cashIn: Boolean, onDismiss: () -> Unit) {
    var isIn by remember { mutableStateOf(cashIn) }
    var amount by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf(if (cashIn) "Sale" else "Purchase") }
    var custom by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("CASH") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var err by remember { mutableStateOf(false) }
    IosSheet(
        onDismissRequest = onDismiss,
        title = { Text(if (isIn) "Cash In (Aaya)" else "Cash Out (Gaya)", color = if (isIn) GotGreen else GaveRed) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipsRow(listOf("In", "Out"), if (isIn) "In" else "Out", {
                    isIn = it == "In"; cat = if (isIn) "Sale" else "Purchase"
                })
                NumField(amount, { amount = it; err = false }, "Rakam *", Modifier.fillMaxWidth(), prefix = "₹")
                if (err) Text("Rakam daalein", color = MaterialTheme.colorScheme.error)
                Text("Category", fontSize = 13.sp, color = ios.secondaryLabel)
                ChipsRow(if (isIn) CASH_IN_CATS else CASH_OUT_CATS, cat, { cat = it })
                if (cat == "Other") TxtField(custom, { custom = it }, "Category ka naam", Modifier.fillMaxWidth())
                Text("Mode", fontSize = 13.sp, color = ios.secondaryLabel)
                ChipsRow(PAY_MODES, mode, { mode = it })
                TxtField(note, { note = it }, "Note", Modifier.fillMaxWidth())
                DateField("Tareekh", date, { it?.let { d -> date = d } })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amount.toAmount()
                if (a <= 0) { err = true; return@TextButton }
                val c = if (cat == "Other" && custom.isNotBlank()) custom.trim() else cat
                Repo.addCash(isIn, a, c, note, mode, date)
                onDismiss()
            }) { Text("Save", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontSize = 17.sp) } }
    )
}

// ------------------------------------------------------------------- stock

@Composable
fun StockScreen() {
    val tick = Repo.tick
    val items = remember(tick) { Repo.items() }
    var query by remember { mutableStateOf("") }
    var edit by remember { mutableStateOf<StockItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var adjust by remember { mutableStateOf<StockItem?>(null) }
    var onlyLow by remember { mutableStateOf(false) }
    val low = items.filter { it.qty <= it.lowStock }
    val value = items.sumOf { it.qty * it.purchasePrice }
    val sellValue = items.sumOf { it.qty * it.sellPrice }
    val shown = items.filter { (query.isBlank() || it.name.contains(query, true)) && (!onlyLow || it.qty <= it.lowStock) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)) {
            item {
                SectionCard {
                    Row {
                        StatBox("Items", items.size.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(0.7f))
                        StatBox("Stock value", rupees(value), MaterialTheme.colorScheme.primary, Modifier.weight(1.2f))
                        StatBox("Kam stock", low.size.toString(), if (low.isEmpty()) GotGreen else GaveRed, Modifier.weight(0.8f))
                    }
                    Text("Bechne par: ${rupees(sellValue)}  ·  Anumanit munafa: ${rupees(sellValue - value)}",
                        fontSize = 13.sp, color = ios.secondaryLabel)
                }
                Spacer(Modifier.height(12.dp))
                IosSearchField(query, { query = it }, "Item dhoondhein")
                Spacer(Modifier.height(8.dp))
                ChipsRow(listOf("Sab items", "Sirf kam stock"), if (onlyLow) "Sirf kam stock" else "Sab items", { onlyLow = it != "Sab items" })
                Spacer(Modifier.height(8.dp))
            }
            if (shown.isEmpty()) item { EmptyState(Icons.Filled.Inventory2, if (items.isEmpty()) "Pehla item jodein — + dabayein" else "Koi item nahi mila") }
            itemsIndexed(shown, key = { _, x -> x.id }) { i, it0 ->
                val isLow = it0.qty <= it0.lowStock
                GroupCell(i, shown.size, onClick = { edit = it0 }, padding = PaddingValues(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(it0.name, fontSize = 17.sp, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Bikri ${rupees(it0.sellPrice)} · Kharid ${rupees(it0.purchasePrice)}", fontSize = 13.sp, color = ios.secondaryLabel)
                        if (isLow) Text("Stock kam hai", fontSize = 12.sp, color = GaveRed, fontWeight = FontWeight.Medium)
                    }
                    // iOS stepper
                    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(ios.fill), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp, 32.dp).clickable { Repo.adjustQty(it0.id, -1.0) }, contentAlignment = Alignment.Center) {
                            Text("−", fontSize = 20.sp, color = ios.label)
                        }
                        Box(Modifier.width(0.5.dp).height(18.dp).background(ios.separator))
                        Box(Modifier.size(38.dp, 32.dp).clickable { Repo.adjustQty(it0.id, 1.0) }, contentAlignment = Alignment.Center) {
                            Text("+", fontSize = 20.sp, color = ios.label)
                        }
                    }
                    TextButton(onClick = { adjust = it0 }) {
                        Text("${money(it0.qty)} ${it0.unit}", fontWeight = FontWeight.SemiBold, color = if (isLow) GaveRed else ios.tint)
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Item jodein") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
    if (adding) ItemDialog(null) { adding = false }
    edit?.let { ItemDialog(it) { edit = null } }
    adjust?.let { item ->
        var q by remember { mutableStateOf("") }
        IosAlert(
            onDismissRequest = { adjust = null },
            title = { Text("${item.name}: stock badlein") },
            text = {
                Column {
                    Text("Abhi: ${money(item.qty)} ${item.unit}")
                    Spacer(Modifier.height(8.dp))
                    NumField(q, { q = it }, "Kitna (${item.unit})", Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = { Repo.adjustQty(item.id, q.toAmount()); adjust = null }) { Text("+ Stock In", color = GotGreen) }
            },
            dismissButton = {
                TextButton(onClick = { Repo.adjustQty(item.id, -q.toAmount()); adjust = null }) { Text("- Stock Out", color = GaveRed) }
            }
        )
    }
}

@Composable
fun ItemDialog(existing: StockItem?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var unit by remember { mutableStateOf(existing?.unit ?: "pcs") }
    var qty by remember { mutableStateOf(existing?.qty?.plain() ?: "") }
    var buy by remember { mutableStateOf(existing?.purchasePrice?.plain() ?: "") }
    var sell by remember { mutableStateOf(existing?.sellPrice?.plain() ?: "") }
    var low by remember { mutableStateOf(existing?.lowStock?.plain() ?: "5") }
    var err by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    IosSheet(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Naya item" else "Item edit") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TxtField(name, { name = it; err = false }, "Item ka naam *", Modifier.fillMaxWidth())
                if (err) Text("Naam zaroori hai", color = MaterialTheme.colorScheme.error)
                ChipsRow(listOf("pcs", "kg", "g", "litre", "ml", "box", "dozen", "packet", "metre", "bag"), unit, { unit = it })
                NumField(qty, { qty = it }, "Kitna stock hai", Modifier.fillMaxWidth(), suffix = unit)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField(buy, { buy = it }, "Kharid rate", Modifier.weight(1f), prefix = "₹")
                    NumField(sell, { sell = it }, "Bikri rate", Modifier.weight(1f), prefix = "₹")
                }
                NumField(low, { low = it }, "Kam stock alert (is se kam ho to)", Modifier.fillMaxWidth(), suffix = unit)
                if (existing != null) TextButton(onClick = { confirmDel = true }) { Text("Item delete karein", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) { err = true; return@TextButton }
                if (existing == null) Repo.addItem(name, unit, qty.toAmount(), buy.toAmount(), sell.toAmount(), low.toAmount())
                else Repo.updateItem(existing.id, name, unit, qty.toAmount(), buy.toAmount(), sell.toAmount(), low.toAmount())
                onDismiss()
            }) { Text("Save", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontSize = 17.sp) } }
    )
    if (confirmDel && existing != null) ConfirmDialog("Item delete karein?", existing.name, "Delete",
        onConfirm = { Repo.deleteItem(existing.id); onDismiss() }, onDismiss = { confirmDel = false })
}
