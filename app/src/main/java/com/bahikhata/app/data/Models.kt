package com.bahikhata.app.data

/** A customer or a supplier. */
data class Party(
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val type: String = "CUSTOMER", // CUSTOMER | SUPPLIER
    val opening: Double = 0.0,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val balance: Double = 0.0
)

/** A khata entry. gave = true means "You Gave" (party owes you). */
data class Entry(
    val id: Long = 0,
    val partyId: Long,
    val gave: Boolean,
    val amount: Double,
    val mode: String = "CASH",
    val note: String = "",
    val date: Long = System.currentTimeMillis(),
    val dueDate: Long? = null
)

/** Entry joined with the party name, used in lists. */
data class EntryRow(
    val id: Long,
    val partyId: Long,
    val partyName: String,
    val gave: Boolean,
    val amount: Double,
    val mode: String,
    val note: String,
    val date: Long
)

/** A cashbook row (cash in / cash out, not tied to a party). */
data class CashEntry(
    val id: Long = 0,
    val cashIn: Boolean,
    val amount: Double,
    val category: String = "General",
    val note: String = "",
    val mode: String = "CASH",
    val date: Long = System.currentTimeMillis()
)

/** A stock item. */
data class StockItem(
    val id: Long = 0,
    val name: String,
    val unit: String = "pcs",
    val qty: Double = 0.0,
    val purchasePrice: Double = 0.0,
    val sellPrice: Double = 0.0,
    val lowStock: Double = 5.0
)

data class InvoiceLine(
    val name: String,
    val qty: Double,
    val rate: Double
)

data class Invoice(
    val id: Long = 0,
    val number: String,
    val partyName: String,
    val phone: String = "",
    val date: Long = System.currentTimeMillis(),
    val lines: List<InvoiceLine> = emptyList(),
    val gstPercent: Double = 0.0,
    val discount: Double = 0.0,
    val paid: Boolean = false
) {
    val subtotal: Double get() = lines.sumOf { it.qty * it.rate }
    val gstAmount: Double get() = (subtotal - discount).coerceAtLeast(0.0) * gstPercent / 100.0
    val total: Double get() = (subtotal - discount).coerceAtLeast(0.0) + gstAmount
}
