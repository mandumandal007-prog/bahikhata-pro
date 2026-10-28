package com.bahikhata.app.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * In-app language. "hinglish" (default) shows the app's own text exactly as written.
 * Other languages translate each on-screen text at draw time from assets/i18n/<code>.json
 * (exact sentence first, then known phrases inside longer text). Data, keys and saved values never change.
 */
object Lang {
    data class Option(val code: String, val label: String, val sub: String)
    val OPTIONS = listOf(
        Option("hinglish", "Hinglish", "Jaisa abhi hai (default)"),
        Option("hi", "हिंदी", "Hindi"),
        Option("en", "English", "English"),
        Option("bn", "বাংলা", "Bengali"),
        Option("mr", "मराठी", "Marathi"),
        Option("gu", "ગુજરાતી", "Gujarati"),
    )

    var code by mutableStateOf("hinglish"); private set
    private var app: Context? = null
    private val dicts = HashMap<String, Map<String, String>>()
    private val phraseLists = HashMap<String, List<Pair<String, String>>>()
    private val cache = HashMap<String, HashMap<String, String>>()

    fun init(ctx: Context) {
        app = ctx.applicationContext
        code = ctx.applicationContext.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE).getString("lang", "hinglish") ?: "hinglish"
    }

    fun change(ctx: Context, v: String) {
        code = v
        ctx.applicationContext.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE).edit().putString("lang", v).apply()
    }

    private fun dict(c: String): Map<String, String> = dicts.getOrPut(c) {
        try {
            val txt = app!!.assets.open("i18n/$c.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val o = JSONObject(txt)
            val m = HashMap<String, String>(o.length() * 2)
            o.keys().forEach { k -> val v = o.optString(k); if (v.isNotEmpty()) m[k] = v }
            m
        } catch (e: Exception) { emptyMap() }
    }

    private fun phrases(c: String): List<Pair<String, String>> = phraseLists.getOrPut(c) {
        dict(c).entries.filter { it.key.length >= 6 && (it.key.contains(' ') || it.key.length >= 9) }
            .sortedByDescending { it.key.length }.map { it.key to it.value }
    }

    /** Translate one on-screen text for the chosen language. */
    fun tr(s: String): String {
        val c = code
        if (c == "hinglish" || s.isBlank() || app == null) return s
        val cc = cache.getOrPut(c) { HashMap() }
        cc[s]?.let { return it }
        val d = dict(c)
        val trimmed = s.trim()
        val out = d[s] ?: d[trimmed]?.let { s.replace(trimmed, it) } ?: run {
            var r = s
            if (s.length <= 400) for ((k, v) in phrases(c)) if (r.contains(k)) r = r.replace(k, v)
            r
        }
        if (cc.size < 5000) cc[s] = out
        return out
    }
}

/** Short helper for non-Text places (toasts, dialogs built from strings). */
fun tr(s: String): String = Lang.tr(s)
