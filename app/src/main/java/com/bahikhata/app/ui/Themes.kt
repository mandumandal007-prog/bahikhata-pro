package com.bahikhata.app.ui

import androidx.compose.ui.graphics.Color

/** Full-app colour themes (Settings > Dikhawat). "classic" = the old look with the accent colour picker. */
data class AppThemePreset(
    val key: String, val name: String,
    val tintLight: Color, val tintDark: Color,
    val bgLight: Color, val bgDark: Color,
    val cellDark: Color? = null,
    val forceDark: Boolean = false,
)

val APP_THEMES = listOf(
    AppThemePreset("classic", "Classic", Color(0xFF007AFF), Color(0xFF0A84FF), Color(0xFFF2F2F7), Color.Black),
    AppThemePreset("ocean", "Ocean Blue", Color(0xFF0A66C2), Color(0xFF4DA3FF), Color(0xFFE8F1FB), Color(0xFF050B14), Color(0xFF111A26)),
    AppThemePreset("emerald", "Emerald Green", Color(0xFF0E9F5A), Color(0xFF34D27B), Color(0xFFE7F5EE), Color(0xFF04110A), Color(0xFF0F1F17)),
    AppThemePreset("royal", "Royal Purple", Color(0xFF6A3FC8), Color(0xFFB18CFF), Color(0xFFF0EBFA), Color(0xFF0C0716), Color(0xFF1B1428)),
    AppThemePreset("sunset", "Sunset Orange", Color(0xFFE2580C), Color(0xFFFF8A3D), Color(0xFFFDF0E6), Color(0xFF140A04), Color(0xFF241810)),
    AppThemePreset("rose", "Rose Pink", Color(0xFFD02A68), Color(0xFFFF6B9A), Color(0xFFFCECF2), Color(0xFF14060B), Color(0xFF261219)),
    AppThemePreset("peacock", "Mor Teal", Color(0xFF00897B), Color(0xFF2ED3C2), Color(0xFFE6F4F2), Color(0xFF031110), Color(0xFF0F201E)),
    AppThemePreset("gold", "Royal Gold", Color(0xFFB7791F), Color(0xFFF2B84B), Color(0xFFFBF4E6), Color(0xFF130E04), Color(0xFF231B0E)),
    AppThemePreset("amoled", "Dark AMOLED", Color(0xFF0A84FF), Color(0xFF0A84FF), Color.Black, Color.Black, Color(0xFF0E0E0E), forceDark = true),
)

fun appTheme(key: String): AppThemePreset = APP_THEMES.firstOrNull { it.key == key } ?: APP_THEMES[0]

/** Apply a preset on top of the iOS palette. */
fun IosColors.withPreset(p: AppThemePreset): IosColors {
    if (p.key == "classic") return this
    return if (!dark) copy(groupedBg = p.bgLight, sheetBg = p.bgLight, barBg = p.bgLight.copy(alpha = 0.95f))
    else {
        val cellC = p.cellDark ?: cell
        copy(groupedBg = p.bgDark, sheetBg = cellC, cell = cellC, barBg = p.bgDark.copy(alpha = 0.95f),
            alertBg = if (p.key == "amoled") Color(0xFF1A1A1A) else alertBg)
    }
}
