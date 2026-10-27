package com.bahikhata.app.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Look-and-feel preferences. Stored only on this phone (SharedPreferences), never synced.
 */
object UiPrefs {
    private var sp: SharedPreferences? = null

    /** "s" | "m" | "l" | "xl" */
    var textSize by mutableStateOf("m"); private set
    /** "system" | "serif" | "condensed" | "mono" */
    var fontStyle by mutableStateOf("system"); private set
    /** "blue" | "green" | "orange" | "purple" | "pink" */
    var accent by mutableStateOf("blue"); private set
    var currency by mutableStateOf("₹"); private set
    /** true = 1,00,000 (Indian), false = 100,000 */
    var indianGrouping by mutableStateOf(true); private set
    var haptics by mutableStateOf(true); private set
    var hideAmounts by mutableStateOf(false); private set

    fun init(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
        sp = p
        textSize = p.getString("textSize", "m") ?: "m"
        fontStyle = p.getString("fontStyle", "system") ?: "system"
        accent = p.getString("accent", "blue") ?: "blue"
        currency = p.getString("currency", "₹") ?: "₹"
        indianGrouping = p.getBoolean("indianGrouping", true)
        haptics = p.getBoolean("haptics", true)
        hideAmounts = p.getBoolean("hideAmounts", false)
        NumFmt.symbol = currency
        NumFmt.indian = indianGrouping
    }

    fun changeTextSize(v: String) { textSize = v; sp?.edit()?.putString("textSize", v)?.apply() }
    fun changeFontStyle(v: String) { fontStyle = v; sp?.edit()?.putString("fontStyle", v)?.apply() }
    fun changeAccent(v: String) { accent = v; sp?.edit()?.putString("accent", v)?.apply() }
    fun changeCurrency(v: String) { currency = v; NumFmt.symbol = v; sp?.edit()?.putString("currency", v)?.apply() }
    fun changeIndianGrouping(v: Boolean) { indianGrouping = v; NumFmt.indian = v; sp?.edit()?.putBoolean("indianGrouping", v)?.apply() }
    fun changeHaptics(v: Boolean) { haptics = v; sp?.edit()?.putBoolean("haptics", v)?.apply() }
    fun changeHideAmounts(v: Boolean) { hideAmounts = v; sp?.edit()?.putBoolean("hideAmounts", v)?.apply() }

    val textScale: Float
        get() = when (textSize) { "s" -> 0.88f; "l" -> 1.12f; "xl" -> 1.25f; else -> 1f }
}

/** Number format used by money()/rupees(). Defaults keep the old behaviour (₹, Indian grouping). */
object NumFmt {
    @Volatile var symbol: String = "₹"
    @Volatile var indian: Boolean = true
    @Volatile var datePattern: String = "dd MMM yyyy"
    @Volatile var time24: Boolean = false
    /** false = amounts shown rounded to whole rupees (display only; saved values are unchanged). */
    @Volatile var paise: Boolean = true
}
