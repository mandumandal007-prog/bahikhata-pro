@file:OptIn(ExperimentalMaterial3Api::class)

package com.bahikhata.app.ui

import com.bahikhata.app.util.S

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.bahikhata.app.util.smsTo
import com.bahikhata.app.util.whatsappFile
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

/** iOS action sheet: send the bill PDF to the customer's WhatsApp, SMS, or any app. */
@Composable
fun InvoiceShareSheet(inv: Invoice, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var noPhone by remember { mutableStateOf(false) }
    val who = inv.partyName.ifBlank { "customer" }
    val summary = "Namaste ${inv.partyName} ji, ${Repo.businessName()} ka bill ${inv.number}: ${rupees(inv.total)}" +
        (if (inv.paid) " (Paid)." else " baaki hai.") + " Dhanyavaad 🙏"
    if (!noPhone) IosActionSheet("Bill PDF bhejein", listOf(
        SheetAction("WhatsApp: $who") {
            if (inv.phone.isBlank()) noPhone = true else {
                val f = Pdf.invoice(ctx, inv, Repo.businessName(), Repo.setting("address", ""), Repo.setting("phone", ""), Repo.setting("gstin", ""))
                whatsappFile(ctx, f, "application/pdf", inv.phone, summary)
            }
        },
        SheetAction("SMS: $who") { if (inv.phone.isBlank()) noPhone = true else smsTo(ctx, inv.phone, summary) },
        SheetAction("Doosre app se share") { shareInvoicePdf(ctx, inv) }
    )) { if (!noPhone) onDismiss() }
    if (noPhone) IosAlert(
        onDismissRequest = { onDismiss() },
        title = { Text("Mobile number nahi hai") },
        text = { Text("Is bill me customer ka number nahi hai. Number ke saath bill banayein ya 'Doosre app se share' use karein.") },
        confirmButton = { TextButton(onClick = { onDismiss() }) { Text("OK", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) } }
    )
}

@Composable
fun InvoiceListScreen(nav: Nav) {
    val ctx = LocalContext.current
    val tick = Repo.tick
    val list = remember(tick) { Repo.invoices() }
    var filter by remember { mutableStateOf("Sab") }
    var del by remember { mutableStateOf<Invoice?>(null) }
    var shareFor by remember { mutableStateOf<Invoice?>(null) }
    val shown = list.filter { filter == "Sab" || (filter == "Paid" && it.paid) || (filter == "Baaki" && !it.paid) }
    val total = list.sumOf { it.total }
    val unpaid = list.filter { !it.paid }.sumOf { it.total }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)) {
            item {
                SectionCard {
                    Row {
                        StatBox("Kul bills", list.size.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(0.6f))
                        StatBox("Kul bikri", rupees(total), GotGreen, Modifier.weight(1f))
                        StatBox("Baaki", rupees(unpaid), GaveRed, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(14.dp))
                ChipsRow(listOf("Sab", "Baaki", "Paid"), filter, { filter = it })
                Spacer(Modifier.height(14.dp))
            }
            if (shown.isEmpty()) item { EmptyState(Icons.AutoMirrored.Filled.ReceiptLong, "Koi bill nahi. 'Naya Bill' dabayein.") }
            itemsIndexed(shown, key = { _, x -> x.id }) { i, inv ->
                var menu by remember { mutableStateOf(false) }
                GroupCell(i, shown.size, onClick = { menu = true }, padding = PaddingValues(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("${inv.number} · ${inv.partyName.ifBlank { "Cash" }}", fontSize = 17.sp, color = ios.label,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${fmtDate(inv.date)} · ${inv.lines.size} item" + (if (inv.gstPercent > 0) " · GST ${money(inv.gstPercent)}%" else ""),
                            fontSize = 13.sp, color = ios.secondaryLabel)
                        Spacer(Modifier.height(6.dp))
                        IosPill(if (inv.paid) "Paid ✓" else "Baaki — tap = Paid", inv.paid, { Repo.setInvoicePaid(inv.id, !inv.paid) }, GotGreen)
                    }
                    Text(rupees(inv.total), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = ios.label)
                    Chevron()
                }
                if (menu) IosActionSheet("${inv.number} · ${rupees(inv.total)}", listOf(
                    SheetAction("PDF bhejein (WhatsApp / SMS)") { shareFor = inv },
                    SheetAction("WhatsApp par text") { whatsapp(ctx, inv.phone, invoiceText(inv)) },
                    SheetAction(if (inv.paid) "Baaki mark karein" else "Paid mark karein") { Repo.setInvoicePaid(inv.id, !inv.paid) },
                    SheetAction("Bill delete karein", destructive = true) { del = inv }
                )) { menu = false }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { nav.go(Screen.NewInvoice) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Naya Bill") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
    shareFor?.let { inv -> InvoiceShareSheet(inv) { shareFor = null } }
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
    var gst by remember { mutableStateOf(S.defGst) }
    var discount by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf(S.billPaid) }
    var reduceStock by remember { mutableStateOf(S.billReduceStock) }
    var toBooks by remember { mutableStateOf(S.billToBooks) }
    var pickParty by remember { mutableStateOf(false) }
    var lineDialog by remember { mutableStateOf<Int?>(null) } // -1 = new
    var saved by remember { mutableStateOf<Invoice?>(null) }
    var sharedSaved by remember { mutableStateOf<Invoice?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    val draft = Invoice(
        number = number, partyName = partyName, phone = phone, date = date,
        lines = lines.map { InvoiceLine(it.name, it.qty, it.rate) },
        gstPercent = gst.toAmount(), discount = discount.toAmount(), paid = paid
    )

    val backLabel = remember { nav.backLabel() }
    val behavior = rememberLargeTitle()
    Scaffold(
        modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { IosNavBar("Naya Bill", behavior, backLabel = backLabel, onBack = { nav.back() }) },
        bottomBar = {
            Column(Modifier.background(ios.barBg)) {
                Hairline()
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Total", fontSize = 13.sp, color = ios.secondaryLabel)
                        Text(rupees(draft.total), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ios.label)
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
                IconButton(onClick = { pickParty = true }, modifier = Modifier.padding(top = 18.dp)) { Icon(Icons.Outlined.PersonSearch, "Party chunein", tint = ios.tint) }
            }
            TxtField(phone, { phone = it }, "Mobile", Modifier.fillMaxWidth(), KeyboardType.Phone)

            SectionCard("Items") {
                if (lines.isEmpty()) Text("Abhi koi item nahi", color = ios.secondaryLabel)
                lines.forEachIndexed { i, l ->
                    Row(Modifier.fillMaxWidth().clickable { lineDialog = i }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.name, fontWeight = FontWeight.SemiBold)
                            Text("${money(l.qty)} x ${rupees(l.rate)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(rupees(l.qty * l.rate), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { lines.removeAt(i) }) { Icon(Icons.Filled.RemoveCircle, "Hatao", tint = IosRed) }
                    }
                    Hairline()
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
                    Text("Paisa mil gaya (Paid)", Modifier.weight(1f)); IosSwitch(checked = paid, onCheckedChange = { paid = it })
                }
                if (lines.any { it.itemId != null }) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Stock se ghatao", Modifier.weight(1f)); IosSwitch(checked = reduceStock, onCheckedChange = { reduceStock = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (paid) "Cashbook me 'Sale' entry jodo"
                        else if (partyId != null) "Party ke khata me udhaar (Diye) jodo"
                        else "Khata me jodne ke liye upar se party chunein",
                        Modifier.weight(1f)
                    )
                    IosSwitch(checked = toBooks, onCheckedChange = { toBooks = it }, enabled = paid || partyId != null)
                }
            }
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickParty) {
        IosAlert(
            onDismissRequest = { pickParty = false },
            title = { Text("Party chunein") },
            text = {
                if (parties.isEmpty()) Text("Khata me abhi koi party nahi hai. Naam seedha likh dein.")
                else LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(parties) { p ->
                        Row(Modifier.fillMaxWidth().clickable {
                            partyName = p.name; phone = p.phone; partyId = p.id; pickParty = false
                        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PartyAvatar(p.id, p.name, 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontSize = 16.sp, color = ios.label, textAlign = TextAlign.Start)
                                if (p.phone.isNotBlank()) Text(p.phone, fontSize = 13.sp, color = ios.secondaryLabel, textAlign = TextAlign.Start)
                            }
                        }
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
        IosSheet(
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
                }) { Text("OK", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { lineDialog = null }) { Text("Cancel", fontSize = 17.sp) } }
        )
    }

    sharedSaved?.let { inv -> InvoiceShareSheet(inv) { sharedSaved = null; saved = null; nav.back() } }
    if (sharedSaved == null) saved?.let { inv ->
        IosAlert(
            onDismissRequest = { saved = null; nav.back() },
            icon = { Icon(Icons.Filled.CheckCircle, null, tint = GotGreen, modifier = Modifier.size(44.dp)) },
            title = { Text("Bill save ho gaya ✓") },
            text = { Text("${inv.number} • ${rupees(inv.total)}\nPDF bana kar WhatsApp par bhej sakte hain.") },
            confirmButton = {
                TextButton(onClick = { sharedSaved = inv }) { Text("PDF bhejein", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { saved = null; nav.back() }) { Text("Done") } }
        )
    }
}
