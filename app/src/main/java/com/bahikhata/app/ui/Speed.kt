@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.bahikhata.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bahikhata.app.data.Party
import com.bahikhata.app.data.Repo
import com.bahikhata.app.util.S
import com.bahikhata.app.util.rupees
import com.bahikhata.app.util.whatsapp
import kotlinx.coroutines.launch
import kotlin.math.abs

// v1.0.42 — usability & speed: quick entry, swipe actions, bulk reminders, tips, empty states.

/** Friendly empty state: icon, title, a hint line and an optional button. */
@Composable
fun EmptyHint(icon: ImageVector, title: String, hint: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(76.dp).clip(CircleShape).background(ios.tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(38.dp), tint = ios.tint)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = ios.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(hint, fontSize = 14.sp, color = ios.secondaryLabel, textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            Box(Modifier.clip(CircleShape).background(ios.tint).clickable(onClick = onAction).padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(action, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

/**
 * Quick entry from the Khata home: pick (or create) a party, type the amount, Diye/Mile, Save.
 * Everything is local first, like every other entry.
 */
@Composable
fun QuickEntrySheet(onDismiss: () -> Unit, onSaved: (partyId: Long, gave: Boolean, amount: Double) -> Unit) {
    val all = remember { Repo.parties() }
    val recentIds = remember { Repo.lastEntryDates().entries.sortedByDescending { it.value }.map { it.key } }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Party?>(null) }
    var amount by remember { mutableStateOf("") }
    var gave by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf(S.defMode.takeIf { it in PAY_MODES } ?: "CASH") }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val haptic = rememberHaptic()
    val color = if (gave) GaveRed else GotGreen

    val matches = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            val byId = all.associateBy { it.id }
            (recentIds.mapNotNull { byId[it] } + all).distinctBy { it.id }.take(8)
        } else all.filter { it.name.contains(q, true) || it.phone.contains(q) }
            .sortedBy { if (it.name.startsWith(q, true)) 0 else 1 }.take(8)
    }

    IosSheet(
        onDismissRequest = onDismiss,
        title = { Text("Jaldi entry", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val p = picked
                if (p == null) {
                    IosSearchField(query, { query = it; err = null }, "Party ka naam likhein ya chunein")
                    matches.forEach { m ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ios.sheetCell)
                            .clickable { picked = m; haptic() }.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            PartyAvatar(m.id, m.name, 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(m.name, Modifier.weight(1f), fontSize = 16.sp, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (abs(m.balance) > 0.005) Text(rupees(abs(m.balance)), fontSize = 14.sp, color = balanceColor(m.balance))
                        }
                    }
                    val q = query.trim()
                    if (q.isNotEmpty() && all.none { it.name.equals(q, true) }) {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ios.tint.copy(alpha = 0.12f))
                            .clickable {
                                val id = Repo.addParty(q, "", "CUSTOMER", 0.0, "")
                                picked = Repo.party(id); haptic()
                            }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PersonAdd, null, tint = ios.tint)
                            Spacer(Modifier.width(10.dp))
                            Text("Naya customer: \"$q\"", color = ios.tint, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (all.isEmpty() && q.isEmpty()) Text("Naam likhein — naya customer turant ban jayega.", fontSize = 13.sp, color = ios.secondaryLabel)
                } else {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ios.sheetCell)
                        .clickable { picked = null }.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        PartyAvatar(p.id, p.name, 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${balanceLabel(p.balance)} ${rupees(abs(p.balance))}", fontSize = 12.sp, color = balanceColor(p.balance))
                        }
                        Text("Badlein", color = ios.tint, fontSize = 14.sp)
                    }
                    ChipsRow(listOf("Diye (Gave)", "Mile (Got)"), if (gave) "Diye (Gave)" else "Mile (Got)", { gave = it.startsWith("Diye"); haptic() })
                    NumField(amount, { amount = it; err = null }, "Rakam *", Modifier.fillMaxWidth(), prefix = "₹")
                    ChipsRow(PAY_MODES, mode, { mode = it })
                    TxtField(note, { note = it }, "Note (optional)", Modifier.fillMaxWidth())
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = picked ?: run { err = "Pehle party chunein"; return@TextButton }
                val a = amount.toAmount()
                if (a <= 0) { err = "Rakam daalein"; return@TextButton }
                val due = if (gave) S.defDueDays.takeIf { it > 0 }?.let { com.bahikhata.app.util.addDays(System.currentTimeMillis(), it) } else null
                Repo.addEntry(p.id, gave, a, mode, note, System.currentTimeMillis(), due)
                haptic()
                onSaved(p.id, gave, a)
            }) { Text("Save", color = color, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontSize = 17.sp) } }
    )
}

/**
 * Send WhatsApp reminders to many parties, one by one. Each tap opens WhatsApp with the
 * ready message; when the user comes back, the next party is waiting. Progress is shown.
 */
@Composable
fun BulkReminderSheet(parties: List<Party>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val shop = remember { Repo.businessName() }
    val list = remember { parties.filter { it.phone.isNotBlank() && it.balance > 0.005 } }
    val noPhone = parties.size - list.size
    var idx by remember { mutableIntStateOf(0) }
    var sent by remember { mutableIntStateOf(0) }
    val haptic = rememberHaptic()
    val progress by animateFloatAsState(if (list.isEmpty()) 1f else idx.toFloat() / list.size, label = "bulk")

    IosSheet(
        onDismissRequest = onDismiss,
        title = { Text("Sabko reminder", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = GotGreen, trackColor = ios.separator)
                Text(if (list.isEmpty()) "Kisi ka mobile number nahi hai." else "${minOf(idx + 1, list.size)} / ${list.size}  ·  bheje: $sent",
                    fontSize = 13.sp, color = ios.secondaryLabel)
                if (idx < list.size) {
                    val p = list[idx]
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ios.sheetCell).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        PartyAvatar(p.id, p.name, 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(p.phone, fontSize = 13.sp, color = ios.secondaryLabel)
                        }
                        Text(rupees(p.balance), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = GaveRed)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton("WhatsApp bhejein", GotGreen, Modifier.weight(1.6f)) {
                            haptic()
                            whatsapp(ctx, p.phone, S.reminderText(p.name, rupees(p.balance), shop, true))
                            sent++; idx++
                        }
                        Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(12.dp)).background(ios.sheetCell)
                            .clickable { idx++ }, contentAlignment = Alignment.Center) { Text("Chhodein", color = ios.tint, fontSize = 16.sp) }
                    }
                } else if (list.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.CheckCircle, null, Modifier.size(48.dp), tint = GotGreen)
                        Spacer(Modifier.height(6.dp))
                        Text("Ho gaya! $sent logon ko reminder bheja.", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ios.label)
                    }
                }
                if (noPhone > 0) Text("$noPhone party ka number nahi hai, unhe chhod diya.", fontSize = 12.sp, color = ios.secondaryLabel)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (idx >= list.size) "Done" else "Band karein", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) } }
    )
}

private data class Tip(val icon: ImageVector, val color: Color, val title: String, val text: String)

/** First-run tips, shown once (skippable). */
@Composable
fun TipsCarousel(onDone: () -> Unit) {
    val tips = remember {
        listOf(
            Tip(Icons.Filled.MenuBook, Color(0xFF007AFF), "BahiKhata Pro me swagat 🙏", "Customer jodein, Diye/Mile likhein — hisaab apne aap banta hai. Internet ke bina bhi chalta hai."),
            Tip(Icons.Filled.Bolt, Color(0xFFFF9500), "Jaldi entry ⚡", "Home par ⚡ button dabayein: party, rakam aur Diye/Mile — ek hi jagah, 3 second me."),
            Tip(Icons.Filled.SwipeRight, Color(0xFF34C759), "Swipe karein", "Party ko daayein khiskayein = WhatsApp reminder. Baayein = Call / Edit."),
            Tip(Icons.Filled.FilterList, Color(0xFF5856D6), "Filter aur sort", "Lena hai, Dena hai, Barabar, Due nikli — ek tap me sahi log. Sabko reminder bhi ek saath."),
        )
    }
    val pager = rememberPagerState { tips.size }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(24.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(ios.sheetBg).padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalPager(pager, Modifier.fillMaxWidth().height(250.dp)) { pg ->
                val t = tips[pg]
                Column(Modifier.fillMaxSize().padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(84.dp).clip(RoundedCornerShape(22.dp)).background(t.color), contentAlignment = Alignment.Center) {
                        Icon(t.icon, null, Modifier.size(44.dp), tint = Color.White)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(t.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ios.label, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(t.text, fontSize = 15.sp, color = ios.secondaryLabel, textAlign = TextAlign.Center)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(tips.size) { d ->
                    val on = pager.currentPage == d
                    val c by animateColorAsState(if (on) ios.tint else ios.tertiaryLabel, label = "dot")
                    Box(Modifier.height(7.dp).width(if (on) 20.dp else 7.dp).clip(CircleShape).background(c))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDone) { Text("Skip", fontSize = 16.sp, color = ios.secondaryLabel) }
                Spacer(Modifier.weight(1f))
                val last = pager.currentPage == tips.size - 1
                Box(Modifier.clip(CircleShape).background(ios.tint).clickable {
                    if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }.padding(horizontal = 22.dp, vertical = 10.dp)) {
                    Text(if (last) "Shuru karein" else "Aage", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Party row with swipe actions: swipe right = WhatsApp reminder, swipe left = more (call / edit).
 * The row springs back after the action, so nothing is ever removed by a swipe.
 */
@Composable
fun SwipeActionsRow(
    index: Int,
    count: Int,
    enabled: Boolean,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!enabled) { Box(modifier) { content() }; return }
    val haptic = rememberHaptic()
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> { haptic(); onSwipeRight() }
                SwipeToDismissBoxValue.EndToStart -> { haptic(); onSwipeLeft() }
                else -> {}
            }
            false // always spring back
        },
        positionalThreshold = { it * 0.3f }
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val dir = state.dismissDirection
            val right = dir == SwipeToDismissBoxValue.StartToEnd
            val bg = when (dir) { SwipeToDismissBoxValue.StartToEnd -> GotGreen; SwipeToDismissBoxValue.EndToStart -> ios.tint; else -> Color.Transparent }
            val scale by animateFloatAsState(if (state.targetValue != SwipeToDismissBoxValue.Settled) 1.2f else 0.9f, label = "swipeIcon")
            Row(Modifier.fillMaxSize().clip(groupShape(index, count)).background(bg).padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (right) Arrangement.Start else Arrangement.End) {
                if (dir != SwipeToDismissBoxValue.Settled) {
                    Icon(if (right) Icons.Filled.Notifications else Icons.Filled.MoreHoriz, null, Modifier.scale(scale), tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(if (right) "Reminder" else "Call / Edit", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { content() }
}
