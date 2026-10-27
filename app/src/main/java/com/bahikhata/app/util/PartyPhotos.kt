package com.bahikhata.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlin.math.max

/**
 * Party (customer/supplier) photos. Kept only on this phone, in app-internal storage,
 * one JPEG per party id: files/party_photos/p_<id>.jpg (about 512 px). Not synced to cloud.
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
    }

    fun delete(ctx: Context, id: Long) {
        try { file(ctx, id).delete() } catch (_: Throwable) {}
        version++
    }
}
