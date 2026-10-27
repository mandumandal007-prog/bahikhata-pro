@file:OptIn(ExperimentalMaterial3Api::class)

package com.bahikhata.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bahikhata.app.data.Cloud
import com.bahikhata.app.util.S
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import com.bahikhata.app.data.Repo
import kotlinx.coroutines.launch

/** Login / Signup / Forgot password. Shown on first launch and from Settings. */
@Composable
fun AuthScreen(firstLaunch: Boolean, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var signup by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var askRestore by remember { mutableStateOf(false) }

    fun finish() {
        Cloud.authFlow = false
        Cloud.onboarded = true
        onDone()
    }
    DisposableEffect(Unit) { onDispose { Cloud.authFlow = false } }

    /** After login: decide what to do with cloud vs phone data. */
    suspend fun afterLogin(isNew: Boolean) {
        if (isNew) {
            Cloud.push(); finish(); return
        }
        info = "Cloud data check ho raha hai…"
        val at = Cloud.cloudUpdatedAt()
        when {
            at == null -> { Cloud.push(); finish() }
            at == 0L -> { Cloud.push(); finish() }
            Cloud.localIsEmpty() -> {
                info = "Aapka data wapas aa raha hai…"
                val err = Cloud.pull()
                if (err != null) { msg = err; info = null } else finish()
            }
            else -> { info = null; askRestore = true }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (!firstLaunch) IosNavBar("Cloud Account", backLabel = "Settings", onBack = onDone)
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(if (firstLaunch) 32.dp else 8.dp))
            Box(
                Modifier.size(88.dp).clip(RoundedCornerShape(20.dp)).background(ios.tint),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(48.dp), tint = Color.White) }
            Spacer(Modifier.height(16.dp))
            Text("BahiKhata Pro", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = ios.label)
            Text(
                "Login karein — phone badlein ya app dobara install karein, data wapas mil jayega",
                textAlign = TextAlign.Center, fontSize = 15.sp,
                color = ios.secondaryLabel
            )
            Spacer(Modifier.height(24.dp))

            if (!Cloud.configured) {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CloudOff, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(10.dp))
                        Text("Cloud login setup pending", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Abhi app offline chalega — sab data isi phone me save hoga. Cloud login chalu hone ke baad " +
                            "naya update install karein, phir yahan login karke data cloud me safe kar sakte hain. " +
                            "Tab tak Settings → Backup se JSON backup bana kar rakhein."
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Aage badhein") }
                return@Column
            }

            if (Cloud.uid != null && !firstLaunch) {
                SectionCard {
                    Text("Login hai: ${Cloud.email ?: ""}", fontWeight = FontWeight.Bold)
                    Text(Cloud.status.ifBlank { "Data apne aap cloud me save hota hai" })
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Theek hai") }
                return@Column
            }

            Row(Modifier.fillMaxWidth()) {
                SegmentedButtonRowCompat(signup) { signup = it; msg = null }
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ios.cell)) {
                CompositionLocalProvider(LocalOnCell provides false) {
                    AuthRow(Icons.Filled.Email) {
                        IosField(email, { email = it.trim() }, "", placeholder = "Email", keyboard = KeyboardType.Email)
                    }
                    Hairline(start = 48.dp)
                    AuthRow(Icons.Filled.Lock) {
                        IosField(pass, { pass = it }, "", placeholder = "Password", keyboard = KeyboardType.Password, password = !showPass,
                            trailing = {
                                Icon(if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Dikhao", tint = ios.gray,
                                    modifier = Modifier.size(22.dp).clickable { showPass = !showPass })
                            })
                    }
                    if (signup) {
                        Hairline(start = 48.dp)
                        AuthRow(Icons.Filled.Lock) {
                            IosField(pass2, { pass2 = it }, "", placeholder = "Password dobara", keyboard = KeyboardType.Password, password = !showPass)
                        }
                    }
                }
            }
            msg?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            info?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                enabled = !busy,
                onClick = {
                    msg = null
                    when {
                        !email.contains("@") -> msg = "Sahi email likhein"
                        pass.length < 6 -> msg = "Password kam se kam 6 akshar"
                        signup && pass != pass2 -> msg = "Dono password same nahi hain"
                        else -> scope.launch {
                            busy = true
                            Cloud.authFlow = true
                            val err = if (signup) Cloud.signUp(email, pass) else Cloud.signIn(email, pass)
                            if (err != null) { msg = err; Cloud.authFlow = false } else afterLogin(signup)
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text(if (signup) "Account banayein" else "Login", fontSize = 16.sp)
            }
            if (!signup) {
                TextButton(onClick = {
                    if (!email.contains("@")) { msg = "Pehle apna email likhein" } else scope.launch {
                        busy = true
                        val err = Cloud.resetPassword(email)
                        busy = false
                        if (err != null) msg = err else info = "Password reset link $email par bhej diya. Email check karein (spam bhi)."
                    }
                }) { Text("Password bhool gaye?") }
            }
            if (firstLaunch) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Abhi nahi — bina login chalayein")
                }
                Text(
                    "Baad me Settings → Cloud Account se login kar sakte hain",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (askRestore) {
        IosAlert(
            onDismissRequest = {},
            title = { Text("Cloud me purana data mila") },
            text = { Text("Is phone me bhi kuch data hai. Kya karein?\n\n• Cloud se laayein: phone ka data hat kar cloud wala aa jayega.\n• Phone wala rakhein: cloud par is phone ka data save hoga.") },
            confirmButton = {
                TextButton(onClick = {
                    askRestore = false
                    scope.launch {
                        busy = true
                        val err = Cloud.pull()
                        busy = false
                        if (err != null) msg = err else finish()
                    }
                }) { Text("Cloud se laayein", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    askRestore = false
                    scope.launch { busy = true; Cloud.push(); busy = false; finish() }
                }) { Text("Phone wala rakhein", fontSize = 15.sp) }
            }
        )
    }
}

@Composable
private fun SegmentedButtonRowCompat(signup: Boolean, onChange: (Boolean) -> Unit) {
    IosSegmented(listOf("Login", "Signup"), if (signup) "Signup" else "Login", { onChange(it == "Signup") })
}

@Composable
private fun AuthRow(icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = ios.gray, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalOnCell provides false) {
                // the field draws on the cell colour itself
                CompositionLocalProvider(LocalIos provides ios.copy(sheetCell = ios.cell)) { content() }
            }
        }
    }
}

/** 4-digit PIN lock screen. */
@Composable
fun PinScreen(pin: String, onUnlock: () -> Unit) {
    val scope = rememberCoroutineScope()
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var forgot by remember { mutableStateOf(false) }

    val act = LocalContext.current as? android.app.Activity
    val bio = remember { S.biometric && Build.VERSION.SDK_INT >= 28 }
    LaunchedEffect(Unit) { if (bio && act != null) biometricUnlock(act) { onUnlock() } }

    fun press(d: String) {
        if (entered.length >= pin.length) return
        entered += d
        error = false
        if (entered.length == pin.length) {
            if (entered == pin) onUnlock() else { error = true; entered = "" }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Lock, null, Modifier.size(40.dp), tint = ios.label)
            Spacer(Modifier.height(12.dp))
            Text(Repo.businessName(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = ios.label)
            Text(if (error) "Galat PIN, dobara daalein" else "PIN daalein",
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(pin.length) { i ->
                    Box(
                        Modifier.size(14.dp).clip(CircleShape)
                            .background(if (i < entered.length) ios.label else Color.Transparent)
                            .border(1.5.dp, ios.label, CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫"))
            rows.forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    r.forEach { k ->
                        if (k.isEmpty()) Spacer(Modifier.size(72.dp)) else
                            Box(
                                Modifier.size(78.dp).clip(CircleShape)
                                    .background(if (k == "⌫") Color.Transparent else ios.fill)
                                    .clickable { if (k == "⌫") entered = entered.dropLast(1) else press(k) },
                                contentAlignment = Alignment.Center
                            ) { Text(k, fontSize = if (k == "⌫") 22.sp else 34.sp, fontWeight = FontWeight.Light, color = ios.label) }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            if (bio && act != null) TextButton(onClick = { biometricUnlock(act) { onUnlock() } }) {
                Icon(Icons.Filled.Fingerprint, null); Spacer(Modifier.width(6.dp)); Text("Finger / face se kholein")
            }
            TextButton(onClick = { forgot = true }) { Text("PIN bhool gaye?") }
        }
    }

    if (forgot) {
        var pass by remember { mutableStateOf("") }
        var msg by remember { mutableStateOf<String?>(null) }
        IosAlert(
            onDismissRequest = { forgot = false },
            title = { Text("PIN bhool gaye") },
            text = {
                Column {
                    if (Cloud.uid != null) {
                        Text("Apne cloud account (${Cloud.email}) ka password daalein. PIN hat jayega.")
                        Spacer(Modifier.height(8.dp))
                        IosField(pass, { pass = it }, "", placeholder = "Password", password = true, keyboard = KeyboardType.Password)
                        msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    } else {
                        Text("Cloud login nahi hai, isliye PIN reset nahi ho sakta. Phone Settings → Apps → BahiKhata Pro → Storage → Clear data karne se PIN hatega (saara data bhi hat jayega, backup ho to restore karein).")
                    }
                }
            },
            confirmButton = {
                if (Cloud.uid != null) TextButton(onClick = {
                    scope.launch {
                        if (Cloud.verifyPassword(pass)) {
                            Repo.setSetting("pin", "")
                            forgot = false
                            onUnlock()
                        } else msg = "Password galat hai ya net nahi hai"
                    }
                }) { Text("PIN hatao") }
            },
            dismissButton = { TextButton(onClick = { forgot = false }) { Text("Band karein") } }
        )
    }
}


/** System fingerprint / face prompt (Android 9+). Calls onOk only on success; otherwise the PIN pad stays. */
fun biometricUnlock(activity: android.app.Activity, onOk: () -> Unit) {
    if (Build.VERSION.SDK_INT < 28) return
    try {
        val ex = activity.mainExecutor
        val prompt = android.hardware.biometrics.BiometricPrompt.Builder(activity)
            .setTitle("BahiKhata kholein")
            .setSubtitle("Finger ya face se unlock")
            .setNegativeButton("PIN daalein", ex) { _, _ -> }
            .build()
        prompt.authenticate(android.os.CancellationSignal(), ex,
            object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) { onOk() }
            })
    } catch (_: Throwable) {}
}
