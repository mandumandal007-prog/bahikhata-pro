@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.bahikhata.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.bahikhata.app.util.PartyPhotos
import com.bahikhata.app.util.smsTo
import com.bahikhata.app.util.whatsappFile
import java.io.File
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
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
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
    else -> Color(0xFF8E8E93)
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
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)) {
            item {
                IosSearchField(query, { query = it }, "Naam ya phone se dhoondhein")
                Spacer(Modifier.height(12.dp))
                SectionCard {
                    Row {
                        StatBox("Aapko milenge", rupees(willGet), GaveRed, Modifier.weight(1f))
                        StatBox("Aapko dene hain", rupees(willGive), GotGreen, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(14.dp))
                ChipsRow(listOf("Customers", "Suppliers", "Sab"), filter, { filter = it })
                Spacer(Modifier.height(14.dp))
            }
            if (shown.isEmpty()) item {
                EmptyState(Icons.Outlined.People, if (all.isEmpty()) "Pehla customer jodein — neeche + dabayein" else "Koi nahi mila")
            }
            itemsIndexed(shown, key = { _, p -> p.id }) { i, p ->
                PartyRow(p, i, shown.size) { nav.go(Screen.PartyDetail(p.id)) }
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
private fun PartyRow(p: Party, index: Int, count: Int, onClick: () -> Unit) {
    GroupCell(index, count, onClick = onClick, separatorInset = 68.dp, padding = PaddingValues(start = 12.dp, end = 8.dp, top = 9.dp, bottom = 9.dp)) {
        PartyAvatar(p.id, p.name, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (if (p.type == "SUPPLIER") "Supplier" else "Customer") + (if (p.phone.isNotBlank()) " · ${p.phone}" else ""),
                fontSize = 14.sp, color = ios.secondaryLabel, maxLines = 1
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(rupees(abs(p.balance)), color = balanceColor(p.balance), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(balanceLabel(p.balance), fontSize = 12.sp, color = balanceColor(p.balance))
        }
        Chevron()
    }
}

/** Photo picked in the party sheet, before Save. */
private sealed class PhotoEdit {
    data object Same : PhotoEdit()
    data object Removed : PhotoEdit()
    data class New(val file: File) : PhotoEdit()
}

/** Add or edit a party. onSaved gets the party id. */
@Composable
fun PartyDialog(existing: Party?, defaultType: String, onDismiss: () -> Unit, onSaved: (Long) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: defaultType) }
    var opening by remember { mutableStateOf(existing?.opening?.let { abs(it).plain() } ?: "") }
    var openingGet by remember { mutableStateOf((existing?.opening ?: 0.0) >= 0) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var err by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf<PhotoEdit>(PhotoEdit.Same) }
    var photoMenu by remember { mutableStateOf(false) }

    val camFile = remember { File(ctx.cacheDir, "party_camera.jpg") }
    val camUri = remember { FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", camFile) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) PartyPhotos.prepare(ctx, uri)?.let { photo = PhotoEdit.New(it) }
    }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) PartyPhotos.prepare(ctx, camUri)?.let { photo = PhotoEdit.New(it) }
    }
    val existingId = existing?.id ?: 0L
    val hasPhoto = when (val p = photo) {
        PhotoEdit.Same -> PartyPhotos.has(ctx, existingId)
        PhotoEdit.Removed -> false
        is PhotoEdit.New -> true
    }

    IosSheet(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Naya party" else "Party edit") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.clip(CircleShape).clickable { photoMenu = true }) {
                        when (val p = photo) {
                            is PhotoEdit.New -> {
                                val bmp = remember(p.file) { PartyPhotos.loadFile(p.file) }
                                if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.size(96.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                                else InitialsAvatar(name, 96.dp)
                            }
                            PhotoEdit.Removed -> InitialsAvatar(name, 96.dp)
                            PhotoEdit.Same -> PartyAvatar(existingId, name, 96.dp)
                        }
                    }
                    Text(if (hasPhoto) "Photo badlein" else "Photo jodein", color = ios.tint, fontSize = 15.sp,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { photoMenu = true }.padding(6.dp))
                }
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
                val id = if (existing == null) {
                    Repo.addParty(name, phone, type, op, note)
                } else {
                    Repo.updateParty(existing.id, name, phone, type, op, note)
                    existing.id
                }
                when (val p = photo) {
                    is PhotoEdit.New -> if (id > 0) PartyPhotos.commit(ctx, id, p.file)
                    PhotoEdit.Removed -> PartyPhotos.delete(ctx, id)
                    PhotoEdit.Same -> {}
                }
                onSaved(id)
            }) { Text("Save", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontSize = 17.sp) } }
    )
    if (photoMenu) {
        val actions = mutableListOf(
            SheetAction("Photo lo (camera)") {
                try { camFile.parentFile?.mkdirs(); camFile.createNewFile(); take.launch(camUri) } catch (_: Exception) {}
            },
            SheetAction("Gallery se chuno") {
                try { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } catch (_: Exception) {}
            }
        )
        if (hasPhoto) actions.add(SheetAction("Photo hatao", destructive = true) { photo = PhotoEdit.Removed })
        IosActionSheet("Party ki photo", actions) { photoMenu = false }
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(ios.cell).clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val c = if (enabled) ios.tint else ios.tertiaryLabel
        Icon(icon, null, tint = c, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = c, maxLines = 1)
    }
}

@Composable
fun PartyScreen(id: Long, nav: Nav) {
    val ctx = LocalContext.current
    val tick = Repo.tick
    val party = remember(tick) { Repo.party(id) }
    val backLabel = remember { nav.backLabel() }
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
    var shareMenu by remember { mutableStateOf(false) }
    var noPhone by remember { mutableStateOf(false) }
    val shop = Repo.businessName()
    val shopPhone = Repo.setting("phone", "")
    val nextDue = entries.mapNotNull { it.dueDate }.filter { it >= System.currentTimeMillis() - 86_400_000L }.minOrNull()

    fun statementFile() = Pdf.statement(ctx, party, entries, shop, shopPhone)
    fun sharePdf() {
        shareFile(ctx, statementFile(), "application/pdf", "Statement share karein")
    }
    fun summary(): String = if (party.balance > 0)
        "Namaste ${party.name} ji, $shop me aapka ${rupees(party.balance)} baaki hai. Kripya jald bhugtan karein. Dhanyavaad 🙏"
    else "Namaste ${party.name} ji, $shop ke saath aapka hisaab: ${balanceLabel(party.balance)} ${rupees(abs(party.balance))}. Dhanyavaad 🙏"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            IosNavBar(
                title = party.name,
                subtitle = if (party.type == "SUPPLIER") "Supplier" else "Customer",
                backLabel = backLabel,
                onBack = { nav.back() },
                actions = {
                    NavIcon(Icons.Outlined.IosShare, "Share") { shareMenu = true }
                    NavIcon(Icons.Outlined.MoreHoriz, "More") { menu = true }
                }
            )
        },
        bottomBar = {
            Column(Modifier.background(ios.barBg)) {
                Hairline()
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton("DIYE ₹ (You Gave)", GaveRed, Modifier.weight(1f)) { newEntry = true }
                    BigButton("MILE ₹ (You Got)", GotGreen, Modifier.weight(1f)) { newEntry = false }
                }
            }
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp)) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.clip(CircleShape).clickable { editParty = true }) { PartyAvatar(party.id, party.name, 88.dp) }
                    Spacer(Modifier.height(8.dp))
                    Text(party.name, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (party.phone.isNotBlank()) Text(party.phone, fontSize = 15.sp, color = ios.secondaryLabel)
                    Spacer(Modifier.height(10.dp))
                    Text(rupees(abs(party.balance)), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = balanceColor(party.balance))
                    Text(balanceLabel(party.balance), fontSize = 15.sp, color = balanceColor(party.balance))
                    if (nextDue != null && party.balance > 0) Text("Due date: ${fmtDate(nextDue)}", fontSize = 13.sp, color = IosOrange)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionTile(Icons.Outlined.Call, "Call", party.phone.isNotBlank(), Modifier.weight(1f)) { call(ctx, party.phone) }
                    ActionTile(Icons.Outlined.Chat, "Reminder", modifier = Modifier.weight(1f)) { whatsapp(ctx, party.phone, summary()) }
                    ActionTile(Icons.Outlined.PictureAsPdf, "Statement", modifier = Modifier.weight(1f)) { shareMenu = true }
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text("ENTRY", Modifier.weight(1.4f), fontSize = 13.sp, color = ios.secondaryLabel)
                    Text("DIYE", Modifier.weight(1f), fontSize = 13.sp, color = GaveRed)
                    Text("MILE", Modifier.weight(1f), fontSize = 13.sp, color = GotGreen)
                }
            }
            if (running.isEmpty()) item {
                EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, "Abhi koi entry nahi. Neeche DIYE ya MILE dabayein.")
            }
            itemsIndexed(running, key = { _, r -> r.first.id }) { i, (e, bal) ->
                GroupCell(i, running.size, onClick = { editEntry = e }) {
                    Column(Modifier.weight(1.4f)) {
                        Text(fmtDate(e.date), fontSize = 15.sp, color = ios.label)
                        Text("Bal. ${rupees(abs(bal))}", fontSize = 12.sp, color = balanceColor(bal))
                        if (e.note.isNotBlank()) Text(e.note, fontSize = 13.sp, color = ios.secondaryLabel, maxLines = 2)
                        Text(e.mode + (e.dueDate?.let { " · Due ${fmtDate(it)}" } ?: ""), fontSize = 12.sp, color = ios.secondaryLabel)
                    }
                    Text(if (e.gave) rupees(e.amount) else "", Modifier.weight(1f), color = GaveRed, fontWeight = FontWeight.SemiBold)
                    Text(if (!e.gave) rupees(e.amount) else "", Modifier.weight(1f), color = GotGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (menu) IosActionSheet(party.name, listOf(
        SheetAction("Party edit karein") { editParty = true },
        SheetAction("Party delete karein", destructive = true) { delParty = true }
    )) { menu = false }
    if (shareMenu) IosActionSheet("Statement PDF bhejein", listOf(
        SheetAction("WhatsApp: ${party.name}") {
            if (party.phone.isBlank()) noPhone = true
            else whatsappFile(ctx, statementFile(), "application/pdf", party.phone, summary())
        },
        SheetAction("SMS: ${party.name}") {
            if (party.phone.isBlank()) noPhone = true else smsTo(ctx, party.phone, summary())
        },
        SheetAction("Doosre app se share") { sharePdf() }
    )) { shareMenu = false }
    if (noPhone) IosAlert(
        onDismissRequest = { noPhone = false },
        title = { Text("Mobile number nahi hai") },
        text = { Text("${party.name} ka number save nahi hai. Pehle number jodein.") },
        confirmButton = { TextButton(onClick = { noPhone = false; editParty = true }) { Text("Number jodein", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = { noPhone = false }) { Text("Cancel", fontSize = 17.sp) } }
    )

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
        onConfirm = { nav.back(); Repo.deleteParty(id); PartyPhotos.delete(ctx, id) },
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

    IosSheet(
        onDismissRequest = onDismiss,
        title = { Text((if (isGave) "Diye — " else "Mile — ") + partyName, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                if (onDelete != null) {
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ios.sheetCell).clickable { confirmDel = true }
                        .padding(13.dp), contentAlignment = Alignment.Center) { Text("Entry delete karein", color = IosRed, fontSize = 17.sp) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amount.toAmount()
                if (a <= 0) { err = true; return@TextButton }
                onSave(isGave, a, mode, note, date, if (isGave) due else null)
            }) { Text("Save", color = color, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", fontSize = 17.sp) }
        }
    )
    if (confirmDel && onDelete != null) ConfirmDialog("Entry delete karein?", "Yeh entry hat jayegi.", "Delete",
        onConfirm = onDelete, onDismiss = { confirmDel = false })
}
