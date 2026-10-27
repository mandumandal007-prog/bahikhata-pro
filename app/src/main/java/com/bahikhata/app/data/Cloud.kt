package com.bahikhata.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bahikhata.app.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Cloud login + sync (Firebase Auth email/password + Firestore).
 *
 * Local-first: the SQLite database is always the working copy. After every change the
 * app pushes changed chunks to Firestore under users/{uid}/... . On a new phone (or after
 * reinstall) the user logs in and the data is pulled back.
 *
 * The Firebase config is baked in at build time from google-services.json
 * (repo secret GOOGLE_SERVICES_JSON or app/google-services.json). Without it the app
 * runs fully offline and shows "cloud login setup pending".
 */
object Cloud {

    private const val CHUNK = 400
    private val TABLES = listOf("parties", "entries", "cash", "items", "invoices", "settings")
    /** Settings that stay on this phone only. */
    private val LOCAL_ONLY_SETTINGS = setOf("pin", "last_backup", "last_auto_backup")

    var configured = false
        private set

    var email by mutableStateOf<String?>(null)
        private set
    var uid by mutableStateOf<String?>(null)
        private set
    var status by mutableStateOf("")
    var lastSync by mutableStateOf(0L)
        private set
    var syncing by mutableStateOf(false)
        private set

    /** True while the login screen is deciding cloud-vs-phone data; auto-sync waits meanwhile. */
    var authFlow by mutableStateOf(false)

    private lateinit var prefs: SharedPreferences
    private val mutex = Mutex()

    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)
        PhotoCloud.init(ctx)
        lastSync = prefs.getLong("last_sync", 0L)
        if (BuildConfig.FB_API_KEY.isBlank() || BuildConfig.FB_APP_ID.isBlank() || BuildConfig.FB_PROJECT_ID.isBlank()) {
            configured = false
            return
        }
        try {
            if (FirebaseApp.getApps(ctx).isEmpty()) {
                val opts = FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FB_API_KEY)
                    .setApplicationId(BuildConfig.FB_APP_ID)
                    .setProjectId(BuildConfig.FB_PROJECT_ID)
                    .apply { if (BuildConfig.FB_STORAGE.isNotBlank()) setStorageBucket(BuildConfig.FB_STORAGE) }
                    .build()
                FirebaseApp.initializeApp(ctx.applicationContext, opts)
            }
            configured = true
            val u = FirebaseAuth.getInstance().currentUser
            uid = u?.uid
            email = u?.email
        } catch (e: Exception) {
            configured = false
            status = "Cloud start nahi hua: ${e.message}"
        }
    }

    /** Has the user finished (or skipped) the first-launch login screen? */
    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) {
            prefs.edit().putBoolean("onboarded", v).apply()
        }

    private fun auth() = FirebaseAuth.getInstance()
    /** All data lives under users/{uid}/... (meta at users/{uid}/meta/info, data in users/{uid}/chunks). */
    private fun metaDoc(id: String) = FirebaseFirestore.getInstance().collection("users").document(id).collection("meta").document("info")
    private fun fs() = FirebaseFirestore.getInstance()

    private fun friendly(e: Exception): String {
        val code = (e as? FirebaseAuthException)?.errorCode ?: ""
        val msg = e.message ?: ""
        return when {
            code == "ERROR_INVALID_EMAIL" -> "Email sahi nahi hai"
            code == "ERROR_WEAK_PASSWORD" -> "Password kam se kam 6 akshar ka rakhein"
            code == "ERROR_EMAIL_ALREADY_IN_USE" -> "Is email se account pehle se bana hai — Login karein"
            code == "ERROR_USER_NOT_FOUND" -> "Is email ka account nahi mila — Signup karein"
            code == "ERROR_WRONG_PASSWORD" || code == "ERROR_INVALID_CREDENTIAL" -> "Email ya password galat hai"
            code == "ERROR_TOO_MANY_REQUESTS" -> "Bahut baar try hua — thodi der baad karein"
            code == "ERROR_OPERATION_NOT_ALLOWED" -> "Firebase me Email/Password login ON nahi hai"
            msg.contains("network", true) -> "Internet nahi hai — net on karke try karein"
            msg.contains("CONFIGURATION_NOT_FOUND") -> "Firebase me Authentication ON nahi hai"
            else -> msg.ifBlank { "Kuch gadbad hui" }
        }
    }

    /** Returns null on success, else an error message. */
    suspend fun signUp(mail: String, pass: String): String? = try {
        val r = auth().createUserWithEmailAndPassword(mail.trim(), pass).await()
        uid = r.user?.uid; email = r.user?.email
        onboarded = true
        null
    } catch (e: Exception) {
        friendly(e)
    }

    suspend fun signIn(mail: String, pass: String): String? = try {
        val r = auth().signInWithEmailAndPassword(mail.trim(), pass).await()
        uid = r.user?.uid; email = r.user?.email
        onboarded = true
        null
    } catch (e: Exception) {
        friendly(e)
    }

    suspend fun resetPassword(mail: String): String? = try {
        auth().sendPasswordResetEmail(mail.trim()).await()
        null
    } catch (e: Exception) {
        friendly(e)
    }

    /** Checks the cloud password (used to unlock a forgotten PIN). */
    suspend fun verifyPassword(pass: String): Boolean = try {
        val u = auth().currentUser
        if (u?.email == null) false else {
            u.reauthenticate(EmailAuthProvider.getCredential(u.email!!, pass)).await()
            true
        }
    } catch (e: Exception) {
        false
    }

    fun signOut() {
        if (configured) auth().signOut()
        uid = null
        email = null
        PhotoCloud.reset()
        prefs.edit().remove("hashes").remove("hash_uid").remove("last_sync").apply()
        lastSync = 0L
        status = ""
    }

    // ------------------------------------------------------------------ sync

    private fun sha(s: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    private fun localSnapshot(): Map<String, List<String>> {
        val root = Repo.exportJson()
        val out = LinkedHashMap<String, List<String>>()
        for (t in TABLES) {
            val arr = root.optJSONArray(t) ?: JSONArray()
            val rows = ArrayList<JSONObject>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (t == "settings" && o.optString("k") in LOCAL_ONLY_SETTINGS) continue
                rows.add(o)
            }
            val chunks = rows.chunked(CHUNK).map { part -> JSONArray().apply { part.forEach { put(it) } }.toString() }
            out[t] = chunks
        }
        return out
    }

    fun localIsEmpty(): Boolean =
        Repo.parties().isEmpty() && Repo.cashList().isEmpty() && Repo.items().isEmpty() && Repo.invoices().isEmpty()

    /** Last-update time of the cloud copy, 0 if none, null if it could not be read. */
    suspend fun cloudUpdatedAt(): Long? {
        val id = uid ?: return null
        return try {
            val snap = withTimeoutOrNull(15000) { metaDoc(id).get(Source.SERVER).await() }
                ?: return null
            if (!snap.exists()) 0L else (snap.getLong("updatedAt") ?: 1L)
        } catch (e: Exception) {
            status = cloudError(e)
            null
        }
    }

    private fun cloudError(e: Exception): String {
        val code = (e as? FirebaseFirestoreException)?.code
        return when {
            code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Firestore rules data block kar rahe hain (rules check karein)"
            code == FirebaseFirestoreException.Code.UNAVAILABLE -> "Internet nahi hai — net aate hi sync hoga"
            (e.message ?: "").contains("not been used", true) || (e.message ?: "").contains("does not exist", true) ->
                "Firebase me Firestore Database ON nahi hai"
            else -> "Sync error: ${e.message}"
        }
    }

    /** Uploads changed chunks. Returns true when the cloud has (or has queued) the latest data. */
    suspend fun push(): Boolean {
        val id = uid ?: return false
        if (!configured) return false
        return pushData(id).also { PhotoCloud.kick() }
    }

    private suspend fun pushData(id: String): Boolean {
        return mutex.withLock {
            syncing = true
            try {
                val snap = withContext(Dispatchers.IO) { localSnapshot() }
                val old: JSONObject = if (prefs.getString("hash_uid", "") == id)
                    JSONObject(prefs.getString("hashes", "{}") ?: "{}") else JSONObject()
                // Safety: never upload an empty phone over the cloud copy when this phone has
                // no sync history for this account (fresh install / login not yet restored).
                if (old.length() == 0 && withContext(Dispatchers.IO) { localIsEmpty() }) {
                    return@withLock true
                }
                val userDoc = metaDoc(id)
                val chunksCol = fs().collection("users").document(id).collection("chunks")
                val newHashes = JSONObject()
                val counts = HashMap<String, Any>()
                var batch = fs().batch()
                var ops = 0
                val commits = ArrayList<com.google.android.gms.tasks.Task<Void>>()
                fun flushIfFull() {
                    if (ops >= 400) {
                        commits.add(batch.commit()); batch = fs().batch(); ops = 0
                    }
                }
                for ((t, chunks) in snap) {
                    counts[t] = chunks.size
                    chunks.forEachIndexed { i, data ->
                        val docId = "${t}_$i"
                        val h = sha(data)
                        newHashes.put(docId, h)
                        if (old.optString(docId) != h) {
                            batch.set(chunksCol.document(docId), mapOf("t" to t, "i" to i, "data" to data))
                            ops++; flushIfFull()
                        }
                    }
                    // delete chunks that no longer exist
                    val oldKeys = old.keys().asSequence().filter { it.startsWith("${t}_") }.toList()
                    for (k in oldKeys) {
                        val idx = k.removePrefix("${t}_").toIntOrNull() ?: continue
                        if (idx >= chunks.size) {
                            batch.delete(chunksCol.document(k)); ops++; flushIfFull()
                        }
                    }
                }
                val changed = ops > 0 || commits.isNotEmpty() || old.length() == 0
                if (!changed) {
                    status = "Sab sync hai ✓"
                    return@withLock true
                }
                val now = System.currentTimeMillis()
                batch.set(userDoc, mapOf("app" to "BahiKhata Pro", "updatedAt" to now, "counts" to counts,
                    "email" to (email ?: ""), "serverTime" to FieldValue.serverTimestamp()))
                commits.add(batch.commit())
                // Firestore keeps the writes on the phone and sends them when the net comes back.
                prefs.edit().putString("hashes", newHashes.toString()).putString("hash_uid", id).apply()
                val ok = withTimeoutOrNull(20000) {
                    commits.forEach { it.await() }
                    true
                }
                if (ok == true) {
                    lastSync = now
                    prefs.edit().putLong("last_sync", now).apply()
                    status = "Cloud me save ho gaya ✓"
                } else {
                    status = "Net slow/band hai — data phone me safe hai, net aate hi upload hoga"
                }
                true
            } catch (e: Exception) {
                status = cloudError(e)
                prefs.edit().remove("hashes").apply()
                false
            } finally {
                syncing = false
            }
        }
    }

    /** Downloads the cloud copy and replaces local data. Returns null on success or an error message. */
    suspend fun pull(): String? {
        val id = uid ?: return "Login nahi hai"
        return mutex.withLock {
            syncing = true
            try {
                val userDoc = withTimeoutOrNull(20000) { metaDoc(id).get(Source.SERVER).await() }
                    ?: return@withLock "Internet nahi hai — net on karke try karein"
                if (!userDoc.exists()) return@withLock "Cloud me koi data nahi mila"
                val chunkSnap = withTimeoutOrNull(30000) {
                    fs().collection("users").document(id).collection("chunks").get(Source.SERVER).await()
                } ?: return@withLock "Internet slow hai — dobara try karein"
                @Suppress("UNCHECKED_CAST")
                val counts = (userDoc.get("counts") as? Map<String, Any>) ?: emptyMap()
                val byId = chunkSnap.documents.associateBy { it.id }
                val root = JSONObject()
                root.put("app", "BahiKhata")
                root.put("version", 1)
                val hashes = JSONObject()
                for (t in TABLES) {
                    val n = (counts[t] as? Number)?.toInt() ?: 0
                    val all = JSONArray()
                    for (i in 0 until n) {
                        val d = byId["${t}_$i"]?.getString("data") ?: continue
                        hashes.put("${t}_$i", sha(d))
                        val arr = JSONArray(d)
                        for (k in 0 until arr.length()) all.put(arr.get(k))
                    }
                    root.put(t, all)
                }
                val ok = withContext(Dispatchers.IO) { Repo.importJson(root) }
                if (!ok) return@withLock "Data restore nahi hua (file kharab?)"
                prefs.edit().putString("hashes", hashes.toString()).putString("hash_uid", id).apply()
                lastSync = System.currentTimeMillis()
                prefs.edit().putLong("last_sync", lastSync).apply()
                status = "Cloud se data wapas aa gaya ✓"
                PhotoCloud.kick(restore = true, overwrite = true)
                null
            } catch (e: Exception) {
                cloudError(e).also { status = it }
            } finally {
                syncing = false
            }
        }
    }
}
