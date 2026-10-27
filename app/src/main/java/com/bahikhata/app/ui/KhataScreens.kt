@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.bahikhata.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bahikhata.app.data.Entry
import com.bahikhata.app.data.Party
import com.bahikhata.app.data.Repo
import com.bahikhata.app.util.Pdf
import com.bahikhata.app.util.call
import com.bahikhata.app.util.fmtDate
import com.bahikhata.app.util.rupees
import com.bahikhata.app.util.shareFile
import com.bahikhata.app.util.whatsapp
import kotlin.math.abs

private fun balanceLabel(b: Double): String = when {
    b > 0.005 -> "Lena hai"
    b < -0.005 -> "Dena hai"
    else -> "Hisaab barabar"
}

private fun balanceColor(b: Double) = when {
    b > 0.005 -> GaveRed
    b < -0.005 -> GotGreen
    else -> androidx.compose.ui.graphics.Color.Gray
}

@Composable
fun KhataScreen(nav: Nav) {
    val tick = Repo.tick
    val all = remember(tick) { Repo.parties() }
    var filter by remember { mutableStateOf("Customers") }
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }

    val willGet = all.filter { it.balance > 0 }.sumOf { it.balance }
    val willGive = all.filter { it.balance < 0 }.sumOf { -it.balance }
    val shown = all.filter {
        (filter == "Sab" || (filter == "Customers" && it.type == "CUSTOMER") || (filter == "Suppliers" && it.type == "SUPPLIER")) &&
            (query.isBlank() || it.name.contains(query, true) || it.phone.contains(query))
    }.sortedByDescending { abs(it.balance) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)) {
            item {
                SectionCard {
                    Row {
                        StatBox("Aapko milenge", rupees(willGet), GaveRed, Modifier.weight(1f))
                        StatBox("Aapko dene hain", rupees(willGive), GotGreen, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Naam ya phone se dhoondhein") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true, shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                ChipsRow(listOf("Customers", "Suppliers", "Sab"), filter, { filter = it })
                Spacer(Modifier.height(8.dp))
            }
            if (shown.isEmpty()) item {
                EmptyState(Icons.Filled.People, if (all.isEmpty()) "Pehla customer jodein — neeche + dabayein" else "Koi nahi mila")
            }
            items(shown, key = { it.id }) { p ->
                PartyRow(p) { nav.go(Screen.PartyDetail(p.id)) }
                HorizontalDivider()
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Filled.PersonAdd, null) },
            text = { Text(if (filter == "Suppliers") "Supplier jodein" else "Customer jodein") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
    if (adding) {
        PartyDialog(null, if (filter == "Suppliers") "SUPPLIER" else "CUSTOMER", onDismiss = { adding = false }) { id ->
            adding = false
            nav.go(Screen.PartyDetail(id))
        }
    }
}

@Composable
private fun PartyRow(p: Party, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick),
        leadingContent = { Avatar(p.name) },
        headlineContent = { Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                (if (p.type == "SUPPLIER") "Supplier" else "Customer") + (if (p.phone.isNotBlank()) " • ${p.phone}" else ""),
                maxLines = 1
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(rupees(abs(p.balance)), color = balanceColor(p.balance), fontWeight = FontWeight.Bold)
                Text(balanceLabel(p.balance), style = MaterialTheme.typography.labelSmall, color = balanceColor(p.balance))
            }
        }
    )
}

/** Add or edit a party. onSaved gets the party id. */
@Composable
fun PartyDialog(existing: Party?, defaultType: String, onDismiss: () -> Unit, onSaved: (Long) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: defaultType) }
    var opening by remember { mutableStateOf(existing?.opening?.let { abs(it).plain() } ?: "") }
    var openingGet by remember { mutableStateOf((existing?.opening ?: 0.0) >= 0) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var err by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Naya party" else "Party edit") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TxtField(name, { name = it; err = false }, "Naam *", Modifier.fillMaxWidth())
                if (err) Text("Naam zaroori hai", color = MaterialTheme.colorScheme.error)
                TxtField(phone, { phone = it }, "Mobile number", Modifier.fillMaxWidth(), KeyboardType.Phone)
                ChipsRow(listOf("Customer", "Supplier"), if (type == "SUPPLIER") "Supplier" else "Customer",
                    { type = if (it == "Supplier") "SUPPLIER" else "CUSTOMER" })
                NumField(opening, { opening = it }, "Purana baaki (opening)", Modifier.fillMaxWidth(), prefix = "₹")
                ChipsRow(listOf("Mujhe lena hai", "Mujhe dena hai"), if (openingGet) "Mujhe lena hai" else "Mujhe dena hai",
                    { openingGet = it == "Mujhe lena hai" })
                TxtField(note, { note = it }, "Note / pata", Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) { err = true; return@TextButton }
                val op = opening.toAmount().let { if (openingGet) it else -it }
                if (existing == null) {
                    onSaved(Repo.addParty(name, phone, type, op, note))
                } else {
                    Repo.updateParty(existing.id, name, phone, type, op, note)
                    onSaved(existing.id)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun PartyScreen(id: Long, nav: Nav) {
    val ctx = LocalContext.current
    val tick = Repo.tick
    val party = remember(tick) { Repo.party(id) }
    if (party == null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }
    val entries = remember(tick) { Repo.entriesOf(id) }
    // running balance (oldest -> newest), shown newest first
    val running = remember(entries, party.opening) {
        var b = party.opening
        entries.map { e -> b += if (e.gave) e.amount else -e.amount; e to b }.reversed()
    }
    var newEntry by remember { mutableStateOf<Boolean?>(null) } // true = gave, false = got
    var editEntry by remember { mutableStateOf<Entry?>(null) }
    var editParty by remember { mutableStateOf(false) }
    var delParty by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val shop = Repo.businessName()
    val shopPhone = Repo.setting("phone", "")
    val nextDue = entries.mapNotNull { it.dueDate }.filter { it >= System.currentTimeMillis() - 86_400_000L }.minOrNull()

    fun sharePdf() {
        val f = Pdf.statement(ctx, party, entries, shop, shopPhone)
        shareFile(ctx, f, "application/pdf", "Statement share karein")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(party.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (party.type == "SUPPLIER") "Supplier" else "Customer", style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { sharePdf() }) { Icon(Icons.Filled.PictureAsPdf, "PDF") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit party") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { menu = false; editParty = true })
                        DropdownMenuItem(text = { Text("Party delete") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { menu = false; delParty = true })
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton("DIYE ₹ (You Gave)", GaveRed, Modifier.weight(1f)) { newEntry = true }
                    BigButton("MILE ₹ (You Got)", GotGreen, Modifier.weight(1f)) { newEntry = false }
                }
            }
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp)) {
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(balanceLabel(party.balance), color = balanceColor(party.balance))
                            Text(rupees(abs(party.balance)), style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold, color = balanceColor(party.balance))
                            if (nextDue != null && party.balance > 0) Text("Due date: ${fmtDate(nextDue)}",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        }
                        Avatar(party.name)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { call(ctx, party.phone) }, label = { Text("Call") },
                            leadingIcon = { Icon(Icons.Filled.Call, null, Modifier.size(18.dp)) }, enabled = party.phone.isNotBlank())
                        AssistChip(onClick = {
                            val msg = if (party.balance > 0)
                                "Namaste ${party.name} ji, $shop me aapka ${rupees(party.balance)} baaki hai. Kripya jald bhugtan karein. Dhanyavaad 🙏"
                            else "Namaste ${party.name} ji, $shop ke saath aapka hisaab: ${balanceLabel(party.balance)} ${rupees(abs(party.balance))}. Dhanyavaad 🙏"
                            whatsapp(ctx, party.phone, msg)
                        }, label = { Text("Reminder") }, leadingIcon = { Icon(Icons.Filled.Send, null, Modifier.size(18.dp)) })
                        AssistChip(onClick = { sharePdf() }, label = { Text("Statement") },
                            leadingIcon = { Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(18.dp)) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text("Entry", Modifier.weight(1.4f), style = MaterialTheme.typography.labelMedium)
                    Text("Diye", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = GaveRed)
                    Text("Mile", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = GotGreen)
                }
                Spacer(Modifier.height(4.dp))
            }
            if (running.isEmpty()) item {
                EmptyState(Icons.Filled.ReceiptLong, "Abhi koi entry nahi. Neeche DIYE ya MILE dabayein.")
            }
            items(running, key = { it.first.id }) { (e, bal) ->
                Card(
                    onClick = { editEntry = e },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1.4f)) {
                            Text(fmtDate(e.date), style = MaterialTheme.typography.labelMedium)
                            Text("Bal. ${rupees(abs(bal))}", style = MaterialTheme.typography.labelSmall, color = balanceColor(bal))
                            if (e.note.isNotBlank()) Text(e.note, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            Text(e.mode + (e.dueDate?.let { " • Due ${fmtDate(it)}" } ?: ""), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if (e.gave) rupees(e.amount) else "", Modifier.weight(1f), color = GaveRed, fontWeight = FontWeight.Bold)
                        Text(if (!e.gave) rupees(e.amount) else "", Modifier.weight(1f), color = GotGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    newEntry?.let { gave ->
        EntryDialog(party.name, gave, null, onDismiss = { newEntry = null }) { g, amt, mode, note, date, due ->
            Repo.addEntry(id, g, amt, mode, note, date, due)
            newEntry = null
        }
    }
    editEntry?.let { e ->
        EntryDialog(party.name, e.gave, e, onDismiss = { editEntry = null },
            onDelete = { Repo.deleteEntry(e.id); editEntry = null }) { g, amt, mode, note, date, due ->
            Repo.updateEntry(e.id, g, amt, mode, note, date, due)
            editEntry = null
        }
    }
    if (editParty) PartyDialog(party, party.type, onDismiss = { editParty = false }) { editParty = false }
    if (delParty) ConfirmDialog(
        "Party delete karein?",
        "${party.name} aur uski saari entries hat jayengi.",
        "Delete",
        onConfirm = { nav.back(); Repo.deleteParty(id) },
        onDismiss = { delParty = false }
    )
}

@Composable
fun EntryDialog(
    partyName: String,
    gave: Boolean,
    existing: Entry?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (gave: Boolean, amount: Double, mode: String, note: String, date: Long, due: Long?) -> Unit
) {
    var isGave by remember { mutableStateOf(existing?.gave ?: gave) }
    var amount by remember { mutableStateOf(existing?.amount?.plain() ?: "") }
    var mode by remember { mutableStateOf(existing?.mode ?: "CASH") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var date by remember { mutableStateOf(existing?.date ?: System.currentTimeMillis()) }
    var due by remember { mutableStateOf(existing?.dueDate) }
    var err by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    val color = if (isGave) GaveRed else GotGreen

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text((if (isGave) "Diye (You Gave) — " else "Mile (You Got) — ") + partyName, color = color) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipsRow(listOf("Diye (Gave)", "Mile (Got)"), if (isGave) "Diye (Gave)" else "Mile (Got)", { isGave = it.startsWith("Diye") })
                NumField(amount, { amount = it; err = false }, "Rakam *", Modifier.fillMaxWidth(), prefix = "₹")
                if (err) Text("Rakam daalein", color = MaterialTheme.colorScheme.error)
                Text("Payment mode", style = MaterialTheme.typography.labelMedium)
                ChipsRow(PAY_MODES, mode, { mode = it })
                TxtField(note, { note = it }, "Note (saman, bill no.)", Modifier.fillMaxWidth())
                DateField("Tareekh", date, { it?.let { d -> date = d } })
                if (isGave) DateField("Wapas kab milega (due date)", due, { due = it }, clearable = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amount.toAmount()
                if (a <= 0) { err = true; return@TextButton }
                onSave(isGave, a, mode, note, date, if (isGave) due else null)
            }) { Text("Save", color = color, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { confirmDel = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
    if (confirmDel && onDelete != null) ConfirmDialog("Entry delete karein?", "Yeh entry hat jayegi.", "Delete",
        onConfirm = onDelete, onDismiss = { confirmDel = false })
}
