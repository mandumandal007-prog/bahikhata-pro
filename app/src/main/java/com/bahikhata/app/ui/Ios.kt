@file:OptIn(ExperimentalMaterial3Api::class)

package com.bahikhata.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bahikhata.app.util.PartyPhotos
import com.bahikhata.app.util.UiPrefs
import kotlin.math.roundToInt

// ---------------------------------------------------------------- iOS palette

@Immutable
data class IosColors(
    val dark: Boolean,
    val tint: Color,
    val groupedBg: Color,
    val cell: Color,
    val sheetBg: Color,
    val sheetCell: Color,
    val alertBg: Color,
    val separator: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val fill: Color,
    val fieldOnCell: Color,
    val segTrack: Color,
    val segThumb: Color,
    val barBg: Color,
    val gray: Color,
)

val IosRed = Color(0xFFFF3B30)
val IosGreen = Color(0xFF34C759)
val IosOrange = Color(0xFFFF9500)
val IosBlue = Color(0xFF007AFF)

fun accentColor(name: String, dark: Boolean): Color = when (name) {
    "green" -> if (dark) Color(0xFF30D158) else Color(0xFF34C759)
    "orange" -> if (dark) Color(0xFFFF9F0A) else Color(0xFFFF9500)
    "purple" -> if (dark) Color(0xFFBF5AF2) else Color(0xFFAF52DE)
    "pink" -> if (dark) Color(0xFFFF375F) else Color(0xFFFF2D55)
    else -> if (dark) Color(0xFF0A84FF) else Color(0xFF007AFF)
}

fun iosColors(dark: Boolean, tint: Color) = if (!dark) IosColors(
    dark = false, tint = tint,
    groupedBg = Color(0xFFF2F2F7), cell = Color.White, sheetBg = Color(0xFFF2F2F7), sheetCell = Color.White,
    alertBg = Color(0xFFF4F4F6), separator = Color(0xFFC6C6C8), label = Color.Black,
    secondaryLabel = Color(0x993C3C43), tertiaryLabel = Color(0x4D3C3C43), fill = Color(0x1F767680),
    fieldOnCell = Color(0xFFEFEFF4), segTrack = Color(0x1F767680), segThumb = Color.White,
    barBg = Color(0xF2F9F9F9), gray = Color(0xFF8E8E93)
) else IosColors(
    dark = true, tint = tint,
    groupedBg = Color.Black, cell = Color(0xFF1C1C1E), sheetBg = Color(0xFF1C1C1E), sheetCell = Color(0xFF2C2C2E),
    alertBg = Color(0xFF2C2C2E), separator = Color(0xFF38383A), label = Color.White,
    secondaryLabel = Color(0x99EBEBF5), tertiaryLabel = Color(0x4DEBEBF5), fill = Color(0x3D767680),
    fieldOnCell = Color(0xFF2C2C2E), segTrack = Color(0x3D767680), segThumb = Color(0xFF636366),
    barBg = Color(0xF0161618), gray = Color(0xFF8E8E93)
)

val LocalIos = staticCompositionLocalOf { iosColors(false, IosBlue) }
/** true while drawing inside a white grouped cell (fields then use a gray fill). */
val LocalOnCell = compositionLocalOf { false }

val ios: IosColors
    @Composable @ReadOnlyComposable get() = LocalIos.current

@Composable
fun rememberHaptic(): () -> Unit {
    val h = LocalHapticFeedback.current
    return remember(h) { { if (UiPrefs.haptics) h.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}

// ---------------------------------------------------------------- nav bar

/**
 * iOS navigation bar. With [behavior] it shows a 34sp large title that scrolls away and
 * a small centred title that fades in, like UINavigationBar.prefersLargeTitles.
 */
@Composable
fun IosNavBar(
    title: String,
    behavior: TopAppBarScrollBehavior? = null,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val largePx = with(density) { 52.dp.toPx() }
    if (behavior != null) {
        SideEffect { if (behavior.state.heightOffsetLimit != -largePx) behavior.state.heightOffsetLimit = -largePx }
    }
    val offset = behavior?.state?.heightOffset ?: 0f
    val fraction = if (behavior == null) 1f else (-offset / largePx).coerceIn(0f, 1f)
    val bg = if (fraction > 0.98f) ios.barBg else ios.groupedBg
    Column(Modifier.fillMaxWidth().background(bg).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(44.dp)) {
            Row(Modifier.align(Alignment.CenterStart).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBackIos, "Back", tint = ios.tint, modifier = Modifier.size(20.dp))
                        Text(backLabel ?: "Back", color = ios.tint, fontSize = 17.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 110.dp))
                    }
                }
                leading?.invoke(this)
            }
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 120.dp).alpha(fraction),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null && behavior == null) Text(subtitle, fontSize = 11.sp, color = ios.secondaryLabel, maxLines = 1)
            }
            CompositionLocalProvider(LocalContentColor provides ios.tint) {
                Row(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
        if (behavior != null) {
            val h = with(density) { (largePx + offset).coerceAtLeast(0f).toDp() }
            Box(Modifier.fillMaxWidth().height(h).clipToBounds()) {
                Column(Modifier.offset { IntOffset(0, offset.roundToInt()) }.padding(start = 16.dp, end = 16.dp)) {
                    if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = ios.secondaryLabel, maxLines = 1,
                        fontWeight = FontWeight.SemiBold)
                    Text(title, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = ios.label, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, letterSpacing = 0.2.sp, lineHeight = 38.sp)
                }
            }
        }
        if (fraction > 0.98f) Hairline()
    }
}

@Composable
fun rememberLargeTitle(): TopAppBarScrollBehavior =
    TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

@Composable
fun Hairline(modifier: Modifier = Modifier, start: androidx.compose.ui.unit.Dp = 0.dp) {
    Box(modifier.fillMaxWidth().padding(start = start).height(0.5.dp).background(ios.separator))
}

@Composable
fun NavIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, desc, tint = ios.tint, modifier = Modifier.size(24.dp)) }
}

// ---------------------------------------------------------------- grouped lists

private val R = 12.dp

fun groupShape(index: Int, count: Int): Shape = when {
    count <= 1 -> RoundedCornerShape(R)
    index == 0 -> RoundedCornerShape(topStart = R, topEnd = R)
    index == count - 1 -> RoundedCornerShape(bottomStart = R, bottomEnd = R)
    else -> RoundedCornerShape(0.dp)
}

/** One row of an inset-grouped list. Draws its own rounded corners and inset hairline. */
@Composable
fun GroupCell(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    separatorInset: androidx.compose.ui.unit.Dp = 16.dp,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
    content: @Composable RowScope.() -> Unit
) {
    val sep = ios.separator
    val density = LocalDensity.current
    val insetPx = with(density) { separatorInset.toPx() }
    val last = index >= count - 1
    Row(
        modifier.fillMaxWidth().clip(groupShape(index, count)).background(ios.cell)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .drawBehind {
                if (!last) drawLine(sep, Offset(insetPx, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
            }
            .heightIn(min = 44.dp)
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically
    ) { CompositionLocalProvider(LocalOnCell provides true) { content() } }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        fontSize = 13.sp, color = ios.secondaryLabel, letterSpacing = 0.1.sp)
}

@Composable
fun SectionFooter(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp), fontSize = 13.sp, color = ios.secondaryLabel)
}

@Composable
fun Chevron() {
    Icon(Icons.Filled.ChevronRight, null, tint = ios.tertiaryLabel, modifier = Modifier.size(22.dp))
}

/** Settings-style rounded square icon. */
@Composable
fun IconTile(icon: ImageVector, color: Color) {
    Box(Modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

// ---------------------------------------------------------------- controls

@Composable
fun IosSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val tap = rememberHaptic()
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, tween(200), label = "sw")
    val track = if (checked) IosGreen else ios.fill.copy(alpha = if (ios.dark) 0.5f else 0.16f)
    Box(
        modifier.alpha(if (enabled) 1f else 0.45f).size(51.dp, 31.dp).clip(CircleShape).background(track)
            .clickable(enabled = enabled && onCheckedChange != null, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                tap(); onCheckedChange?.invoke(!checked)
            }
            .padding(2.dp)
    ) {
        Box(Modifier.offset(x = x).size(27.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun IosSegmented(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val tap = rememberHaptic()
    val idx = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(9.dp)).background(ios.segTrack).padding(2.dp)) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * idx, tween(220), label = "seg")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().shadow(1.5.dp, RoundedCornerShape(7.dp)).clip(RoundedCornerShape(7.dp)).background(ios.segThumb))
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, o ->
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (o != selected) { tap(); onSelect(o) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(o, fontSize = 13.sp, fontWeight = if (i == idx) FontWeight.SemiBold else FontWeight.Medium, color = ios.label,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

@Composable
fun IosPill(text: String, selected: Boolean, onClick: () -> Unit, color: Color = ios.tint) {
    Box(
        Modifier.clip(CircleShape).background(if (selected) color else ios.fill).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(text, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else ios.label, maxLines = 1)
    }
}

@Composable
fun IosPills(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val tap = rememberHaptic()
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> IosPill(o, o == selected, { tap(); onSelect(o) }) }
    }
}

/** iOS search bar. */
@Composable
fun IosSearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(10.dp)).background(ios.fill).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = ios.gray, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = ios.gray, fontSize = 17.sp, maxLines = 1)
            BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(fontSize = 17.sp, color = ios.label),
                cursorBrush = SolidColor(ios.tint), modifier = Modifier.fillMaxWidth())
        }
    }
}

/** iOS text field: caption above a rounded field. */
@Composable
fun IosField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    prefix: String? = null,
    suffix: String? = null,
    password: Boolean = false,
    placeholder: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val bg = if (LocalOnCell.current) ios.fieldOnCell else ios.sheetCell
    Column(modifier) {
        if (label.isNotEmpty()) Text(label, fontSize = 13.sp, color = ios.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (prefix != null) Text(prefix + " ", color = ios.secondaryLabel, fontSize = 17.sp)
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder != null) Text(placeholder, color = ios.tertiaryLabel, fontSize = 17.sp, maxLines = 1)
                BasicTextField(
                    value, onChange, singleLine = singleLine,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = ios.label),
                    cursorBrush = SolidColor(ios.tint),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (suffix != null) Text(" $suffix", color = ios.secondaryLabel, fontSize = 15.sp)
            trailing?.invoke()
        }
    }
}

// ---------------------------------------------------------------- buttons (shadow Material's, no elevation)

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(containerColor = ios.tint, contentColor = Color.White),
    shape: Shape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) = androidx.compose.material3.Button(
    onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled, shape = shape, colors = colors,
    elevation = null, contentPadding = contentPadding
) { ProvideTextStyle(TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)) { content() } }

/** iOS "tinted" button (light tint fill, tint text). */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    content: @Composable RowScope.() -> Unit
) = androidx.compose.material3.Button(
    onClick = onClick, modifier = modifier.heightIn(min = 46.dp), enabled = enabled, shape = shape,
    colors = ButtonDefaults.buttonColors(containerColor = ios.tint.copy(alpha = 0.13f), contentColor = ios.tint),
    elevation = null, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
) { ProvideTextStyle(TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)) { content() } }

/** Replaces Material's FAB with an iOS-style capsule button. */
@Composable
fun ExtendedFloatingActionButton(onClick: () -> Unit, icon: @Composable () -> Unit, text: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val tint = ios.tint
    Row(
        modifier.shadow(10.dp, CircleShape, ambientColor = tint, spotColor = tint).clip(CircleShape).background(tint)
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            icon(); Spacer(Modifier.width(6.dp))
            ProvideTextStyle(TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)) { text() }
        }
    }
}

// ---------------------------------------------------------------- alerts and sheets

/** Centered iOS alert, same slots as Material's AlertDialog. */
@Composable
fun IosAlert(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier.padding(horizontal = 36.dp).widthIn(max = 320.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ios.alertBg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CompositionLocalProvider(LocalOnCell provides true, LocalContentColor provides ios.label) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (icon != null) { icon(); Spacer(Modifier.height(8.dp)) }
                    if (title != null) ProvideTextStyle(TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = ios.label)) { title() }
                    if (text != null) {
                        Spacer(Modifier.height(6.dp))
                        ProvideTextStyle(TextStyle(fontSize = 14.sp, textAlign = TextAlign.Center, color = ios.label, lineHeight = 19.sp)) { text() }
                    }
                }
            }
            Hairline()
            ProvideTextStyle(TextStyle(fontSize = 17.sp, color = ios.tint)) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (dismissButton != null) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { dismissButton() }
                        Box(Modifier.width(0.5.dp).fillMaxHeight().background(ios.separator))
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        ProvideTextStyle(TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ios.tint)) { confirmButton() }
                    }
                }
            }
        }
    }
}

/** iOS form sheet: "Cancel   Title   Save" header over a grouped background. Same slots as AlertDialog. */
@Composable
fun IosSheet(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = state,
        containerColor = ios.sheetBg,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        tonalElevation = 0.dp,
        dragHandle = { Box(Modifier.padding(top = 6.dp).size(36.dp, 5.dp).clip(CircleShape).background(ios.tertiaryLabel)) },
    ) {
        Column(modifier.fillMaxWidth().imePadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.widthIn(min = 70.dp), contentAlignment = Alignment.CenterStart) {
                    ProvideTextStyle(TextStyle(fontSize = 17.sp, color = ios.tint)) { dismissButton?.invoke() }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    ProvideTextStyle(TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ios.label, textAlign = TextAlign.Center)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { icon?.invoke(); title?.invoke() }
                    }
                }
                Box(Modifier.widthIn(min = 70.dp), contentAlignment = Alignment.CenterEnd) {
                    ProvideTextStyle(TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ios.tint)) { confirmButton() }
                }
            }
            Hairline()
            Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp).navigationBarsPadding()) {
                ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(color = ios.label)) { text?.invoke() }
            }
        }
    }
}

data class SheetAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/** iOS action sheet: grouped options at the bottom and a separate Cancel button. */
@Composable
fun IosActionSheet(title: String?, actions: List<SheetAction>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(8.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ios.alertBg)) {
                    if (title != null) {
                        Text(title, Modifier.fillMaxWidth().padding(14.dp), textAlign = TextAlign.Center, fontSize = 13.sp,
                            color = ios.secondaryLabel, fontWeight = FontWeight.SemiBold)
                        Hairline()
                    }
                    actions.forEachIndexed { i, a ->
                        if (i > 0) Hairline()
                        Box(Modifier.fillMaxWidth().height(57.dp).clickable { a.onClick(); onDismiss() }, contentAlignment = Alignment.Center) {
                            Text(a.label, fontSize = 19.sp, color = if (a.destructive) IosRed else ios.tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(57.dp).clip(RoundedCornerShape(14.dp)).background(ios.alertBg).clickable { onDismiss() },
                    contentAlignment = Alignment.Center) {
                    Text("Cancel", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = ios.tint)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- avatars

@Composable
fun PartyAvatar(id: Long, name: String, size: androidx.compose.ui.unit.Dp = 42.dp) {
    val ctx = LocalContext.current
    val v = PartyPhotos.version
    val bmp = remember(id, v) { PartyPhotos.load(ctx, id) }
    if (bmp != null) {
        Image(bmp.asImageBitmap(), name, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
    } else InitialsAvatar(name, size)
}

@Composable
fun InitialsAvatar(name: String, size: androidx.compose.ui.unit.Dp = 42.dp) {
    val initials = name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFA5ABB8), Color(0xFF858A96)))),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.4f).sp)
    }
}
