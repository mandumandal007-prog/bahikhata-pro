package com.bahikhata.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.bahikhata.app.data.PhotoCloud
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/**
 * Party (customer/supplier) photos. Kept on this phone in app-internal storage,
 * one JPEG per party id: files/party_photos/p_<id>.jpg (about 512 px).
 * A small copy (about 256 px) is synced to Firestore by [com.bahikhata.app.data.PhotoCloud].
 */
object PartyPhotos {
    /** Bumped on every change so avatars recompose. */
    var version by mutableIntStateOf(0); private set
    private val cache = LruCache<String, Bitmap>(60)

    private fun dir(ctx: Context) = File(ctx.filesDir, "party_photos").apply { mkdirs() }
    fun file(ctx: Context, id: Long) = File(dir(ctx), "p_$id.jpg")
    fun has(ctx: Context, id: Long) = id > 0 && file(ctx, id).exists()

    fun load(ctx: Context, id: Long): Bitmap? {
        if (id <= 0) return null
        val key = "$id:$version"
        cache.get(key)?.let { return it }
        val f = file(ctx, id)
        if (!f.exists()) return null
        return try { BitmapFactory.decodeFile(f.absolutePath)?.also { cache.put(key, it) } } catch (e: Throwable) { null }
    }

    fun loadFile(f: File?): Bitmap? = try { f?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) } } catch (e: Throwable) { null }

    /** Reads an image (gallery/camera), fixes rotation, scales to <= 512 px and writes a temp JPEG. */
    fun prepare(ctx: Context, uri: Uri): File? = try {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: throw IllegalStateException("decode")
        val rot = try {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Throwable) { 0f }
        val scale = 512f / max(bmp.width, bmp.height)
        val m = Matrix()
        if (scale < 1f) m.postScale(scale, scale)
        if (rot != 0f) m.postRotate(rot)
        if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        val out = File(ctx.cacheDir, "photo_pending_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        out
    } catch (e: Throwable) { null }

    fun commit(ctx: Context, id: Long, pending: File) {
        try { pending.copyTo(file(ctx, id), overwrite = true); pending.delete() } catch (_: Throwable) {}
        version++
        PhotoCloud.onPhotoSet(id)
    }

    fun delete(ctx: Context, id: Long) {
        try { file(ctx, id).delete() } catch (_: Throwable) {}
        version++
        PhotoCloud.onPhotoRemoved(id)
    }

    /** Lets background sync refresh avatars (call on the main thread). */
    fun bumpVersion() { version++ }

    /** All local photo ids (files named p_<id>.jpg). */
    fun localIds(ctx: Context): List<Long> =
        dir(ctx).listFiles()?.mapNotNull { f ->
            f.name.takeIf { it.startsWith("p_") && it.endsWith(".jpg") }?.removePrefix("p_")?.removeSuffix(".jpg")?.toLongOrNull()
        } ?: emptyList()

    /**
     * Small cloud/backup copy: max 256 px, JPEG quality 70 (lower if needed to stay near 40 KB).
     * Returns null if the photo can't be read or would exceed the 200 KB hard cap.
     */
    fun smallJpeg(f: File): ByteArray? = try {
        var bmp = BitmapFactory.decodeFile(f.absolutePath) ?: throw IllegalStateException("decode")
        val scale = 256f / max(bmp.width, bmp.height)
        if (scale < 1f) bmp = Bitmap.createScaledBitmap(bmp, max(1, (bmp.width * scale).toInt()), max(1, (bmp.height * scale).toInt()), true)
        var out: ByteArray? = null
        for (q in intArrayOf(70, 60, 50, 40)) {
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, q, bos)
            out = bos.toByteArray()
            if (out.size <= 40 * 1024) break
        }
        out?.takeIf { it.isNotEmpty() && it.size <= 200 * 1024 }
    } catch (e: Throwable) { null }

    /** Writes downloaded/restored JPEG bytes as the party's local photo (atomic rename). */
    fun writeBytes(ctx: Context, id: Long, bytes: ByteArray): Boolean = try {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, b)
        if (id <= 0 || b.outWidth <= 0 || b.outHeight <= 0) false else {
            val tmp = File(dir(ctx), "p_$id.jpg.tmp")
            tmp.writeBytes(bytes)
            val dst = file(ctx, id)
            if (!tmp.renameTo(dst)) { tmp.copyTo(dst, overwrite = true); tmp.delete() }
            true
        }
    } catch (e: Throwable) { false }

    /** Backup file: {"<partyId>": "<base64 jpeg>"} for every local photo. */
    fun backupJson(ctx: Context): JSONObject {
        val o = JSONObject()
        for (id in localIds(ctx)) {
            val bytes = smallJpeg(file(ctx, id)) ?: continue
            o.put(id.toString(), Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
        return o
    }

    /** Backup JSON with party photos added under "party_photos" (normal cloud sync never includes them). */
    fun exportWithPhotos(ctx: Context, root: JSONObject): JSONObject {
        try { root.put("party_photos", backupJson(ctx)) } catch (_: Throwable) {}
        return root
    }

    /** Restores photos from a backup file (if it has any) and queues them for cloud sync. */
    fun restoreFromBackup(ctx: Context, root: JSONObject): Int {
        val o = root.optJSONObject("party_photos") ?: return 0
        var n = 0
        for (k in o.keys()) {
            val id = k.toLongOrNull() ?: continue
            val bytes = try { Base64.decode(o.optString(k), Base64.NO_WRAP) } catch (e: Throwable) { continue }
            if (writeBytes(ctx, id, bytes)) { PhotoCloud.onPhotoSet(id); n++ }
        }
        return n
    }
}
