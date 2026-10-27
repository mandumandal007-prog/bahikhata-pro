@file:OptIn(ExperimentalMaterial3Api::class)

package com.bahikhata.app.ui

import androidx.compose.foundation.background
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
        Cloud.onboarded = true
        onDone()
    }

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
        topBar = {
            if (!firstLaunch) TopAppBar(
                title = { Text("Cloud Account") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(if (firstLaunch) 32.dp else 8.dp))
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.MenuBook, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(16.dp))
            Text("BahiKhata Pro", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Login karein — phone badlein ya app dobara install karein, data wapas mil jayega",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            OutlinedTextField(
                value = email, onValueChange = { email = it.trim() }, label = { Text("Email") },
                leadingIcon = { Icon(Icons.Filled.Email, null) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = pass, onValueChange = { pass = it }, label = { Text("Password") },
                leadingIcon = { Icon(Icons.Filled.Lock, null) }, singleLine = true,
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Dikhao")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            if (signup) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pass2, onValueChange = { pass2 = it }, label = { Text("Password dobara") },
                    leadingIcon = { Icon(Icons.Filled.Lock, null) }, singleLine = true,
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
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
                            val err = if (signup) Cloud.signUp(email, pass) else Cloud.signIn(email, pass)
                            if (err != null) msg = err else afterLogin(signup)
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
        AlertDialog(
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
                }) { Text("Cloud se laayein") }
            },
            dismissButton = {
                TextButton(onClick = {
                    askRestore = false
                    scope.launch { busy = true; Cloud.push(); busy = false; finish() }
                }) { Text("Phone wala rakhein") }
            }
        )
    }
}

@Composable
private fun SegmentedButtonRowCompat(signup: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(selected = !signup, onClick = { onChange(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Login") }
        SegmentedButton(selected = signup, onClick = { onChange(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Signup") }
    }
}

/** 4-digit PIN lock screen. */
@Composable
fun PinScreen(pin: String, onUnlock: () -> Unit) {
    val scope = rememberCoroutineScope()
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var forgot by remember { mutableStateOf(false) }

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
            Icon(Icons.Filled.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(Repo.businessName(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(if (error) "Galat PIN, dobara daalein" else "PIN daalein",
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(pin.length) { i ->
                    Box(
                        Modifier.size(18.dp).clip(CircleShape)
                            .background(if (i < entered.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫"))
            rows.forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    r.forEach { k ->
                        if (k.isEmpty()) Spacer(Modifier.size(72.dp)) else
                            FilledTonalButton(
                                onClick = { if (k == "⌫") entered = entered.dropLast(1) else press(k) },
                                modifier = Modifier.size(72.dp),
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp)
                            ) { Text(k, fontSize = 24.sp) }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            TextButton(onClick = { forgot = true }) { Text("PIN bhool gaye?") }
        }
    }

    if (forgot) {
        var pass by remember { mutableStateOf("") }
        var msg by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { forgot = false },
            title = { Text("PIN bhool gaye") },
            text = {
                Column {
                    if (Cloud.uid != null) {
                        Text("Apne cloud account (${Cloud.email}) ka password daalein. PIN hat jayega.")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pass, onValueChange = { pass = it }, singleLine = true,
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation()
                        )
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
