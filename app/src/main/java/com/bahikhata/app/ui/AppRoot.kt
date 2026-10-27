@file:OptIn(ExperimentalMaterial3Api::class)

package com.bahikhata.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bahikhata.app.data.Cloud
import com.bahikhata.app.data.Repo
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import com.bahikhata.app.util.S

sealed class Screen {
    data object Tabs : Screen()
    data class PartyDetail(val id: Long) : Screen()
    data object NewInvoice : Screen()
    data object Tools : Screen()
    data object Settings : Screen()
    data object Auth : Screen()
    data class Prefs(val group: String) : Screen()
}

enum class Tab(val label: String, val icon: ImageVector, val outline: ImageVector, val title: String) {
    KHATA("Khata", Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook, "Khata"),
    CASH("Cashbook", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, "Cashbook"),
    STOCK("Stock", Icons.Filled.Inventory2, Icons.Outlined.Inventory2, "Stock"),
    BILL("Bill", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong, "Bills"),
    REPORTS("Reports", Icons.Filled.BarChart, Icons.Outlined.BarChart, "Reports")
}

/** Tabs the user has switched on (Settings → Tab bar). Khata is always shown. */
fun visibleTabs(): List<Tab> = Tab.entries.filter { S.tabShown(it.name) }

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Tabs)
    var tab by mutableStateOf(
        Tab.entries.firstOrNull { it.name == S.defaultTab && S.tabShown(it.name) } ?: Tab.KHATA
    )
    val current: Screen get() = stack.last()
    fun go(s: Screen) { stack.add(s) }
    fun back(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
    /** Title of the screen below the current one, for the iOS back button. */
    fun backLabel(): String = when (stack.getOrNull(stack.size - 2)) {
        Screen.Tabs -> tab.title
        Screen.Settings -> "Settings"
        Screen.Tools -> "Calculators"
        is Screen.PartyDetail -> "Party"
        Screen.NewInvoice -> "Bill"
        Screen.Auth -> "Account"
        is Screen.Prefs -> "Back"
        null -> "Back"
    }
}

@Composable
fun AppRoot() {
    val tick = Repo.tick
    val pin = remember(tick) { Repo.setting("pin", "") }
    var locked by remember { mutableStateOf(Repo.setting("pin", "").isNotEmpty()) }
    var showAuth by remember { mutableStateOf(!Cloud.onboarded && Cloud.uid == null) }

    // Lock again when the app was in the background longer than the chosen time (Settings → Security).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        var stoppedAt = 0L
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> stoppedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> {
                    val after = S.lockAfterSec
                    if (stoppedAt > 0 && after >= 0 && System.currentTimeMillis() - stoppedAt >= after * 1000 &&
                        Repo.setting("pin", "").isNotEmpty()) locked = true
                }
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    // Auto cloud sync: a few seconds after any change.
    val uid = Cloud.uid
    val authFlow = Cloud.authFlow
    val ctx = LocalContext.current
    LaunchedEffect(tick, uid, authFlow) {
        if (uid != null && Cloud.configured && !authFlow && S.autoSync) {
            delay(3000)
            if (!S.wifiOnly || S.onWifi(ctx)) Cloud.push()
        }
    }
    // Auto backup to the phone (Settings → Backup), checked once per app start.
    LaunchedEffect(Unit) {
        delay(4000)
        withContext(Dispatchers.IO) { S.autoBackupIfDue(ctx) }
    }

    when {
        locked && pin.isNotEmpty() -> PinScreen(pin) { locked = false }
        showAuth -> AuthScreen(firstLaunch = true, onDone = { showAuth = false })
        else -> MainShell()
    }
}

@Composable
fun MainShell() {
    val nav = remember { Nav() }
    BackHandler(enabled = nav.stack.size > 1) { nav.back() }
    BackHandler(enabled = nav.stack.size == 1 && nav.tab != Tab.KHATA) { nav.tab = Tab.KHATA }
    // a tab switched off in Settings can't stay selected
    val tabsNow = remember(Repo.tick) { visibleTabs() }
    LaunchedEffect(tabsNow) { if (nav.tab !in tabsNow) nav.tab = Tab.KHATA }

    // iOS push/pop: new screen slides in from the right, the old one moves a third to the left (parallax).
    AnimatedContent(
        targetState = nav.stack.size to nav.current,
        transitionSpec = {
            val forward = targetState.first > initialState.first
            val spec = tween<IntOffset>(340, easing = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f))
            val t = if (forward) {
                slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it / 3 }
            } else {
                slideInHorizontally(spec) { -it / 3 } togetherWith slideOutHorizontally(spec) { it }
            }
            t.apply { targetContentZIndex = if (forward) 1f else -1f }
        },
        label = "nav"
    ) { (_, s) ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (s) {
                is Screen.PartyDetail -> PartyScreen(s.id, nav)
                Screen.NewInvoice -> InvoiceCreateScreen(nav)
                Screen.Tools -> ToolsScreen(nav)
                Screen.Settings -> SettingsScreen(nav)
                Screen.Auth -> AuthScreen(firstLaunch = false, onDone = { nav.back() })
                is Screen.Prefs -> PrefsScreen(s.group, nav)
                Screen.Tabs -> TabsScaffold(nav)
            }
        }
    }
}

@Composable
fun TabsScaffold(nav: Nav) {
    @Suppress("UNUSED_VARIABLE") val t = Repo.tick
    val behavior = key(nav.tab) { rememberLargeTitle() }
    Scaffold(
        modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            IosNavBar(
                title = nav.tab.title,
                subtitle = Repo.businessName(),
                behavior = behavior,
                leading = {
                    if (Cloud.uid != null) {
                        Icon(
                            if (Cloud.syncing) Icons.Outlined.CloudSync else Icons.Outlined.CloudDone, "Cloud",
                            tint = ios.tint, modifier = Modifier.padding(start = 12.dp).size(22.dp)
                        )
                    }
                },
                actions = {
                    NavIcon(Icons.Outlined.Calculate, "Calculators") { nav.go(Screen.Tools) }
                    NavIcon(Icons.Outlined.Settings, "Settings") { nav.go(Screen.Settings) }
                }
            )
        },
        bottomBar = { IosTabBar(nav) }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (nav.tab) {
                Tab.KHATA -> KhataScreen(nav)
                Tab.CASH -> CashbookScreen()
                Tab.STOCK -> StockScreen()
                Tab.BILL -> InvoiceListScreen(nav)
                Tab.REPORTS -> ReportsScreen(nav)
            }
        }
    }
}

/** Tab bar: floating rounded pill (default) or the classic full-width iOS bar (Settings → Tab bar). */
@Composable
fun IosTabBar(nav: Nav) {
    val tick = Repo.tick
    val tabs = remember(tick) { visibleTabs() }
    val floating = remember(tick) { S.tabStyle != "classic" }
    val labels = remember(tick) { S.tabLabels }
    if (tabs.size <= 1) { Spacer(Modifier.navigationBarsPadding()); return }
    if (floating) FloatingTabBar(nav, tabs, labels) else ClassicTabBar(nav, tabs, labels)
}

@Composable
private fun ClassicTabBar(nav: Nav, tabs: List<Tab>, labels: Boolean) {
    val tap = rememberHaptic()
    Column(Modifier.fillMaxWidth().background(ios.barBg)) {
        Hairline()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(if (labels) 54.dp else 48.dp)) {
            tabs.forEach { tab ->
                val sel = nav.tab == tab
                val color = if (sel) ios.tint else ios.gray
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (!sel) { tap(); nav.tab = tab }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(if (sel) tab.icon else tab.outline, tab.label, tint = color, modifier = Modifier.size(25.dp))
                    if (labels) {
                        Spacer(Modifier.height(2.dp))
                        Text(tab.label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * Floating iOS-style pill: 28dp corners, inset from the screen edges, translucent bar colour,
 * hairline border and a soft shadow. The selected tab sits in a rounded tinted highlight.
 * It lives in the Scaffold's bottom slot, so screen content is padded above it and never hidden.
 */
@Composable
private fun FloatingTabBar(nav: Nav, tabs: List<Tab>, labels: Boolean) {
    val tap = rememberHaptic()
    val shape = RoundedCornerShape(28.dp)
    val barColor = if (ios.dark) Color(0xFF1C1C1E).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.94f)
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.fillMaxWidth()
                .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
                .clip(shape)
                .background(barColor)
                .border(0.5.dp, ios.separator, shape)
                .height(if (labels) 62.dp else 54.dp)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val sel = nav.tab == tab
                val color by animateColorAsState(if (sel) ios.tint else ios.gray, label = "tabc")
                val bg by animateColorAsState(if (sel) ios.tint.copy(alpha = if (ios.dark) 0.22f else 0.12f) else Color.Transparent, label = "tabbg")
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .clip(RoundedCornerShape(22.dp))
                        .background(bg)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (!sel) { tap(); nav.tab = tab }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(if (sel) tab.icon else tab.outline, tab.label, tint = color, modifier = Modifier.size(if (labels) 23.dp else 26.dp))
                    if (labels) {
                        Spacer(Modifier.height(1.dp))
                        Text(tab.label, fontSize = 10.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, color = color, maxLines = 1)
                    }
                }
            }
        }
    }
}
