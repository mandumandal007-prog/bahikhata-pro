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

sealed class Screen {
    data object Tabs : Screen()
    data class PartyDetail(val id: Long) : Screen()
    data object NewInvoice : Screen()
    data object Tools : Screen()
    data object Settings : Screen()
    data object Auth : Screen()
}

enum class Tab(val label: String, val icon: ImageVector, val outline: ImageVector, val title: String) {
    KHATA("Khata", Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook, "Khata"),
    CASH("Cashbook", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, "Cashbook"),
    STOCK("Stock", Icons.Filled.Inventory2, Icons.Outlined.Inventory2, "Stock"),
    BILL("Bill", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong, "Bills"),
    REPORTS("Reports", Icons.Filled.BarChart, Icons.Outlined.BarChart, "Reports")
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Tabs)
    var tab by mutableStateOf(Tab.KHATA)
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
        null -> "Back"
    }
}

@Composable
fun AppRoot() {
    val tick = Repo.tick
    val pin = remember(tick) { Repo.setting("pin", "") }
    var locked by remember { mutableStateOf(Repo.setting("pin", "").isNotEmpty()) }
    var showAuth by remember { mutableStateOf(!Cloud.onboarded && Cloud.uid == null) }

    // Lock again when the app was in the background for more than a minute.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        var stoppedAt = 0L
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> stoppedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > 60_000 &&
                    Repo.setting("pin", "").isNotEmpty()) locked = true
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    // Auto cloud sync: a few seconds after any change.
    val uid = Cloud.uid
    LaunchedEffect(tick, uid) {
        if (uid != null && Cloud.configured) {
            delay(3000)
            Cloud.push()
        }
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
                Tab.REPORTS -> ReportsScreen()
            }
        }
    }
}

/** Translucent iOS tab bar with outline icons; the selected tab is filled and tinted. */
@Composable
fun IosTabBar(nav: Nav) {
    val tap = rememberHaptic()
    Column(Modifier.fillMaxWidth().background(ios.barBg)) {
        Hairline()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(54.dp)) {
            Tab.entries.forEach { tab ->
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
                    Spacer(Modifier.height(2.dp))
                    Text(tab.label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color, maxLines = 1)
                }
            }
        }
    }
}
