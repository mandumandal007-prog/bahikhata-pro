@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bahikhata.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.ui.unit.sp
import com.bahikhata.app.util.UiPrefs
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bahikhata.app.util.fmtDate
import java.util.Calendar
import java.util.TimeZone

val GaveRed = Color(0xFFFF3B30)
val GotGreen = Color(0xFF34C759)

val PAY_MODES = listOf("CASH", "UPI", "BANK", "CARD", "CHEQUE")

fun String.toAmount(): Double = this.replace(",", "").replace("₹", "").trim().toDoubleOrNull()
    ?.takeIf { it.isFinite() && it >= 0.0 && it < 1_000_000_000_000.0 } ?: 0.0

/** Keeps only a clean amount: digits, one dot, 2 decimals, at most 11 digits before the dot. */
fun cleanAmountInput(v: String): String {
    val f = v.filter { it.isDigit() || it == '.' }
    val dot = f.indexOf('.')
    return if (dot < 0) f.take(11)
    else f.substring(0, dot).take(11) + "." + f.substring(dot + 1).replace(".", "").take(2)
}

fun Double.plain(): String =
    if (this == 0.0) "" else if (this % 1.0 == 0.0) this.toLong().toString() else this.toString()

@Composable
fun NumField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    prefix: String? = null
) {
    var calc by remember { mutableStateOf(false) }
    IosField(value, { v -> onChange(cleanAmountInput(v)) }, label, modifier,
        keyboard = KeyboardType.Decimal, prefix = prefix?.let { if (it == "₹") com.bahikhata.app.util.NumFmt.symbol else it }, suffix = suffix,
        placeholder = "0",
        trailing = if (remember { com.bahikhata.app.data.Repo.setting("calc_in_amount", "1") == "1" }) ({
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Calculate, "Calculator",
                Modifier.padding(start = 6.dp).size(22.dp).clickable { calc = true }, tint = ios.tint)
        }) else null)
    if (calc) CalcDialog(value, onDismiss = { calc = false }) { r -> onChange(cleanAmountInput(r)); calc = false }
}

@Composable
fun TxtField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true
) {
    IosField(value, onChange, label, modifier, keyboard = keyboard, singleLine = singleLine)
}

/** Converts a DatePicker UTC-midnight value to local time (keeping the current time of day). */
fun utcDateToLocal(utc: Long, keepTimeFrom: Long = System.currentTimeMillis()): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
    val l = Calendar.getInstance().apply {
        timeInMillis = keepTimeFrom
        set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
    }
    return l.timeInMillis
}

fun localToUtcDate(local: Long): Long {
    val l = Calendar.getInstance().apply { timeInMillis = local }
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH))
    }
    return u.timeInMillis
}

@Composable
fun DateField(
    label: String,
    millis: Long?,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    clearable: Boolean = false
) {
    var open by remember { mutableStateOf(false) }
    val bg = if (LocalOnCell.current) ios.fieldOnCell else ios.sheetCell
    Column(modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = ios.secondaryLabel, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp), maxLines = 1)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(bg).clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.CalendarMonth, null, tint = ios.tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(millis?.let { fmtDate(it) } ?: "Select karein", Modifier.weight(1f), fontSize = 17.sp,
                color = if (millis != null) ios.tint else ios.tertiaryLabel, maxLines = 1)
            if (clearable && millis != null) {
                Icon(Icons.Filled.Cancel, "Hatao", tint = ios.tertiaryLabel, modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onChange(null) })
            }
        }
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = localToUtcDate(millis ?: System.currentTimeMillis()))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(utcDateToLocal(it)) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

@Composable
fun ChipsRow(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val maxLen = options.maxOfOrNull { it.length } ?: 0
    val segmented = (options.size <= 3 && maxLen <= 16) || (options.size <= 5 && maxLen <= 4) || (options.size == 4 && maxLen <= 8)
    if (segmented) IosSegmented(options, selected, onSelect, modifier)
    else IosPills(options, selected, onSelect, modifier)
}

@Composable
fun Avatar(name: String, color: Color = Color.Unspecified) {
    InitialsAvatar(name, 40.dp)
}

@Composable
fun StatBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    var reveal by remember { mutableStateOf(false) }
    val hidden = UiPrefs.hideAmounts && !reveal && value.any { it.isDigit() } && value.startsWith(com.bahikhata.app.util.NumFmt.symbol)
    Column(modifier.padding(4.dp).then(if (UiPrefs.hideAmounts) Modifier.clickable { reveal = !reveal } else Modifier)) {
        Text(label, fontSize = 13.sp, color = ios.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (hidden) com.bahikhata.app.util.NumFmt.symbol + " ••••" else value, fontSize = 21.sp, color = color,
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = (-0.3).sp)
    }
}

/** iOS inset-grouped section: small caps header outside, rounded white card inside. */
@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) Text(com.bahikhata.app.util.tr(title).uppercase(), Modifier.padding(start = 16.dp, bottom = 6.dp, top = 4.dp), fontSize = 13.sp,
            color = ios.secondaryLabel, maxLines = 2)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ios.cell).padding(16.dp)) {
            CompositionLocalProvider(LocalOnCell provides true) { content() }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, Modifier.size(52.dp), tint = ios.tertiaryLabel)
        Spacer(Modifier.height(12.dp))
        Text(text, color = ios.secondaryLabel, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "Haan",
    danger: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    IosAlert(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (danger) IosRed else ios.tint, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontSize = 17.sp) } }
    )
}

@Composable
fun KeyValue(label: String, value: String, bold: Boolean = false, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), color = ios.secondaryLabel)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color)
    }
}

@Composable
fun BigButton(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        shape = RoundedCornerShape(14.dp)
    ) { Text(text, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
}

@Composable
fun ClickRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, ios.tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, color = ios.label)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = ios.secondaryLabel)
        }
        Chevron()
    }
}
