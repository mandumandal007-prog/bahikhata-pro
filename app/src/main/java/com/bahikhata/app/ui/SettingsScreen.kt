@file:OptIn(ExperimentalMaterial3Api::class)

package com.bahikhata.app.ui

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            SectionCard("Cloud Account (login)") {
                when {
                    !Cloud.configured -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CloudOff, null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(8.dp))
                            Text("Cloud login setup pending", fontWeight = FontWeight.Bold)
                        }
                        Text("Abhi data sirf is phone me hai. Neeche 'Backup' se file bana kar rakhein.", style = MaterialTheme.typography.bodySmall)
                    }
                    Cloud.uid == null -> {
                        Text("Login karne se data cloud me safe rahega — app delete ho ya phone badle, login karke wapas milega.")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { nav.go(Screen.Auth) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Login, null); Spacer(Modifier.width(8.dp)); Text("Login / Signup")
                        }
                    }
                    else -> {
                        KeyValue("Account", Cloud.email ?: "")
                        KeyValue("Last sync", if (Cloud.lastSync > 0) fmtDateTime(Cloud.lastSync) else "—")
                        if (Cloud.status.isNotBlank()) Text(Cloud.status, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { scope.launch { Cloud.push() } }, enabled = !Cloud.syncing, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.CloudUpload, null); Spacer(Modifier.width(6.dp)); Text("Sync now")
                            }
                            OutlinedButton(onClick = { cloudRestore = true }, enabled = !Cloud.syncing, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.CloudDownload, null); Spacer(Modifier.width(6.dp)); Text("Restore")
                            }
                        }
                        TextButton(onClick = { logout = true }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, null); Spacer(Modifier.width(6.dp)); Text("Logout")
                        }
                    }
                }
            }

            SectionCard("Dukan ki jaankari (bill/PDF par chhapegi)") {
                TxtField(business, { business = it }, "Dukan / business ka naam", Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                TxtField(phone, { phone = it }, "Mobile", Modifier.fillMaxWidth(), KeyboardType.Phone)
                Spacer(Modifier.height(6.dp))
                TxtField(address, { address = it }, "Pata", Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                TxtField(gstin, { gstin = it.uppercase() }, "GSTIN (optional)", Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    Repo.setSetting("business", business.trim().ifEmpty { "Meri Dukan" })
                    Repo.setSetting("phone", phone.trim())
                    Repo.setSetting("address", address.trim())
                    Repo.setSetting("gstin", gstin.trim())
                    toast("Save ho gaya ✓")
                }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            }

            SectionCard("Dikhawat (Theme)") {
                ChipsRow(listOf("System", "Light", "Dark"), theme.replaceFirstChar { it.uppercase() },
                    { Repo.setSetting("theme", it.lowercase()) })
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Wallpaper ke colours (Material You)", Modifier.weight(1f))
                        Switch(checked = dynamic, onCheckedChange = { Repo.setSetting("dynamic", if (it) "1" else "0") })
                    }
                }
            }

            SectionCard("Suraksha (PIN lock)") {
                Text(if (hasPin) "PIN lock ON hai — app kholne par PIN maanga jayega" else "PIN lock OFF hai")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { pinDialog = true }) { Text(if (hasPin) "PIN badlein" else "PIN lagayein") }
                    if (hasPin) OutlinedButton(onClick = { Repo.setSetting("pin", ""); toast("PIN hata diya") }) { Text("PIN hatayein") }
                }
            }

            SectionCard("Backup / Restore (JSON file)") {
                ClickRow(Icons.Filled.SaveAlt, "Backup file save karein", "Phone/Drive me .json file") {
                    exportLauncher.launch("bahikhata-backup-$stamp.json")
                }
                ClickRow(Icons.Filled.Share, "Backup share karein", "WhatsApp / Gmail / Drive par bhejein") {
                    try {
                        val f = File(ctx.cacheDir, "bahikhata-backup-$stamp.json")
                        f.writeText(Repo.exportJson().toString(2))
                        shareFile(ctx, f, "application/json", "Backup bhejein")
                    } catch (e: Exception) { toast("Backup nahi bana") }
                }
                ClickRow(Icons.Filled.Restore, "Backup se restore karein", "Purani .json file chunein") {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                }
            }

            SectionCard("Khatarnak") {
                ClickRow(Icons.Filled.DeleteForever, "Saara data delete karein", "Parties, entries, cashbook, stock, bills") { wipe = true }
            }

            Text("BahiKhata Pro v${BuildConfig.VERSION_NAME}", Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pinDialog) {
        var p1 by remember { mutableStateOf("") }
        var p2 by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { pinDialog = false },
            title = { Text("4 ankon ka PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(p1, { v -> p1 = v.filter { it.isDigit() }.take(4) }, label = { Text("Naya PIN") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    OutlinedTextField(p2, { v -> p2 = v.filter { it.isDigit() }.take(4) }, label = { Text("PIN dobara") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
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
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { pinDialog = false }) { Text("Cancel") } }
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
        AlertDialog(
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
                }) { Text("Logout + data hatao", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    logout = false
                    scope.launch { Cloud.push(); Cloud.signOut(); toast("Logout ho gaya") }
                }) { Text("Sirf logout") }
            }
        )
    }
}
