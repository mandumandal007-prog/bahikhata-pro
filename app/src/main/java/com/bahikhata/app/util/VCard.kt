package com.bahikhata.app.util

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.bahikhata.app.data.Repo
import java.io.File

/**
 * Editable visiting card (v1.0.46). Every field, its show/hide switch, the template and the photo
 * position are saved in settings (keys vc_*); the photo/logo is a local file like party photos.
 */
object VCard {
    data class Field(val key: String, val label: String, val icon: String)
    val FIELDS = listOf(
        Field("shop", "Dukan / business ka naam", ""),
        Field("owner", "Malik ka naam", ""),
        Field("tagline", "Tagline (jaise: Sabse sasta, sabse achha)", ""),
        Field("phone", "Mobile number", "📞 "),
        Field("phone2", "Doosra number / WhatsApp", "💬 "),
        Field("email", "Email", "✉️ "),
        Field("address", "Pata (address)", "📍 "),
        Field("website", "Website / Instagram", "🌐 "),
        Field("gstin", "GSTIN", "GSTIN: "),
        Field("upi", "UPI ID", "UPI: "),
        Field("extra", "Aur kuch (timing, services…)", "• "),
    )
    data class Template(val name: String, val c1: Int, val c2: Int, val accent: Int, val text: Int)
    val TEMPLATES = listOf(
        Template("Navy", 0xFF14213D.toInt(), 0xFF274472.toInt(), 0xFFFCA311.toInt(), 0xFFFFFFFF.toInt()),
        Template("Green", 0xFF0B6E4F.toInt(), 0xFF08A045.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
        Template("Maroon", 0xFF6A040F.toInt(), 0xFF9D0208.toInt(), 0xFFFFD166.toInt(), 0xFFFFFFFF.toInt()),
        Template("Black Gold", 0xFF0D0D0D.toInt(), 0xFF2B2B2B.toInt(), 0xFFD4AF37.toInt(), 0xFFF5E6B8.toInt()),
        Template("Purple", 0xFF3C1A78.toInt(), 0xFF7B2FF7.toInt(), 0xFFFFC6FF.toInt(), 0xFFFFFFFF.toInt()),
        Template("Sky", 0xFF0077B6.toInt(), 0xFF48CAE4.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
        Template("Kesariya", 0xFFE85D04.toInt(), 0xFFFFBA08.toInt(), 0xFF6A040F.toInt(), 0xFFFFFFFF.toInt()),
        Template("Safed", 0xFFFFFFFF.toInt(), 0xFFF1F3F5.toInt(), 0xFF1D4ED8.toInt(), 0xFF111827.toInt()),
        Template("Pink", 0xFFB5179E.toInt(), 0xFFF72585.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
    )
    val PHOTO_POS = listOf("left" to "Baayen", "right" to "Daayen", "top" to "Upar", "none" to "Nahi")

    fun default(key: String): String = when (key) {
        "shop" -> Repo.businessName()
        "phone" -> Repo.setting("phone", "")
        "address" -> Repo.setting("address", "")
        "gstin" -> Repo.setting("gstin", "")
        "upi" -> S.upiId
        else -> ""
    }
    fun value(key: String): String = Repo.setting("vc_$key", "\u0000").let { if (it == "\u0000") default(key) else it }
    fun shown(key: String): Boolean = Repo.setting("vc_show_$key", "1") == "1"
    fun photoFile(ctx: Context) = File(ctx.filesDir, "vcard_photo.jpg")

    data class Cfg(
        val values: Map<String, String>, val show: Map<String, Boolean>, val style: Int, val photoPos: String,
        val roundPhoto: Boolean, val showQr: Boolean, val photo: Bitmap?,
    )

    fun current(ctx: Context): Cfg = Cfg(
        FIELDS.associate { it.key to value(it.key) }, FIELDS.associate { it.key to shown(it.key) },
        Repo.setting("vcard_style", "0").toIntOrNull() ?: 0, Repo.setting("vc_photo_pos", "left"),
        Repo.setting("vc_photo_round", "1") == "1", Repo.setting("vc_qr", "1") == "1",
        PartyPhotos.loadFile(photoFile(ctx)),
    )

    fun save(cfg: Cfg) {
        cfg.values.forEach { (k, v) -> Repo.setSetting("vc_$k", v) }
        cfg.show.forEach { (k, v) -> Repo.setSetting("vc_show_$k", if (v) "1" else "0") }
        Repo.setSetting("vcard_style", cfg.style.toString())
        Repo.setSetting("vc_photo_pos", cfg.photoPos)
        Repo.setSetting("vc_photo_round", if (cfg.roundPhoto) "1" else "0")
        Repo.setSetting("vc_qr", if (cfg.showQr) "1" else "0")
    }

    private fun layout(text: String, p: TextPaint, width: Int, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, width.coerceAtLeast(10))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false).build()

    fun render(cfg: Cfg): Bitmap {
        val w = 1080; val h = 640
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val t = TEMPLATES[cfg.style.coerceIn(0, TEMPLATES.size - 1)]
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), t.c1, t.c2, Shader.TileMode.CLAMP) }
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 36f, 36f, bg)
        // decorative corner
        c.drawCircle(w + 40f, -60f, 260f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.accent; alpha = 40 })
        fun v(k: String) = if (cfg.show[k] == true) cfg.values[k].orEmpty().trim() else ""

        val photo = if (cfg.photoPos != "none") cfg.photo else null
        val qrUri = if (cfg.showQr) v("upi").takeIf { it.contains("@") }?.let {
            "upi://pay?pa=" + android.net.Uri.encode(it) + "&pn=" + android.net.Uri.encode(v("shop").ifBlank { "Shop" }) + "&cu=INR"
        } else null
        val qrSize = 250
        var left = 60f; var right = w - 60f; var top = 70f
        // photo
        if (photo != null) {
            val ps = if (cfg.photoPos == "top") 150f else 210f
            val r = when (cfg.photoPos) {
                "right" -> RectF(w - 60f - ps, 60f, w - 60f, 60f + ps)
                "top" -> RectF(60f, 50f, 60f + ps, 50f + ps)
                else -> RectF(60f, 60f, 60f + ps, 60f + ps)
            }
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.accent }
            val src = cropSquare(photo)
            val path = Path().apply { if (cfg.roundPhoto) addOval(r, Path.Direction.CW) else addRoundRect(r, 24f, 24f, Path.Direction.CW) }
            if (cfg.roundPhoto) c.drawOval(RectF(r.left - 6, r.top - 6, r.right + 6, r.bottom + 6), ring)
            else c.drawRoundRect(RectF(r.left - 6, r.top - 6, r.right + 6, r.bottom + 6), 28f, 28f, ring)
            c.save(); c.clipPath(path); c.drawBitmap(src, null, r, Paint(Paint.FILTER_BITMAP_FLAG)); c.restore()
            when (cfg.photoPos) {
                "right" -> right = r.left - 30f
                "top" -> { left = r.right + 30f; top = 70f }
                else -> left = r.right + 36f
            }
        }
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = t.text }
        var y = top
        val shop = v("shop")
        if (shop.isNotBlank()) {
            tp.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); tp.textSize = if (shop.length > 20) 58f else 72f
            val l = layout(shop, tp, (right - left).toInt(), 2)
            c.save(); c.translate(left, y); l.draw(c); c.restore(); y += l.height + 8f
        }
        val owner = v("owner")
        if (owner.isNotBlank()) {
            tp.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); tp.textSize = 38f
            val l = layout(owner, tp, (right - left).toInt(), 1); c.save(); c.translate(left, y); l.draw(c); c.restore(); y += l.height + 4f
        }
        val tag = v("tagline")
        if (tag.isNotBlank()) {
            tp.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC); tp.textSize = 32f; tp.alpha = 220
            val l = layout(tag, tp, (right - left).toInt(), 2); c.save(); c.translate(left, y); l.draw(c); c.restore(); y += l.height + 4f
            tp.alpha = 255
        }
        c.drawRect(left, y + 10f, left + 200f, y + 18f, Paint().apply { color = t.accent })
        y += 44f
        // details: below the photo/header, full width minus QR
        val photoBottom = if (photo != null) (if (cfg.photoPos == "top") 206f else 276f) else 0f
        if (y < photoBottom + 14f) y = photoBottom + 14f
        val dLeft = 60f
        val dRight = if (qrUri != null) w - 60f - qrSize - 30f else w - 60f
        tp.typeface = Typeface.DEFAULT; tp.textSize = 34f
        val lines = mutableListOf<String>()
        for (f in FIELDS.drop(3)) { val s = v(f.key); if (s.isNotBlank()) lines += f.icon + s }
        for (s in lines) {
            if (y > h - 70f) break
            val maxL = if (s.length > 40) 2 else 1
            val l = layout(s, tp, (dRight - dLeft).toInt(), maxL)
            c.save(); c.translate(dLeft, y); l.draw(c); c.restore(); y += l.height + 12f
        }
        if (qrUri != null) Qr.bitmap(qrUri, qrSize)?.let { q ->
            val r = RectF(w - 60f - qrSize, h - 60f - qrSize - 34f, w - 60f, h - 60f - 34f)
            c.drawRoundRect(RectF(r.left - 10, r.top - 10, r.right + 10, r.bottom + 10), 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
            c.drawBitmap(q, null, r, null)
            val sp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = t.text; textSize = 26f; textAlign = Paint.Align.CENTER }
            c.drawText("Scan karke pay karein", r.centerX(), h - 40f, sp)
        }
        return bmp
    }

    private fun cropSquare(b: Bitmap): Bitmap {
        val s = minOf(b.width, b.height)
        return Bitmap.createBitmap(b, (b.width - s) / 2, (b.height - s) / 2, s, s)
    }
}
