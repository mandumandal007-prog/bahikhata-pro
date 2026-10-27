package com.bahikhata.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.bahikhata.app.util.PartyPhotos
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Free cloud sync for party photos (no Firebase Storage). Each photo is a small JPEG
 * (~256 px, 15–40 KB) stored as base64 in its own Firestore doc:
 *   users/{uid}/partyPhotos/{partyId}  = {data, updatedAt}
 * plus one small index doc users/{uid}/meta/photos = {ids: {partyId: updatedAt}} so a
 * restore reads one doc and downloads only the photos that are missing on this phone.
 *
 * Local-first: changes are queued in prefs and retried after every data sync; Firestore
 * also keeps queued writes on the phone while offline. Everything runs in the background.
 */
object PhotoCloud {
    private var app: Context? = null
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    /** uid for which the cloud index was checked in this app session. */
    @Volatile private var checkedUid: String? = null

    fun init(ctx: Context) {
        app = ctx.applicationContext
        prefs = ctx.applicationContext.getSharedPreferences("photo_sync", Context.MODE_PRIVATE)
    }

    /** Forget the per-session check (on logout). */
    fun reset() { checkedUid = null }

    private fun fs() = FirebaseFirestore.getInstance()
    private fun col(uid: String) = fs().collection("users").document(uid).collection("partyPhotos")
    private fun indexDoc(uid: String) = fs().collection("users").document(uid).collection("meta").document("photos")

    // ------------------------------------------------------------ local queue
    // pending = {"<partyId>": "up:<millis>" | "del:<millis>"}

    @Synchronized private fun pending(): JSONObject = JSONObject(prefs.getString("pending", "{}") ?: "{}")
    @Synchronized private fun mark(id: Long, op: String) {
        val p = pending(); p.put(id.toString(), "$op:${System.currentTimeMillis()}")
        prefs.edit().putString("pending", p.toString()).apply()
    }
    /** Clears an op only if nothing newer was queued for that photo meanwhile. */
    @Synchronized private fun clear(key: String, value: String) {
        val p = pending()
        if (p.optString(key) == value) { p.remove(key); prefs.edit().putString("pending", p.toString()).apply() }
    }
    @Synchronized private fun synced(uid: String): JSONObject = JSONObject(prefs.getString("synced_$uid", "{}") ?: "{}")
    @Synchronized private fun setSynced(uid: String, key: String, ts: Long?) {
        val s = synced(uid)
        if (ts == null) s.remove(key) else s.put(key, ts)
        prefs.edit().putString("synced_$uid", s.toString()).apply()
    }

    fun onPhotoSet(id: Long) {
        if (app == null || id <= 0) return
        mark(id, "up"); kick()
    }

    fun onPhotoRemoved(id: Long) {
        if (app == null || id <= 0) return
        mark(id, "del"); kick()
    }

    /**
     * Runs a background pass: uploads/deletes queued photos and, once per session (or when
     * [restore] is true, e.g. after a cloud restore), downloads photos missing on this phone.
     * [overwrite] replaces local photos that differ from the cloud copy (used after a cloud restore).
     */
    fun kick(restore: Boolean = false, overwrite: Boolean = false) {
        if (app == null) return
        scope.launch {
            try { run(restore, overwrite) } catch (_: Throwable) {}
        }
    }

    private suspend fun run(restore: Boolean, overwrite: Boolean) = mutex.withLock {
        val ctx = app ?: return@withLock
        val uid = Cloud.uid ?: return@withLock
        if (!Cloud.configured) return@withLock
        if (restore || checkedUid != uid) {
            if (download(ctx, uid, overwrite)) checkedUid = uid
        }
        upload(ctx, uid)
    }

    /** Reads the index; downloads missing/newer photos; queues local-only photos for upload. */
    private suspend fun download(ctx: Context, uid: String, overwrite: Boolean): Boolean {
        val snap = withTimeoutOrNull(15000) { indexDoc(uid).get(Source.SERVER).await() } ?: return false
        val remote = HashMap<String, Long>()
        (snap.get("ids") as? Map<*, *>)?.forEach { (k, v) ->
            val key = k?.toString() ?: return@forEach
            remote[key] = (v as? Number)?.toLong() ?: 0L
        }
        val partyIds = withContext(Dispatchers.IO) { Repo.parties().map { it.id }.toSet() }
        val pend = pending()
        var changed = false
        for ((key, ts) in remote) {
            val id = key.toLongOrNull() ?: continue
            if (id !in partyIds || pend.has(key)) continue
            val have = synced(uid).optLong(key, -1L)
            val exists = PartyPhotos.file(ctx, id).exists()
            val need = !exists || (have in 0 until ts) || (overwrite && have != ts)
            if (!need) {
                if (have < 0) setSynced(uid, key, ts)
                continue
            }
            val doc = withTimeoutOrNull(15000) { col(uid).document(key).get(Source.SERVER).await() } ?: continue
            val data = doc.getString("data") ?: continue
            val bytes = try { Base64.decode(data, Base64.NO_WRAP) } catch (e: Throwable) { continue }
            // a newer local change may have been queued while downloading — keep it
            if (pending().has(key)) continue
            if (PartyPhotos.writeBytes(ctx, id, bytes)) {
                setSynced(uid, key, (doc.getLong("updatedAt") ?: ts))
                changed = true
            }
        }
        // photos that exist only on this phone (e.g. added in v1.0.37 or before login) -> upload
        val syncedNow = synced(uid)
        for (id in PartyPhotos.localIds(ctx)) {
            val key = id.toString()
            if (id in partyIds && !remote.containsKey(key) && !syncedNow.has(key) && !pending().has(key)) mark(id, "up")
        }
        if (changed) withContext(Dispatchers.Main) { PartyPhotos.bumpVersion() }
        return true
    }

    private suspend fun upload(ctx: Context, uid: String) {
        val pend = pending()
        for (key in pend.keys().asSequence().toList()) {
            val value = pend.optString(key)
            val id = key.toLongOrNull()
            if (id == null) { clear(key, value); continue }
            val now = System.currentTimeMillis()
            val batch = fs().batch()
            val isUp = value.startsWith("up")
            if (isUp) {
                val f = PartyPhotos.file(ctx, id)
                if (!f.exists()) { clear(key, value); continue }
                val bytes = withContext(Dispatchers.IO) { PartyPhotos.smallJpeg(f) }
                if (bytes == null) { clear(key, value); continue }
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                batch.set(col(uid).document(key), mapOf("data" to b64, "updatedAt" to now))
                batch.set(indexDoc(uid), mapOf("ids" to mapOf(key to now), "updatedAt" to now), SetOptions.merge())
            } else {
                batch.delete(col(uid).document(key))
                batch.set(indexDoc(uid), mapOf("ids" to mapOf(key to FieldValue.delete()), "updatedAt" to now), SetOptions.merge())
            }
            val ok = try {
                val task = batch.commit()
                withTimeoutOrNull(20000) { task.await(); true } ?: false
            } catch (e: Exception) { false }
            if (!ok) return // offline/slow: Firestore keeps the write queued; we retry on the next sync too
            clear(key, value)
            setSynced(uid, key, if (isUp) now else null)
        }
    }
}
