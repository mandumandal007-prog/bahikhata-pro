@file:OptIn(ExperimentalMaterial3Api::class)

package com.bahikhata.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Context
import com.bahikhata.app.data.Invoice
import com.bahikhata.app.data.InvoiceLine
import com.bahikhata.app.data.Repo
import com.bahikhata.app.util.Pdf
import com.bahikhata.app.util.fmtDate
import com.bahikhata.app.util.money
import com.bahikhata.app.util.rupees
import com.bahikhata.app.util.shareFile
import com.bahikhata.app.util.whatsapp

fun shareInvoicePdf(ctx: Context, inv: Invoice) {
    val f = Pdf.invoice(
        ctx, inv, Repo.businessName(), Repo.setting("address", ""), Repo.setting("phone", ""), Repo.setting("gstin", "")
    )
    shareFile(ctx, f, "application/pdf", "Bill share karein")
}

fun invoiceText(inv: Invoice): String = buildString {
    append("*${Repo.businessName()}*\n")
    append("Bill ${inv.number} • ${fmtDate(inv.date)}\n")
    append("Party: ${inv.partyName}\n\n")
    inv.lines.forEach { append("${it.name}  ${money(it.qty)} x ${money(it.rate)} = ${rupees(it.qty * it.rate)}\n") }
    append("\nSubtotal: ${rupees(inv.subtotal)}\n")
    if (inv.discount > 0) append("Discount: -${rupees(inv.discount)}\n")
    if (inv.gstPercent > 0) append("GST ${money(inv.gstPercent)}%: ${rupees(inv.gstAmount)}\n")
    append("*Total: ${rupees(inv.total)}*  (${if (inv.paid) "Paid" else "Baaki"})\n")
    append("Dhanyavaad 🙏")
}

@Composable
fun InvoiceListScreen(nav: Nav) {
    val ctx = LocalContext.current
    val tick = Repo.tick
    val list = remember(tick) { Repo.invoices() }
    var filter by remember { mutableStateOf("Sab") }
    var del by remember { mutableStateOf<Invoice?>(null) }
    val shown = list.filter { filter == "Sab" || (filter == "Paid" && it.paid) || (filter == "Baaki" && !it.paid) }
    val total = list.sumOf { it.total }
    val unpaid = list.filter { !it.paid }.sumOf { it.total }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)) {
            item {
                SectionCard {
                    Row {
                        StatBox("Kul bills", list.size.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(0.6f))
                        StatBox("Kul bikri", rupees(total), GotGreen, Modifier.weight(1f))
                        StatBox("Baaki", rupees(unpaid), GaveRed, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                ChipsRow(listOf("Sab", "Baaki", "Paid"), filter, { filter = it })
                Spacer(Modifier.height(8.dp))
            }
            if (shown.isEmpty()) item { EmptyState(Icons.AutoMirrored.Filled.ReceiptLong, "Koi bill nahi. 'Naya Bill' dabayein.") }
            items(shown, key = { it.id }) { inv ->
                var menu by remember { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${inv.number} • ${inv.partyName.ifBlank { "Cash" }}", fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${fmtDate(inv.date)} • ${inv.lines.size} item" + (if (inv.gstPercent > 0) " • GST ${money(inv.gstPercent)}%" else ""),
                                style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                            FilterChip(
                                selected = inv.paid, onClick = { Repo.setInvoicePaid(inv.id, !inv.paid) },
                                label = { Text(if (inv.paid) "Paid ✓" else "Baaki — tap = Paid") }
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(rupees(inv.total), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Row {
                                IconButton(onClick = { shareInvoicePdf(ctx, inv) }) { Icon(Icons.Filled.PictureAsPdf, "PDF") }
                                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("PDF share") }, leadingIcon = { Icon(Icons.Filled.Share, null) },
                                        onClick = { menu = false; shareInvoicePdf(ctx, inv) })
                                    DropdownMenuItem(text = { Text("WhatsApp par text") }, leadingIcon = { Icon(Icons.Filled.Send, null) },
                                        onClick = { menu = false; whatsapp(ctx, inv.phone, invoiceText(inv)) })
                                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                        onClick = { menu = false; del = inv })
                                }
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { nav.go(Screen.NewInvoice) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Naya Bill") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
    del?.let { inv ->
        ConfirmDialog("Bill delete karein?", "${inv.number} • ${rupees(inv.total)}", "Delete",
            onConfirm = { Repo.deleteInvoice(inv.id) }, onDismiss = { del = null })
    }
}

private data class LineDraft(val name: String, val qty: Double, val rate: Double, val itemId: Long? = null)

@Composable
fun InvoiceCreateScreen(nav: Nav) {
    val ctx = LocalContext.current
    val parties = remember { Repo.parties() }
    val stock = remember { Repo.items() }
    var number by remember { mutableStateOf(Repo.nextInvoiceNumber()) }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var partyName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var partyId by remember { mutableStateOf<Long?>(null) }
    val lines = remember { mutableStateListOf<LineDraft>() }
    var gst by remember { mutableStateOf("0") }
    var discount by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf(true) }
    var reduceStock by remember { mutableStateOf(true) }
    var toBooks by remember { mutableStateOf(true) }
    var pickParty by remember { mutableStateOf(false) }
    var lineDialog by remember { mutableStateOf<Int?>(null) } // -1 = new
    var saved by remember { mutableStateOf<Invoice?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    val draft = Invoice(
        number = number, partyName = partyName, phone = phone, date = date,
        lines = lines.map { InvoiceLine(it.name, it.qty, it.rate) },
        gstPercent = gst.toAmount(), discount = discount.toAmount(), paid = paid
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Naya Bill") },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Total", style = MaterialTheme.typography.labelMedium)
                        Text(rupees(draft.total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = {
                        err = null
                        if (lines.isEmpty()) { err = "Kam se kam ek item jodein"; return@Button }
                        val id = Repo.addInvoice(draft)
                        val inv = draft.copy(id = id)
                        if (reduceStock) lines.forEach { l -> l.itemId?.let { Repo.adjustQty(it, -l.qty) } }
                        if (toBooks) {
                            if (paid) Repo.addCash(true, inv.total, "Sale", "Bill ${inv.number}" +
                                (if (partyName.isNotBlank()) " - $partyName" else ""), "CASH", date)
                            else partyId?.let { pid -> Repo.addEntry(pid, true, inv.total, "CASH", "Bill ${inv.number}", date, null) }
                        }
                        saved = inv
                    }, modifier = Modifier.height(52.dp)) { Text("Save Bill") }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TxtField(number, { number = it }, "Bill no.", Modifier.weight(1f))
                Box(Modifier.weight(1.2f)) { DateField("Tareekh", date, { it?.let { d -> date = d } }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TxtField(partyName, { partyName = it; partyId = null }, "Party / customer ka naam", Modifier.weight(1f))
                IconButton(onClick = { pickParty = true }) { Icon(Icons.Filled.PersonSearch, "Party chunein") }
            }
            TxtField(phone, { phone = it }, "Mobile", Modifier.fillMaxWidth(), KeyboardType.Phone)

            SectionCard("Items") {
                if (lines.isEmpty()) Text("Abhi koi item nahi", color = MaterialTheme.colorScheme.onSurfaceVariant)
                lines.forEachIndexed { i, l ->
                    Row(Modifier.fillMaxWidth().clickable { lineDialog = i }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.name, fontWeight = FontWeight.SemiBold)
                            Text("${money(l.qty)} x ${rupees(l.rate)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(rupees(l.qty * l.rate), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { lines.removeAt(i) }) { Icon(Icons.Filled.Close, "Hatao") }
                    }
                    HorizontalDivider()
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { lineDialog = -1 }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Item jodein")
                }
            }

            SectionCard("GST aur discount") {
                ChipsRow(listOf("0", "5", "12", "18", "28"), gst, { gst = it })
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField(gst, { gst = it }, "GST %", Modifier.weight(1f), suffix = "%")
                    NumField(discount, { discount = it }, "Discount", Modifier.weight(1f), prefix = "₹")
                }
                Spacer(Modifier.height(8.dp))
                KeyValue("Subtotal", rupees(draft.subtotal))
                if (draft.discount > 0) KeyValue("Discount", "-" + rupees(draft.discount))
                if (draft.gstPercent > 0) {
                    KeyValue("CGST ${money(draft.gstPercent / 2)}%", rupees(draft.gstAmount / 2))
                    KeyValue("SGST ${money(draft.gstPercent / 2)}%", rupees(draft.gstAmount / 2))
                }
                KeyValue("Total", rupees(draft.total), bold = true)
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Paisa mil gaya (Paid)", Modifier.weight(1f)); Switch(checked = paid, onCheckedChange = { paid = it })
                }
                if (lines.any { it.itemId != null }) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Stock se ghatao", Modifier.weight(1f)); Switch(checked = reduceStock, onCheckedChange = { reduceStock = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (paid) "Cashbook me 'Sale' entry jodo"
                        else if (partyId != null) "Party ke khata me udhaar (Diye) jodo"
                        else "Khata me jodne ke liye upar se party chunein",
                        Modifier.weight(1f)
                    )
                    Switch(checked = toBooks, onCheckedChange = { toBooks = it }, enabled = paid || partyId != null)
                }
            }
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickParty) {
        AlertDialog(
            onDismissRequest = { pickParty = false },
            title = { Text("Party chunein") },
            text = {
                if (parties.isEmpty()) Text("Khata me abhi koi party nahi hai. Naam seedha likh dein.")
                else LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(parties) { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text(p.phone) },
                            leadingContent = { Avatar(p.name) },
                            modifier = Modifier.clickable {
                                partyName = p.name; phone = p.phone; partyId = p.id; pickParty = false
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickParty = false }) { Text("Band") } }
        )
    }

    lineDialog?.let { idx ->
        val ex = if (idx >= 0) lines.getOrNull(idx) else null
        var name by remember(idx) { mutableStateOf(ex?.name ?: "") }
        var qty by remember(idx) { mutableStateOf(ex?.qty?.plain() ?: "1") }
        var rate by remember(idx) { mutableStateOf(ex?.rate?.plain() ?: "") }
        var itemId by remember(idx) { mutableStateOf(ex?.itemId) }
        var showStock by remember(idx) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { lineDialog = null },
            title = { Text(if (ex == null) "Item jodein" else "Item badlein") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (stock.isNotEmpty()) {
                        OutlinedButton(onClick = { showStock = !showStock }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Inventory2, null); Spacer(Modifier.width(6.dp)); Text("Stock se chunein")
                        }
                        if (showStock) stock.forEach { s ->
                            Text("${s.name} • ${rupees(s.sellPrice)} • ${money(s.qty)} ${s.unit}",
                                Modifier.fillMaxWidth().clickable {
                                    name = s.name; rate = s.sellPrice.plain(); itemId = s.id; showStock = false
                                }.padding(8.dp))
                        }
                    }
                    TxtField(name, { name = it; itemId = null }, "Item ka naam", Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField(qty, { qty = it }, "Qty", Modifier.weight(1f))
                        NumField(rate, { rate = it }, "Rate", Modifier.weight(1f), prefix = "₹")
                    }
                    Text("Amount: ${rupees(qty.toAmount() * rate.toAmount())}", fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isBlank()) return@TextButton
                    val d = LineDraft(name.trim(), qty.toAmount().takeIf { it > 0 } ?: 1.0, rate.toAmount(), itemId)
                    if (ex == null) lines.add(d) else lines[idx] = d
                    lineDialog = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { lineDialog = null }) { Text("Cancel") } }
        )
    }

    saved?.let { inv ->
        AlertDialog(
            onDismissRequest = { saved = null; nav.back() },
            icon = { Icon(Icons.Filled.CheckCircle, null, tint = GotGreen) },
            title = { Text("Bill save ho gaya ✓") },
            text = { Text("${inv.number} • ${rupees(inv.total)}\nPDF bana kar WhatsApp par bhej sakte hain.") },
            confirmButton = {
                TextButton(onClick = { shareInvoicePdf(ctx, inv); saved = null; nav.back() }) { Text("PDF Share") }
            },
            dismissButton = { TextButton(onClick = { saved = null; nav.back() }) { Text("Done") } }
        )
    }
}
