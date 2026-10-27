package com.bahikhata.app.ui

import android.app.Activity
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.bahikhata.app.data.Repo
import com.bahikhata.app.util.UiPrefs

private fun fontFamily(style: String): FontFamily = when (style) {
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    "condensed" -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
    else -> FontFamily.Default
}

private fun iosTypography(ff: FontFamily): Typography {
    fun s(size: Int, w: FontWeight = FontWeight.Normal, ls: Double = -0.2, lh: Int = (size * 1.3).toInt()) =
        TextStyle(fontFamily = ff, fontSize = size.sp, fontWeight = w, letterSpacing = ls.sp, lineHeight = lh.sp)
    return Typography(
        displayLarge = s(40, FontWeight.Bold), displayMedium = s(36, FontWeight.Bold), displaySmall = s(34, FontWeight.Bold),
        headlineLarge = s(34, FontWeight.Bold, 0.2), headlineMedium = s(28, FontWeight.Bold, 0.2), headlineSmall = s(22, FontWeight.Bold),
        titleLarge = s(20, FontWeight.SemiBold), titleMedium = s(17, FontWeight.SemiBold), titleSmall = s(15, FontWeight.SemiBold),
        bodyLarge = s(17), bodyMedium = s(15), bodySmall = s(13, ls = -0.08),
        labelLarge = s(15, FontWeight.Medium), labelMedium = s(13, ls = -0.08), labelSmall = s(11, ls = 0.06)
    )
}

/** theme setting: "system" | "light" | "dark"; dynamic setting: "1" to use the wallpaper colour as tint. */
@Composable
fun BahiTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val t = Repo.tick
    androidx.compose.runtime.remember(t) { com.bahikhata.app.util.S.refreshGlobals() }
    val mode = Repo.setting("theme", "system")
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val dynamic = Repo.setting("dynamic", "0") == "1" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val ctx = LocalContext.current
    val tint = if (dynamic) (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)).primary
    else accentColor(UiPrefs.accent, dark)
    val c = iosColors(dark, tint)
    val container = tint.copy(alpha = 0.15f).compositeOver(c.cell)
    val colors = if (dark) darkColorScheme(
        primary = tint, onPrimary = Color.White, primaryContainer = container, onPrimaryContainer = tint,
        secondary = tint, onSecondary = Color.White, secondaryContainer = Color(0xFF3A3A3C), onSecondaryContainer = Color.White,
        tertiary = Color(0xFFFF9F0A), onTertiary = Color.Black, tertiaryContainer = Color(0xFF3A2A10), onTertiaryContainer = Color(0xFFFF9F0A),
        background = c.groupedBg, onBackground = Color.White, surface = c.cell, onSurface = Color.White,
        surfaceVariant = Color(0xFF2C2C2E), onSurfaceVariant = Color(0xFF98989F), surfaceTint = Color.Transparent,
        surfaceContainerLowest = Color.Black, surfaceContainerLow = c.cell, surfaceContainer = c.cell,
        surfaceContainerHigh = Color(0xFF2C2C2E), surfaceContainerHighest = Color(0xFF3A3A3C),
        outline = Color(0xFF545458), outlineVariant = c.separator,
        error = Color(0xFFFF453A), onError = Color.White, errorContainer = Color(0xFF3B1513), onErrorContainer = Color(0xFFFF453A)
    ) else lightColorScheme(
        primary = tint, onPrimary = Color.White, primaryContainer = container, onPrimaryContainer = tint,
        secondary = tint, onSecondary = Color.White, secondaryContainer = Color(0xFFE5E5EA), onSecondaryContainer = Color.Black,
        tertiary = Color(0xFFFF9500), onTertiary = Color.White, tertiaryContainer = Color(0xFFFFF0DB), onTertiaryContainer = Color(0xFF8A4F00),
        background = c.groupedBg, onBackground = Color.Black, surface = c.cell, onSurface = Color.Black,
        surfaceVariant = Color(0xFFE5E5EA), onSurfaceVariant = Color(0xFF8A8A8E), surfaceTint = Color.Transparent,
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color.White,
        surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFE5E5EA),
        outline = Color(0xFFC6C6C8), outlineVariant = Color(0xFFE5E5EA),
        error = Color(0xFFFF3B30), onError = Color.White, errorContainer = Color(0xFFFFE5E3), onErrorContainer = Color(0xFFD70015)
    )
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val ctrl = WindowCompat.getInsetsController(window, view)
            ctrl.isAppearanceLightStatusBars = !dark
            ctrl.isAppearanceLightNavigationBars = !dark
            // Settings → Security: block screenshots / hide in recents, keep the screen on
            val wf = android.view.WindowManager.LayoutParams.FLAG_SECURE
            if (com.bahikhata.app.util.S.secureScreen) window.addFlags(wf) else window.clearFlags(wf)
            val ko = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            if (com.bahikhata.app.util.S.keepScreenOn) window.addFlags(ko) else window.clearFlags(ko)
        }
    }
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(14.dp), extraLarge = RoundedCornerShape(14.dp)
    )
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalIos provides c,
        LocalDensity provides Density(d.density, d.fontScale * UiPrefs.textScale)
    ) {
        MaterialTheme(colorScheme = colors, typography = iosTypography(fontFamily(UiPrefs.fontStyle)), shapes = shapes, content = content)
    }
}
