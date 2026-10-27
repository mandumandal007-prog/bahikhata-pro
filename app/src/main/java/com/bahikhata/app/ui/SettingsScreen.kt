@file:OptIn(ExperimentalMaterial3Api::class)

package com.bahikhata.app.ui

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.bahikhata.app.util.UiPrefs
import com.bahikhata.app.util.rupees
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.bahikhata.app.BuildConfig
import com.bahikhata.app.data.Cloud
import com.bahikhata.app.data.Repo
import com.bahikhata.app.util.fmtDateTime
import com.bahikhata.app.util.shareFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick = Repo.tick
    var business by remember { mutableStateOf(Repo.setting("business", "Meri Dukan")) }
    var phone by remember { mutableStateOf(Repo.setting("phone", "")) }
    var address by remember { mutableStateOf(Repo.setting("address", "")) }
    var gstin by remember { mutableStateOf(Repo.setting("gstin", "")) }
    var terms by remember { mutableStateOf(Repo.setting("terms", "")) }
    var termsOn by remember { mutableStateOf(Repo.setting("terms_on", "1") == "1") }
    val theme = remember(tick) { Repo.setting("theme", "system") }
    val dynamic = remember(tick) { Repo.setting("dynamic", "0") == "1" }
    val hasPin = remember(tick) { Repo.setting("pin", "").isNotEmpty() }
    var pinDialog by remember { mutableStateOf(false) }
    var restoreJson by remember { mutableStateOf<JSONObject?>(null) }
    var wipe by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    var cloudRestore by remember { mutableStateOf(false) }

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ENGLISH).format(Date())

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(Repo.exportJson().toString(2).toByteArray()) }
                    true
                } catch (e: Exception) { false }
            }
            toast(if (ok) "Backup save ho gaya ✓" else "Backup save nahi hua")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val obj = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openInputStream(uri)?.use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
                } catch (e: Exception) { null }
            }
            if (obj == null || !obj.has("parties")) toast("Yeh BahiKhata backup file nahi hai") else restoreJson = obj
        }
    }

    val backLabel = remember { nav.backLabel() }
    val behavior = rememberLargeTitle()
    Scaffold(
        modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { IosNavBar("Settings", behavior, backLabel = backLabel, onBack = { nav.back() }) }
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {

            // ---- account card (like the Apple ID row)
            Spacer(Modifier.height(8.dp))
            GroupCell(0, 1, onClick = if (Cloud.configured && Cloud.uid == null) ({ nav.go(Screen.Auth) }) else null,
                padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Box(Modifier.size(58.dp).clip(CircleShape).background(ios.tint), contentAlignment = Alignment.Center) {
                    Icon(if (Cloud.uid != null) Icons.Filled.Cloud else Icons.Filled.CloudOff, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    when {
                        !Cloud.configured -> {
                            Text("Cloud login setup pending", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ios.label)
                            Text("Abhi data sirf is phone me hai. Neeche 'Backup' se file bana kar rakhein.", fontSize = 13.sp, color = ios.secondaryLabel)
                        }
                        Cloud.uid == null -> {
                            Text("Login / Signup", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ios.tint)
                            Text("Login karne se data cloud me safe rahega — app delete ho ya phone badle, login karke wapas milega.",
                                fontSize = 13.sp, color = ios.secondaryLabel)
                        }
                        else -> {
                            Text(Cloud.email ?: "", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ios.label, maxLines = 1)
                            Text("Last sync: " + (if (Cloud.lastSync > 0) fmtDateTime(Cloud.lastSync) else "—"), fontSize = 13.sp, color = ios.secondaryLabel)
                            if (Cloud.status.isNotBlank()) Text(Cloud.status, fontSize = 13.sp, color = ios.tint)
                        }
                    }
                }
                if (Cloud.configured && Cloud.uid == null) Chevron()
            }
            if (Cloud.uid != null) {
                Spacer(Modifier.height(16.dp))
                GroupCell(0, 3, onClick = { if (!Cloud.syncing) scope.launch { Cloud.push() } }) {
                    IconTile(Icons.Filled.CloudUpload, ios.tint); Spacer(Modifier.width(12.dp))
                    Text(if (Cloud.syncing) "Sync ho raha hai…" else "Sync now", Modifier.weight(1f), fontSize = 17.sp, color = ios.label)
                }
                GroupCell(1, 3, onClick = { if (!Cloud.syncing) cloudRestore = true }) {
                    IconTile(Icons.Filled.CloudDownload, IosOrange); Spacer(Modifier.width(12.dp))
                    Text("Cloud se restore", Modifier.weight(1f), fontSize = 17.sp, color = ios.label); Chevron()
                }
                GroupCell(2, 3, onClick = { logout = true }) {
                    IconTile(Icons.AutoMirrored.Filled.Logout, IosRed); Spacer(Modifier.width(12.dp))
                    Text("Logout", Modifier.weight(1f), fontSize = 17.sp, color = IosRed)
                }
            }

            // ---- shop
            SectionHeader("Dukan ki jaankari")
            SectionCard {
                TxtField(business, { business = it }, "Dukan / business ka naam", Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                TxtField(phone, { phone = it }, "Mobile", Modifier.fillMaxWidth(), KeyboardType.Phone)
                Spacer(Modifier.height(10.dp))
                TxtField(address, { address = it }, "Pata", Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                TxtField(gstin, { gstin = it.uppercase() }, "GSTIN (optional)", Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    Repo.setSetting("business", business.trim().ifEmpty { "Meri Dukan" })
                    Repo.setSetting("phone", phone.trim())
                    Repo.setSetting("address", address.trim())
                    Repo.setSetting("gstin", gstin.trim())
                    toast("Save ho gaya ✓")
                }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            }
            SectionFooter("Yeh jaankari bill aur PDF par chhapegi.")

            SectionHeader("Statement / Bill: Terms & Rules")
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PDF par dikhao", Modifier.weight(1f), fontSize = 17.sp)
                    IosSwitch(termsOn, { termsOn = it; Repo.setSetting("terms_on", if (it) "1" else "0") })
                }
                Spacer(Modifier.height(10.dp))
                TxtField(terms, { terms = it }, "Apne rules likhein (har line alag)", Modifier.fillMaxWidth().heightIn(min = 90.dp), singleLine = false)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { Repo.setSetting("terms", terms.trim()); toast("Terms save ho gaye ✓") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Terms save karein")
                }
            }
            SectionFooter("Party statement aur bill PDF ke neeche chhapenge. Backup me bhi jaate hain.")

            // ---- appearance
            SectionHeader("Dikhawat")
            SectionCard {
                Text("Theme", fontSize = 13.sp, color = ios.secondaryLabel); Spacer(Modifier.height(6.dp))
                IosSegmented(listOf("System", "Light", "Dark"), theme.replaceFirstChar { it.uppercase() }, { Repo.setSetting("theme", it.lowercase()) })
                Spacer(Modifier.height(14.dp))
                Text("Accent colour", fontSize = 13.sp, color = ios.secondaryLabel); Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    listOf("blue", "green", "orange", "purple", "pink").forEach { a ->
                        val c = accentColor(a, ios.dark)
                        Box(Modifier.size(34.dp).clip(CircleShape).background(c).clickable { UiPrefs.changeAccent(a) }, contentAlignment = Alignment.Center) {
                            if (UiPrefs.accent == a) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Text size", fontSize = 13.sp, color = ios.secondaryLabel); Spacer(Modifier.height(6.dp))
                val sizes = listOf("s" to "Chhota", "m" to "Normal", "l" to "Bada", "xl" to "XL")
                IosSegmented(sizes.map { it.second }, sizes.first { it.first == UiPrefs.textSize }.second,
                    { v -> UiPrefs.changeTextSize(sizes.first { it.second == v }.first) })
                Spacer(Modifier.height(14.dp))
                Text("Font style", fontSize = 13.sp, color = ios.secondaryLabel); Spacer(Modifier.height(6.dp))
                val fonts = listOf("system" to "System", "serif" to "Serif", "condensed" to "Patla", "mono" to "Mono")
                IosSegmented(fonts.map { it.second }, (fonts.firstOrNull { it.first == UiPrefs.fontStyle } ?: fonts[0]).second,
                    { v -> UiPrefs.changeFontStyle(fonts.first { it.second == v }.first) })
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Wallpaper ka colour (Material You)", Modifier.weight(1f), fontSize = 17.sp)
                        IosSwitch(checked = dynamic, onCheckedChange = { Repo.setSetting("dynamic", if (it) "1" else "0") })
                    }
                }
            }

            // ---- money format
            SectionHeader("Paisa format")
            SectionCard {
                Text("Currency", fontSize = 13.sp, color = ios.secondaryLabel); Spacer(Modifier.height(6.dp))
                IosSegmented(listOf("₹", "Rs.", "$", "€", "£"), UiPrefs.currency, { UiPrefs.changeCurrency(it) })
                Spacer(Modifier.height(14.dp))
                Text("Ginti ka tareeka", fontSize = 13.sp, color = ios.secondaryLabel); Spacer(Modifier.height(6.dp))
                IosSegmented(listOf("1,00,000", "100,000"), if (UiPrefs.indianGrouping) "1,00,000" else "100,000",
                    { UiPrefs.changeIndianGrouping(it == "1,00,000") })
            }
            SectionFooter("Example: ${rupees(125000.0)}")

            // ---- feel + privacy
            SectionHeader("Anubhav aur privacy")
            GroupCell(0, 3) {
                IconTile(Icons.Filled.Vibration, Color(0xFF5856D6)); Spacer(Modifier.width(12.dp))
                Text("Haptic (vibration) feedback", Modifier.weight(1f), fontSize = 17.sp, color = ios.label)
                IosSwitch(UiPrefs.haptics, { UiPrefs.changeHaptics(it) })
            }
            GroupCell(1, 3) {
                IconTile(Icons.Filled.VisibilityOff, ios.gray); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Amounts chhupao", fontSize = 17.sp, color = ios.label)
                    Text("Home ke totals •••• dikhenge, tap karke dekhein", fontSize = 12.sp, color = ios.secondaryLabel)
                }
                IosSwitch(UiPrefs.hideAmounts, { UiPrefs.changeHideAmounts(it) })
            }
            GroupCell(2, 3) {
                IconTile(Icons.Filled.Lock, IosRed); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("PIN lock", fontSize = 17.sp, color = ios.label)
                    Text(if (hasPin) "App kholne par PIN maanga jayega" else "Off", fontSize = 12.sp, color = ios.secondaryLabel)
                }
                IosSwitch(hasPin, { on -> if (on) pinDialog = true else { Repo.setSetting("pin", ""); toast("PIN hata diya") } })
            }
            if (hasPin) {
                Spacer(Modifier.height(8.dp))
                GroupCell(0, 1, onClick = { pinDialog = true }) {
                    Text("PIN badlein", Modifier.weight(1f), fontSize = 17.sp, color = ios.tint); Chevron()
                }
            }

            // ---- backup
            SectionHeader("Backup / Restore (JSON file)")
            GroupCell(0, 3, onClick = { exportLauncher.launch("bahikhata-backup-$stamp.json") }, separatorInset = 57.dp) {
                IconTile(Icons.Filled.SaveAlt, ios.tint); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Backup file save karein", fontSize = 17.sp, color = ios.label)
                    Text("Phone/Drive me .json file", fontSize = 12.sp, color = ios.secondaryLabel)
                }
                Chevron()
            }
            GroupCell(1, 3, onClick = {
                try {
                    val f = File(ctx.cacheDir, "bahikhata-backup-$stamp.json")
                    f.writeText(Repo.exportJson().toString(2))
                    shareFile(ctx, f, "application/json", "Backup bhejein")
                } catch (e: Exception) { toast("Backup nahi bana") }
            }, separatorInset = 57.dp) {
                IconTile(Icons.Filled.Share, IosGreen); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Backup share karein", fontSize = 17.sp, color = ios.label)
                    Text("WhatsApp / Gmail / Drive par bhejein", fontSize = 12.sp, color = ios.secondaryLabel)
                }
                Chevron()
            }
            GroupCell(2, 3, onClick = {
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
            }) {
                IconTile(Icons.Filled.Restore, IosOrange); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Backup se restore karein", fontSize = 17.sp, color = ios.label)
                    Text("Purani .json file chunein", fontSize = 12.sp, color = ios.secondaryLabel)
                }
                Chevron()
            }
            SectionFooter("Party photos sirf is phone me rehti hain (backup/cloud me nahi jaati).")

            Spacer(Modifier.height(24.dp))
            GroupCell(0, 1, onClick = { wipe = true }) {
                Text("Saara data delete karein", Modifier.weight(1f), fontSize = 17.sp, color = IosRed, textAlign = TextAlign.Center)
            }
            SectionFooter("Parties, entries, cashbook, stock, bills — sab hat jayega.")

            Spacer(Modifier.height(20.dp))
            Text("BahiKhata Pro v${BuildConfig.VERSION_NAME}", Modifier.align(Alignment.CenterHorizontally),
                fontSize = 13.sp, color = ios.secondaryLabel)
            Spacer(Modifier.height(32.dp))
        }
    }

    if (pinDialog) {
        var p1 by remember { mutableStateOf("") }
        var p2 by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        IosAlert(
            onDismissRequest = { pinDialog = false },
            title = { Text("4 ankon ka PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IosField(p1, { v -> p1 = v.filter { it.isDigit() }.take(4) }, "", placeholder = "Naya PIN",
                        password = true, keyboard = KeyboardType.NumberPassword)
                    IosField(p2, { v -> p2 = v.filter { it.isDigit() }.take(4) }, "", placeholder = "PIN dobara",
                        password = true, keyboard = KeyboardType.NumberPassword)
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text(if (Cloud.uid != null) "PIN bhoolne par cloud password se khol sakte hain." else
                        "Dhyan dein: PIN bhool gaye to bina cloud login ke reset nahi hoga.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        p1.length != 4 -> err = "4 ank daalein"
                        p1 != p2 -> err = "Dono PIN same nahi"
                        else -> { Repo.setSetting("pin", p1); pinDialog = false; toast("PIN lag gaya ✓") }
                    }
                }) { Text("Save", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { pinDialog = false }) { Text("Cancel", fontSize = 17.sp) } }
        )
    }
    restoreJson?.let { obj ->
        ConfirmDialog("Restore karein?", "Abhi ka saara data hat kar backup wala data aa jayega.", "Restore",
            onConfirm = { toast(if (Repo.importJson(obj)) "Restore ho gaya ✓" else "Restore fail hua") },
            onDismiss = { restoreJson = null })
    }
    if (wipe) ConfirmDialog("Saara data delete?", "Yeh wapas nahi aayega" + (if (Cloud.uid != null) " (cloud se bhi hat jayega jab sync hoga)." else "."),
        "Delete", onConfirm = { Repo.wipeAll(); toast("Data delete ho gaya") }, onDismiss = { wipe = false })
    if (cloudRestore) ConfirmDialog("Cloud se restore?", "Phone ka abhi ka data hat kar cloud me save data aa jayega.", "Restore",
        onConfirm = { scope.launch { val e = Cloud.pull(); toast(e ?: "Cloud se data aa gaya ✓") } },
        onDismiss = { cloudRestore = false })
    if (logout) {
        IosAlert(
            onDismissRequest = { logout = false },
            title = { Text("Logout") },
            text = { Text("Data cloud me safe hai. Logout ke baad is phone ka data rakhna hai ya hatana hai?") },
            confirmButton = {
                TextButton(onClick = {
                    logout = false
                    scope.launch {
                        Cloud.push()
                        Cloud.signOut()
                        Repo.wipeAll()
                        toast("Logout ho gaya, phone se data hata diya")
                    }
                }) { Text("Logout + data hatao", color = IosRed, fontSize = 15.sp) }
            },
            dismissButton = {
                TextButton(onClick = {
                    logout = false
                    scope.launch { Cloud.push(); Cloud.signOut(); toast("Logout ho gaya") }
                }) { Text("Sirf logout", fontSize = 15.sp) }
            }
        )
    }
}
