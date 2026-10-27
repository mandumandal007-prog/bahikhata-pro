package com.bahikhata.app.util

import android.content.Context
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.bahikhata.app.data.Entry
import com.bahikhata.app.data.Invoice
import com.bahikhata.app.data.Party
import java.io.File
import java.io.FileOutputStream

object Pdf {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f

    private fun paint(size: Float, bold: Boolean = false, color: Int = AColor.BLACK): Paint {
        val p = Paint()
        p.isAntiAlias = true
        p.textSize = size
        p.color = color
        p.typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        return p
    }

    private fun line(canvas: android.graphics.Canvas, y: Float, paint: Paint) {
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, paint)
    }

    /** Party ledger / statement of account. */
    fun statement(
        ctx: Context,
        party: Party,
        entries: List<Entry>,
        shopName: String,
        shopPhone: String
    ): File {
        val doc = PdfDocument()
        val title = paint(18f, true)
        val sub = paint(11f)
        val body = paint(11f)
        val bodyBold = paint(11f, true)
        val small = paint(9f)
        val rule = Paint().apply {
            color = AColor.parseColor("#CCCCCC")
            strokeWidth = 0.8f
        }

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        var canvas = page.canvas
        var y = MARGIN + 10f

        fun header() {
            canvas.drawText(shopName, MARGIN, y, title)
            y += 16f
            if (shopPhone.isNotBlank()) {
                canvas.drawText(shopPhone, MARGIN, y, small)
                y += 12f
            }
            y += 6f
            canvas.drawText("Statement of Account", MARGIN, y, bodyBold)
            y += 16f
            canvas.drawText("Party: ${party.name}", MARGIN, y, body)
            y += 14f
            if (party.phone.isNotBlank()) {
                canvas.drawText("Phone: ${party.phone}", MARGIN, y, body)
                y += 14f
            }
            canvas.drawText("Type: ${if (party.type == "SUPPLIER") "Supplier" else "Customer"}", MARGIN, y, body)
            y += 14f
            canvas.drawText("Date: ${fmtDate(System.currentTimeMillis())}", MARGIN, y, body)
            y += 18f
            line(canvas, y, rule)
            y += 14f
            canvas.drawText("Date", MARGIN, y, bodyBold)
            canvas.drawText("Type", MARGIN + 90f, y, bodyBold)
            canvas.drawText("Amount", MARGIN + 180f, y, bodyBold)
            canvas.drawText("Balance", MARGIN + 320f, y, bodyBold)
            y += 6f
            line(canvas, y, rule)
            y += 16f
        }

        header()

        var running = party.opening
        if (party.opening != 0.0) {
            canvas.drawText("-", MARGIN, y, body)
            canvas.drawText("Opening", MARGIN + 90f, y, body)
            canvas.drawText(money(party.opening), MARGIN + 180f, y, body)
            canvas.drawText(money(running), MARGIN + 320f, y, body)
            y += 16f
        }

        for (e in entries) {
            if (y > PAGE_H - 90f) {
                canvas.drawText("Page $pageNo", MARGIN, PAGE_H - 30f, small)
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
                canvas = page.canvas
                y = MARGIN + 20f
                canvas.drawText(shopName + "  (contd.)", MARGIN, y, bodyBold)
                y += 24f
            }
            running += if (e.gave) e.amount else -e.amount
            canvas.drawText(fmtDate(e.date), MARGIN, y, body)
            canvas.drawText(if (e.gave) "You Gave" else "You Got", MARGIN + 90f, y, body)
            canvas.drawText(money(e.amount), MARGIN + 180f, y, body)
            canvas.drawText(money(running), MARGIN + 320f, y, body)
            y += 16f
        }

        y += 4f
        line(canvas, y, rule)
        y += 20f
        canvas.drawText("Closing Balance", MARGIN, y, bodyBold)
        canvas.drawText(money(running), MARGIN + 320f, y, bodyBold)
        y += 22f
        val note = if (running >= 0) "You will get: ${money(running)}" else "You will give: ${money(-running)}"
        canvas.drawText(note, MARGIN, y, bodyBold)

        canvas.drawText("Page $pageNo", MARGIN, PAGE_H - 30f, small)
        doc.finishPage(page)

        val file = File(ctx.cacheDir, "statement_${party.id}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** GST / retail invoice. */
    fun invoice(
        ctx: Context,
        inv: Invoice,
        shopName: String,
        shopAddress: String,
        shopPhone: String,
        shopGstin: String
    ): File {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        val canvas = page.canvas

        val title = paint(20f, true)
        val body = paint(11f)
        val bold = paint(11f, true)
        val small = paint(9f)
        val rule = Paint().apply {
            color = AColor.parseColor("#CCCCCC")
            strokeWidth = 0.8f
        }

        var y = MARGIN + 10f
        canvas.drawText(shopName, MARGIN, y, title)
        y += 18f
        if (shopAddress.isNotBlank()) {
            canvas.drawText(shopAddress, MARGIN, y, small)
            y += 12f
        }
        if (shopPhone.isNotBlank()) {
            canvas.drawText("Phone: $shopPhone", MARGIN, y, small)
            y += 12f
        }
        if (shopGstin.isNotBlank()) {
            canvas.drawText("GSTIN: $shopGstin", MARGIN, y, small)
            y += 12f
        }

        canvas.drawText("TAX INVOICE", PAGE_W - MARGIN - 90f, MARGIN + 12f, bold)
        canvas.drawText(inv.number, PAGE_W - MARGIN - 90f, MARGIN + 28f, body)

        y += 10f
        line(canvas, y, rule)
        y += 20f
        canvas.drawText("Bill To:", MARGIN, y, bold)
        y += 15f
        canvas.drawText(inv.partyName.ifBlank { "Cash Customer" }, MARGIN, y, body)
        y += 14f
        if (inv.phone.isNotBlank()) {
            canvas.drawText(inv.phone, MARGIN, y, body)
            y += 14f
        }
        canvas.drawText("Date: ${fmtDate(inv.date)}", PAGE_W - MARGIN - 130f, y - 29f, body)

        y += 10f
        line(canvas, y, rule)
        y += 16f
        canvas.drawText("#", MARGIN, y, bold)
        canvas.drawText("Item", MARGIN + 25f, y, bold)
        canvas.drawText("Qty", MARGIN + 250f, y, bold)
        canvas.drawText("Rate", MARGIN + 320f, y, bold)
        canvas.drawText("Amount", MARGIN + 410f, y, bold)
        y += 6f
        line(canvas, y, rule)
        y += 16f

        inv.lines.forEachIndexed { i, l ->
            canvas.drawText((i + 1).toString(), MARGIN, y, body)
            canvas.drawText(l.name.take(32), MARGIN + 25f, y, body)
            canvas.drawText(money(l.qty), MARGIN + 250f, y, body)
            canvas.drawText(money(l.rate), MARGIN + 320f, y, body)
            canvas.drawText(money(l.qty * l.rate), MARGIN + 410f, y, body)
            y += 16f
        }

        y += 4f
        line(canvas, y, rule)
        y += 20f

        fun row(label: String, value: String, isBold: Boolean = false) {
            val p = if (isBold) bold else body
            canvas.drawText(label, MARGIN + 300f, y, p)
            canvas.drawText(value, MARGIN + 410f, y, p)
            y += 16f
        }

        row("Subtotal", money(inv.subtotal))
        if (inv.discount > 0) row("Discount", "-" + money(inv.discount))
        if (inv.gstPercent > 0) row("GST (${money(inv.gstPercent)}%)", money(inv.gstAmount))
        y += 4f
        line(canvas, y, rule)
        y += 20f
        row("TOTAL", "Rs " + money(inv.total), true)

        y += 20f
        canvas.drawText(if (inv.paid) "PAID" else "UNPAID", MARGIN, y, bold)
        y += 30f
        canvas.drawText("Thank you for your business!", MARGIN, y, small)

        doc.finishPage(page)
        val file = File(ctx.cacheDir, "invoice_${inv.number}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
