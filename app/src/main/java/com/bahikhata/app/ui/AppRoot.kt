@file:OptIn(ExperimentalMaterial3Api::class)

package com.bahikhata.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
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

enum class Tab(val label: String, val icon: ImageVector) {
    KHATA("Khata", Icons.Filled.MenuBook),
    CASH("Cashbook", Icons.Filled.AccountBalanceWallet),
    STOCK("Stock", Icons.Filled.Inventory2),
    BILL("Bill", Icons.AutoMirrored.Filled.ReceiptLong),
    REPORTS("Reports", Icons.Filled.BarChart)
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Tabs)
    var tab by mutableStateOf(Tab.KHATA)
    val current: Screen get() = stack.last()
    fun go(s: Screen) { stack.add(s) }
    fun back(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
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

    when (val s = nav.current) {
        is Screen.PartyDetail -> PartyScreen(s.id, nav)
        Screen.NewInvoice -> InvoiceCreateScreen(nav)
        Screen.Tools -> ToolsScreen(nav)
        Screen.Settings -> SettingsScreen(nav)
        Screen.Auth -> AuthScreen(firstLaunch = false, onDone = { nav.back() })
        Screen.Tabs -> TabsScaffold(nav)
    }
}

@Composable
fun TabsScaffold(nav: Nav) {
    @Suppress("UNUSED_VARIABLE") val t = Repo.tick
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(Repo.businessName(), fontWeight = FontWeight.Bold)
                        Text(nav.tab.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (Cloud.uid != null) {
                        Icon(
                            if (Cloud.syncing) Icons.Filled.CloudSync else Icons.Filled.CloudDone, "Cloud",
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    IconButton(onClick = { nav.go(Screen.Tools) }) { Icon(Icons.Filled.Calculate, "Calculators") }
                    IconButton(onClick = { nav.go(Screen.Settings) }) { Icon(Icons.Filled.Settings, "Settings") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = nav.tab == tab,
                        onClick = { nav.tab = tab },
                        icon = { Icon(tab.icon, tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
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
