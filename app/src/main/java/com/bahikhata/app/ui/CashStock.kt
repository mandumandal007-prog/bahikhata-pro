@file:OptIn(ExperimentalMaterial3Api::class)

package com.bahikhata.app.ui

import androidx.compose.foundation.layout.*
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
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
            item {
                SectionCard {
                    Text("Kul balance (cash in hand)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(rupees(inHand), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                        color = if (inHand >= 0) GotGreen else GaveRed)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { month = addMonths(month, -1) }) { Icon(Icons.Filled.ChevronLeft, "Pichhla") }
                        Text(monthLabel(month), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { month = addMonths(month, 1) }) { Icon(Icons.Filled.ChevronRight, "Agla") }
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
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
                        Text(day, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        val net = list.sumOf { if (it.cashIn) it.amount else -it.amount }
                        Text((if (net >= 0) "+" else "") + money(net), style = MaterialTheme.typography.labelLarge,
                            color = if (net >= 0) GotGreen else GaveRed)
                    }
                }
                items(list, key = { it.id }) { e ->
                    Card(onClick = { del = e }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (e.cashIn) Icons.Filled.SouthWest else Icons.Filled.NorthEast, null,
                                tint = if (e.cashIn) GotGreen else GaveRed)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.category, fontWeight = FontWeight.SemiBold)
                                Text(listOf(e.mode, e.note).filter { it.isNotBlank() }.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text((if (e.cashIn) "+" else "-") + rupees(e.amount), fontWeight = FontWeight.Bold,
                                color = if (e.cashIn) GotGreen else GaveRed)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("CASH IN (Aaya)", GotGreen, Modifier.weight(1f)) { add = true }
            BigButton("CASH OUT (Gaya)", GaveRed, Modifier.weight(1f)) { add = false }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isIn) "Cash In (Aaya)" else "Cash Out (Gaya)", color = if (isIn) GotGreen else GaveRed) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipsRow(listOf("In", "Out"), if (isIn) "In" else "Out", {
                    isIn = it == "In"; cat = if (isIn) "Sale" else "Purchase"
                })
                NumField(amount, { amount = it; err = false }, "Rakam *", Modifier.fillMaxWidth(), prefix = "₹")
                if (err) Text("Rakam daalein", color = MaterialTheme.colorScheme.error)
                Text("Category", style = MaterialTheme.typography.labelMedium)
                ChipsRow(if (isIn) CASH_IN_CATS else CASH_OUT_CATS, cat, { cat = it })
                if (cat == "Other") TxtField(custom, { custom = it }, "Category ka naam", Modifier.fillMaxWidth())
                Text("Mode", style = MaterialTheme.typography.labelMedium)
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
            }) { Text("Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)) {
            item {
                SectionCard {
                    Row {
                        StatBox("Items", items.size.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(0.7f))
                        StatBox("Stock value", rupees(value), MaterialTheme.colorScheme.primary, Modifier.weight(1.2f))
                        StatBox("Kam stock", low.size.toString(), if (low.isEmpty()) GotGreen else GaveRed, Modifier.weight(0.8f))
                    }
                    Text("Bechne par: ${rupees(sellValue)}  •  Anumanit munafa: ${rupees(sellValue - value)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, placeholder = { Text("Item dhoondhein") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true,
                    shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                ChipsRow(listOf("Sab items", "Sirf kam stock"), if (onlyLow) "Sirf kam stock" else "Sab items", { onlyLow = it != "Sab items" })
                Spacer(Modifier.height(8.dp))
            }
            if (shown.isEmpty()) item { EmptyState(Icons.Filled.Inventory2, if (items.isEmpty()) "Pehla item jodein — + dabayein" else "Koi item nahi mila") }
            items(shown, key = { it.id }) { it0 ->
                val isLow = it0.qty <= it0.lowStock
                Card(onClick = { edit = it0 }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(it0.name, fontWeight = FontWeight.SemiBold)
                            Text("Bikri ${rupees(it0.sellPrice)} • Kharid ${rupees(it0.purchasePrice)}", style = MaterialTheme.typography.bodySmall)
                            if (isLow) Text("⚠ Stock kam hai", style = MaterialTheme.typography.labelSmall, color = GaveRed)
                        }
                        IconButton(onClick = { Repo.adjustQty(it0.id, -1.0) }) { Icon(Icons.Filled.RemoveCircleOutline, "Kam") }
                        TextButton(onClick = { adjust = it0 }) {
                            Text("${money(it0.qty)} ${it0.unit}", fontWeight = FontWeight.Bold, color = if (isLow) GaveRed else MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { Repo.adjustQty(it0.id, 1.0) }) { Icon(Icons.Filled.AddCircleOutline, "Zyada") }
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
        AlertDialog(
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
    AlertDialog(
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
            }) { Text("Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (confirmDel && existing != null) ConfirmDialog("Item delete karein?", existing.name, "Delete",
        onConfirm = { Repo.deleteItem(existing.id); onDismiss() }, onDismiss = { confirmDel = false })
}
