package com.bahikhata.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single source of truth for the app. Backed by a local SQLite database.
 * `tick` is a Compose state counter — every write bumps it so UI recomposes.
 */
object Repo {

    private var dbh: DbHelper? = null

    var tick by mutableStateOf(0)
        private set

    fun init(ctx: Context) {
        if (dbh == null) {
            val h = DbHelper(ctx.applicationContext)
            h.writableDatabase
            dbh = h
        }
    }

    private val db: SQLiteDatabase get() = dbh!!.writableDatabase

    fun bump() {
        tick++
    }

    private fun <T> query(sql: String, args: Array<String>? = null, mapper: (Cursor) -> T): List<T> {
        val out = ArrayList<T>()
        db.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) out.add(mapper(c))
        }
        return out
    }

    // ---------------------------------------------------------------- settings

    fun setting(k: String, def: String = ""): String {
        db.rawQuery("SELECT v FROM settings WHERE k=?", arrayOf(k)).use { c ->
            if (c.moveToNext()) return c.getString(0) ?: def
        }
        return def
    }

    fun setSetting(k: String, v: String) {
        val cv = ContentValues().apply {
            put("k", k)
            put("v", v)
        }
        db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        bump()
    }

    /** All settings whose key starts with [prefix] (v1.0.44, reminder history). */
    fun settingsWithPrefix(prefix: String): List<Pair<String, String>> =
        query("SELECT k, v FROM settings WHERE k LIKE ?", arrayOf("$prefix%")) { (it.getString(0) ?: "") to (it.getString(1) ?: "") }

    fun businessName(): String = setting("business", "Meri Dukan")

    // ----------------------------------------------------------------- parties

    private fun mapParty(c: Cursor) = Party(
        id = c.getLong(0),
        name = c.getString(1) ?: "",
        phone = c.getString(2) ?: "",
        type = c.getString(3) ?: "CUSTOMER",
        opening = c.getDouble(4),
        note = c.getString(5) ?: "",
        createdAt = c.getLong(6)
    )

    private const val PARTY_COLS = "id,name,phone,type,opening,note,created_at"

    fun balances(): Map<Long, Double> {
        val m = HashMap<Long, Double>()
        query(
            "SELECT party_id, SUM(CASE WHEN gave=1 THEN amount ELSE -amount END) FROM entries GROUP BY party_id"
        ) { c -> m[c.getLong(0)] = c.getDouble(1) }
        return m
    }

    fun parties(): List<Party> {
        val bal = balances()
        return query("SELECT $PARTY_COLS FROM parties ORDER BY name COLLATE NOCASE") { mapParty(it) }
            .map { it.copy(balance = it.opening + (bal[it.id] ?: 0.0)) }
    }

    fun party(id: Long): Party? {
        val bal = balances()[id] ?: 0.0
        return query("SELECT $PARTY_COLS FROM parties WHERE id=?", arrayOf(id.toString())) { mapParty(it) }
            .firstOrNull()
            ?.let { it.copy(balance = it.opening + bal) }
    }

    fun addParty(name: String, phone: String, type: String, opening: Double, note: String): Long {
        val cv = ContentValues().apply {
            put("name", name.trim())
            put("phone", phone.trim())
            put("type", type)
            put("opening", opening)
            put("note", note.trim())
            put("created_at", System.currentTimeMillis())
        }
        val id = db.insert("parties", null, cv)
        bump()
        return id
    }

    fun updateParty(id: Long, name: String, phone: String, type: String, opening: Double, note: String) {
        val cv = ContentValues().apply {
            put("name", name.trim())
            put("phone", phone.trim())
            put("type", type)
            put("opening", opening)
            put("note", note.trim())
        }
        db.update("parties", cv, "id=?", arrayOf(id.toString()))
        bump()
    }

    /** Parties with a "Diye" entry whose due date has passed (for the list badge). */
    fun overdueParties(): Set<Long> = query(
        "SELECT DISTINCT party_id FROM entries WHERE gave=1 AND due_date IS NOT NULL AND due_date > 0 AND due_date < ?",
        arrayOf(startOfToday().toString())
    ) { it.getLong(0) }.toSet()

    /** Latest entry date per party (for "recent activity" sorting). */
    fun lastEntryDates(): Map<Long, Long> {
        val m = HashMap<Long, Long>()
        query("SELECT party_id, MAX(date) FROM entries GROUP BY party_id") { c -> m[c.getLong(0)] = c.getLong(1) }
        return m
    }

    private fun startOfToday(): Long {
        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun deleteParty(id: Long) {
        db.delete("entries", "party_id=?", arrayOf(id.toString()))
        db.delete("parties", "id=?", arrayOf(id.toString()))
        bump()
    }

    // ----------------------------------------------------------------- entries

    fun addEntry(
        partyId: Long,
        gave: Boolean,
        amount: Double,
        mode: String,
        note: String,
        date: Long,
        dueDate: Long?
    ): Long {
        val cv = ContentValues().apply {
            put("party_id", partyId)
            put("gave", if (gave) 1 else 0)
            put("amount", amount)
            put("mode", mode)
            put("note", note.trim())
            put("date", date)
            if (dueDate != null) put("due_date", dueDate)
        }
        val id = db.insert("entries", null, cv)
        bump()
        return id
    }

    fun deleteEntry(id: Long) {
        db.delete("entries", "id=?", arrayOf(id.toString()))
        bump()
    }

    fun entriesOf(partyId: Long): List<Entry> = query(
        "SELECT id,party_id,gave,amount,mode,note,date,due_date FROM entries " +
            "WHERE party_id=? ORDER BY date ASC, id ASC",
        arrayOf(partyId.toString())
    ) { c ->
        Entry(
            id = c.getLong(0),
            partyId = c.getLong(1),
            gave = c.getInt(2) == 1,
            amount = c.getDouble(3),
            mode = c.getString(4) ?: "CASH",
            note = c.getString(5) ?: "",
            date = c.getLong(6),
            dueDate = if (c.isNull(7)) null else c.getLong(7)
        )
    }

    private fun mapEntryRow(c: Cursor) = EntryRow(
        id = c.getLong(0),
        partyId = c.getLong(1),
        partyName = c.getString(2) ?: "",
        gave = c.getInt(3) == 1,
        amount = c.getDouble(4),
        mode = c.getString(5) ?: "CASH",
        note = c.getString(6) ?: "",
        date = c.getLong(7)
    )

    private const val ENTRY_ROW_SELECT =
        "SELECT e.id, e.party_id, p.name, e.gave, e.amount, e.mode, e.note, e.date " +
            "FROM entries e JOIN parties p ON p.id = e.party_id "

    fun recentEntries(limit: Int = 10): List<EntryRow> = query(
        ENTRY_ROW_SELECT + "ORDER BY e.date DESC, e.id DESC LIMIT $limit"
    ) { mapEntryRow(it) }

    fun entriesBetween(start: Long, end: Long): List<EntryRow> = query(
        ENTRY_ROW_SELECT + "WHERE e.date >= ? AND e.date < ? ORDER BY e.date DESC",
        arrayOf(start.toString(), end.toString())
    ) { mapEntryRow(it) }

    // -------------------------------------------------------------------- cash

    fun cashList(): List<CashEntry> = query(
        "SELECT id,cash_in,amount,category,note,mode,date FROM cash ORDER BY date DESC, id DESC"
    ) { c ->
        CashEntry(
            id = c.getLong(0),
            cashIn = c.getInt(1) == 1,
            amount = c.getDouble(2),
            category = c.getString(3) ?: "General",
            note = c.getString(4) ?: "",
            mode = c.getString(5) ?: "CASH",
            date = c.getLong(6)
        )
    }

    fun addCash(cashIn: Boolean, amount: Double, category: String, note: String, mode: String, date: Long): Long {
        val cv = ContentValues().apply {
            put("cash_in", if (cashIn) 1 else 0)
            put("amount", amount)
            put("category", category)
            put("note", note.trim())
            put("mode", mode)
            put("date", date)
        }
        val id = db.insert("cash", null, cv)
        bump()
        return id
    }

    fun deleteCash(id: Long) {
        db.delete("cash", "id=?", arrayOf(id.toString()))
        bump()
    }

    // ------------------------------------------------------------------- items

    fun items(): List<StockItem> = query(
        "SELECT id,name,unit,qty,purchase_price,sell_price,low_stock FROM items ORDER BY name COLLATE NOCASE"
    ) { c ->
        StockItem(
            id = c.getLong(0),
            name = c.getString(1) ?: "",
            unit = c.getString(2) ?: "pcs",
            qty = c.getDouble(3),
            purchasePrice = c.getDouble(4),
            sellPrice = c.getDouble(5),
            lowStock = c.getDouble(6)
        )
    }

    fun addItem(name: String, unit: String, qty: Double, purchase: Double, sell: Double, low: Double): Long {
        val cv = ContentValues().apply {
            put("name", name.trim())
            put("unit", unit.trim().ifEmpty { "pcs" })
            put("qty", qty)
            put("purchase_price", purchase)
            put("sell_price", sell)
            put("low_stock", low)
        }
        val id = db.insert("items", null, cv)
        bump()
        return id
    }

    fun updateItem(id: Long, name: String, unit: String, qty: Double, purchase: Double, sell: Double, low: Double) {
        val cv = ContentValues().apply {
            put("name", name.trim())
            put("unit", unit.trim().ifEmpty { "pcs" })
            put("qty", qty)
            put("purchase_price", purchase)
            put("sell_price", sell)
            put("low_stock", low)
        }
        db.update("items", cv, "id=?", arrayOf(id.toString()))
        bump()
    }

    fun adjustQty(id: Long, delta: Double) {
        db.execSQL("UPDATE items SET qty = qty + ? WHERE id = ?", arrayOf<Any>(delta, id))
        bump()
    }

    fun deleteItem(id: Long) {
        db.delete("items", "id=?", arrayOf(id.toString()))
        bump()
    }

    // ---------------------------------------------------------------- invoices

    fun invoices(): List<Invoice> = query(
        "SELECT id,number,party_name,phone,date,lines,gst,discount,paid FROM invoices ORDER BY date DESC, id DESC"
    ) { c -> readInvoice(c) }

    /** Real bills only (quotations are not sales). */
    fun bills(): List<Invoice> = invoices().filter { !it.isQuote }

    fun invoice(id: Long): Invoice? = query(
        "SELECT id,number,party_name,phone,date,lines,gst,discount,paid FROM invoices WHERE id=?",
        arrayOf(id.toString())
    ) { c -> readInvoice(c) }.firstOrNull()

    private fun readInvoice(c: Cursor): Invoice {
        val raw = c.getString(5) ?: "[]"
        val lines = ArrayList<InvoiceLine>()
        var meta: JSONObject? = null
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.has("_meta")) { meta = o.optJSONObject("_meta"); continue }
                lines.add(
                    InvoiceLine(
                        name = o.optString("name"),
                        qty = o.optDouble("qty", 0.0),
                        rate = o.optDouble("rate", 0.0),
                        disc = o.optDouble("disc", 0.0).let { if (it.isNaN()) 0.0 else it }
                    )
                )
            }
        } catch (_: Exception) {
        }
        val m = meta
        return Invoice(
            id = c.getLong(0),
            number = c.getString(1) ?: "",
            partyName = c.getString(2) ?: "",
            phone = c.getString(3) ?: "",
            date = c.getLong(4),
            lines = lines,
            gstPercent = c.getDouble(6),
            discount = c.getDouble(7),
            paid = c.getInt(8) == 1,
            type = m?.optString("type", "BILL")?.ifBlank { "BILL" } ?: "BILL",
            received = m?.optDouble("received", 0.0)?.let { if (it.isNaN()) 0.0 else it } ?: 0.0,
            payMode = m?.optString("mode", "CASH")?.ifBlank { "CASH" } ?: "CASH",
            note = m?.optString("note", "") ?: "",
            convertedTo = m?.optString("conv", "") ?: ""
        )
    }

    /** Lines + extra bill fields as one JSON array; extras ride in a trailing {"_meta":{...}} item. */
    private fun linesJson(inv: Invoice): String {
        val arr = JSONArray()
        inv.lines.forEach { l ->
            arr.put(JSONObject().apply {
                put("name", l.name)
                put("qty", l.qty)
                put("rate", l.rate)
                if (l.disc > 0) put("disc", l.disc)
            })
        }
        if (inv.type != "BILL" || inv.received > 0 || inv.payMode != "CASH" || inv.note.isNotBlank() || inv.convertedTo.isNotBlank()) {
            arr.put(JSONObject().put("_meta", JSONObject().apply {
                put("type", inv.type)
                put("received", inv.received)
                put("mode", inv.payMode)
                if (inv.note.isNotBlank()) put("note", inv.note)
                if (inv.convertedTo.isNotBlank()) put("conv", inv.convertedTo)
            }))
        }
        return arr.toString()
    }

    fun addInvoice(inv: Invoice): Long {
        val cv = ContentValues().apply {
            put("number", inv.number)
            put("party_name", inv.partyName)
            put("phone", inv.phone)
            put("date", inv.date)
            put("lines", linesJson(inv))
            put("gst", inv.gstPercent)
            put("discount", inv.discount)
            put("paid", if (inv.paid) 1 else 0)
        }
        val id = db.insert("invoices", null, cv)
        bump()
        return id
    }

    /** Rewrites the extra fields (payment, type, note) of an existing bill. */
    fun updateInvoiceMeta(inv: Invoice) {
        val cv = ContentValues().apply {
            put("lines", linesJson(inv))
            put("paid", if (inv.paid) 1 else 0)
        }
        db.update("invoices", cv, "id=?", arrayOf(inv.id.toString()))
        bump()
    }

    /** Adds a payment to a bill; marks it paid when fully received. Returns the updated bill. */
    fun addInvoicePayment(id: Long, amount: Double, mode: String): Invoice? {
        val inv = invoice(id) ?: return null
        val got = (inv.received + amount).coerceAtMost(inv.total)
        val up = inv.copy(received = got, payMode = mode, paid = got >= inv.total - 0.005)
        updateInvoiceMeta(up)
        return up
    }

    /** Recently billed items with their last rate, newest first (a free "catalog"). */
    fun recentBillItems(limit: Int = 12): List<InvoiceLine> {
        val seen = LinkedHashMap<String, InvoiceLine>()
        for (inv in invoices()) for (l in inv.lines) {
            val k = l.name.trim().lowercase()
            if (k.isNotEmpty() && k !in seen) seen[k] = l.copy(qty = 1.0)
            if (seen.size >= limit) return seen.values.toList()
        }
        return seen.values.toList()
    }

    fun nextQuoteNumber(): String {
        // max existing QT number + 1, so numbers never repeat after a delete
        val n = (invoices().filter { it.isQuote }.maxOfOrNull { q ->
            Regex("(\\d+)\\D*$").find(q.number)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        } ?: 0) + 1
        return "QT-" + n.toString().padStart(4, '0')
    }

    fun setInvoicePaid(id: Long, paid: Boolean) {
        val inv = invoice(id)
        if (inv != null && (inv.received > 0 || inv.type != "BILL")) {
            updateInvoiceMeta(inv.copy(paid = paid, received = if (paid) inv.total else 0.0))
            return
        }
        val cv = ContentValues().apply { put("paid", if (paid) 1 else 0) }
        db.update("invoices", cv, "id=?", arrayOf(id.toString()))
        bump()
    }

    fun updateEntry(id: Long, gave: Boolean, amount: Double, mode: String, note: String, date: Long, dueDate: Long?) {
        val cv = ContentValues().apply {
            put("gave", if (gave) 1 else 0)
            put("amount", amount)
            put("mode", mode)
            put("note", note.trim())
            put("date", date)
            if (dueDate != null) put("due_date", dueDate) else putNull("due_date")
        }
        db.update("entries", cv, "id=?", arrayOf(id.toString()))
        bump()
    }

    fun deleteInvoice(id: Long) {
        db.delete("invoices", "id=?", arrayOf(id.toString()))
        bump()
    }

    fun nextInvoiceNumber(): String {
        val count = query("SELECT COUNT(*) FROM invoices") { it.getInt(0) }.firstOrNull() ?: 0
        val maxId = query("SELECT IFNULL(MAX(id),0) FROM invoices") { it.getLong(0) }.firstOrNull() ?: 0L
        val n = maxOf(count.toLong(), maxId) + 1
        val prefix = setting("invoice_prefix", "INV-")
        return prefix + n.toString().padStart(4, '0')
    }

    // -------------------------------------------------------- backup / restore

    fun exportJson(): JSONObject {
        val root = JSONObject()
        root.put("app", "BahiKhata")
        root.put("version", 1)
        root.put("exported_at", System.currentTimeMillis())

        val partyArr = JSONArray()
        parties().forEach { p ->
            partyArr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("phone", p.phone)
                put("type", p.type)
                put("opening", p.opening)
                put("note", p.note)
                put("created_at", p.createdAt)
            })
        }
        root.put("parties", partyArr)

        val entryArr = JSONArray()
        query("SELECT id,party_id,gave,amount,mode,note,date,due_date FROM entries") { c ->
            entryArr.put(JSONObject().apply {
                put("id", c.getLong(0))
                put("party_id", c.getLong(1))
                put("gave", c.getInt(2) == 1)
                put("amount", c.getDouble(3))
                put("mode", c.getString(4) ?: "CASH")
                put("note", c.getString(5) ?: "")
                put("date", c.getLong(6))
                if (!c.isNull(7)) put("due_date", c.getLong(7))
            })
        }
        root.put("entries", entryArr)

        val cashArr = JSONArray()
        cashList().forEach { e ->
            cashArr.put(JSONObject().apply {
                put("id", e.id)
                put("cash_in", e.cashIn)
                put("amount", e.amount)
                put("category", e.category)
                put("note", e.note)
                put("mode", e.mode)
                put("date", e.date)
            })
        }
        root.put("cash", cashArr)

        val itemArr = JSONArray()
        items().forEach { i ->
            itemArr.put(JSONObject().apply {
                put("id", i.id)
                put("name", i.name)
                put("unit", i.unit)
                put("qty", i.qty)
                put("purchase_price", i.purchasePrice)
                put("sell_price", i.sellPrice)
                put("low_stock", i.lowStock)
            })
        }
        root.put("items", itemArr)

        val invArr = JSONArray()
        invoices().forEach { inv ->
            val lines = JSONArray(linesJson(inv))
            invArr.put(JSONObject().apply {
                put("id", inv.id)
                put("number", inv.number)
                put("party_name", inv.partyName)
                put("phone", inv.phone)
                put("date", inv.date)
                put("lines", lines)
                put("gst", inv.gstPercent)
                put("discount", inv.discount)
                put("paid", inv.paid)
            })
        }
        root.put("invoices", invArr)

        val settingsArr = JSONArray()
        query("SELECT k, v FROM settings") { c ->
            settingsArr.put(JSONObject().apply {
                put("k", c.getString(0))
                put("v", c.getString(1) ?: "")
            })
        }
        root.put("settings", settingsArr)

        return root
    }

    /** Restores a backup. Returns true on success. Existing data is replaced. */
    fun importJson(root: JSONObject): Boolean {
        return try {
            db.beginTransaction()

            db.delete("parties", null, null)
            db.delete("entries", null, null)
            db.delete("cash", null, null)
            db.delete("items", null, null)
            db.delete("invoices", null, null)

            val ps = root.optJSONArray("parties") ?: JSONArray()
            for (i in 0 until ps.length()) {
                val o = ps.getJSONObject(i)
                val cv = ContentValues().apply {
                    put("id", o.optLong("id"))
                    put("name", o.optString("name"))
                    put("phone", o.optString("phone"))
                    put("type", o.optString("type", "CUSTOMER"))
                    put("opening", o.optDouble("opening", 0.0))
                    put("note", o.optString("note"))
                    put("created_at", o.optLong("created_at", System.currentTimeMillis()))
                }
                db.insert("parties", null, cv)
            }

            val es = root.optJSONArray("entries") ?: JSONArray()
            for (i in 0 until es.length()) {
                val o = es.getJSONObject(i)
                val cv = ContentValues().apply {
                    put("id", o.optLong("id"))
                    put("party_id", o.optLong("party_id"))
                    put("gave", if (o.optBoolean("gave")) 1 else 0)
                    put("amount", o.optDouble("amount", 0.0))
                    put("mode", o.optString("mode", "CASH"))
                    put("note", o.optString("note"))
                    put("date", o.optLong("date", System.currentTimeMillis()))
                    if (o.has("due_date")) put("due_date", o.optLong("due_date"))
                }
                db.insert("entries", null, cv)
            }

            val cs = root.optJSONArray("cash") ?: JSONArray()
            for (i in 0 until cs.length()) {
                val o = cs.getJSONObject(i)
                val cv = ContentValues().apply {
                    put("id", o.optLong("id"))
                    put("cash_in", if (o.optBoolean("cash_in")) 1 else 0)
                    put("amount", o.optDouble("amount", 0.0))
                    put("category", o.optString("category", "General"))
                    put("note", o.optString("note"))
                    put("mode", o.optString("mode", "CASH"))
                    put("date", o.optLong("date", System.currentTimeMillis()))
                }
                db.insert("cash", null, cv)
            }

            val is0 = root.optJSONArray("items") ?: JSONArray()
            for (i in 0 until is0.length()) {
                val o = is0.getJSONObject(i)
                val cv = ContentValues().apply {
                    put("id", o.optLong("id"))
                    put("name", o.optString("name"))
                    put("unit", o.optString("unit", "pcs"))
                    put("qty", o.optDouble("qty", 0.0))
                    put("purchase_price", o.optDouble("purchase_price", 0.0))
                    put("sell_price", o.optDouble("sell_price", 0.0))
                    put("low_stock", o.optDouble("low_stock", 5.0))
                }
                db.insert("items", null, cv)
            }

            val ivs = root.optJSONArray("invoices") ?: JSONArray()
            for (i in 0 until ivs.length()) {
                val o = ivs.getJSONObject(i)
                val cv = ContentValues().apply {
                    put("id", o.optLong("id"))
                    put("number", o.optString("number"))
                    put("party_name", o.optString("party_name"))
                    put("phone", o.optString("phone"))
                    put("date", o.optLong("date", System.currentTimeMillis()))
                    put("lines", (o.optJSONArray("lines") ?: JSONArray()).toString())
                    put("gst", o.optDouble("gst", 0.0))
                    put("discount", o.optDouble("discount", 0.0))
                    put("paid", if (o.optBoolean("paid")) 1 else 0)
                }
                db.insert("invoices", null, cv)
            }

            val ss = root.optJSONArray("settings") ?: JSONArray()
            for (i in 0 until ss.length()) {
                val o = ss.getJSONObject(i)
                val cv = ContentValues().apply {
                    put("k", o.optString("k"))
                    put("v", o.optString("v"))
                }
                db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }

            db.setTransactionSuccessful()
            db.endTransaction()
            bump()
            true
        } catch (e: Exception) {
            try {
                db.endTransaction()
            } catch (_: Exception) {
            }
            false
        }
    }


    // ------------------------------------------------------------ dashboard (v1.0.40)

    /** Oldest passed due date per party (only "Diye" entries with a due date before today). */
    fun overdueSince(): Map<Long, Long> {
        val m = HashMap<Long, Long>()
        query(
            "SELECT party_id, MIN(due_date) FROM entries WHERE gave=1 AND due_date IS NOT NULL AND due_date > 0 AND due_date < ? GROUP BY party_id",
            arrayOf(startOfToday().toString())
        ) { c -> m[c.getLong(0)] = c.getLong(1) }
        return m
    }

    /** Latest "Mile" (payment received) date per party. */
    fun lastPaymentDates(): Map<Long, Long> {
        val m = HashMap<Long, Long>()
        query("SELECT party_id, MAX(date) FROM entries WHERE gave=0 GROUP BY party_id") { c -> m[c.getLong(0)] = c.getLong(1) }
        return m
    }

    /** Earliest entry date per party. */
    fun firstEntryDates(): Map<Long, Long> {
        val m = HashMap<Long, Long>()
        query("SELECT party_id, MIN(date) FROM entries GROUP BY party_id") { c -> m[c.getLong(0)] = c.getLong(1) }
        return m
    }

    /** Khata entries with their payment mode, in a time window (for mode split). */
    fun entriesFull(start: Long, end: Long): List<Entry> = query(
        "SELECT id,party_id,gave,amount,mode,note,date,due_date FROM entries WHERE date >= ? AND date < ? ORDER BY date DESC",
        arrayOf(start.toString(), end.toString())
    ) { c ->
        Entry(
            id = c.getLong(0), partyId = c.getLong(1), gave = c.getInt(2) == 1, amount = c.getDouble(3),
            mode = c.getString(4) ?: "CASH", note = c.getString(5) ?: "", date = c.getLong(6),
            dueDate = if (c.isNull(7)) null else c.getLong(7)
        )
    }

    fun wipeAll() {
        db.delete("parties", null, null)
        db.delete("entries", null, null)
        db.delete("cash", null, null)
        db.delete("items", null, null)
        db.delete("invoices", null, null)
        bump()
    }
}
