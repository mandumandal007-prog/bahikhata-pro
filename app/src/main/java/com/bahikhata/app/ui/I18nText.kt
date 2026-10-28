package com.bahikhata.app.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.bahikhata.app.util.Lang

/**
 * Every plain-string Text in the app's screens resolves here first (same package beats the material3 star import):
 *  - the string is shown in the chosen language (Lang.tr; Hinglish = unchanged)
 *  - a single-line text never cuts silently: it ends with "…" instead of being chopped.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    @Suppress("UNUSED_VARIABLE") val lang = Lang.code
    androidx.compose.material3.Text(
        Lang.tr(text), modifier, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration,
        textAlign, lineHeight,
        if (maxLines <= 2 && overflow == TextOverflow.Clip) TextOverflow.Ellipsis else overflow,
        softWrap, maxLines, minLines, onTextLayout ?: {}, style
    )
}

/** A fixed height that grows with the phone's font size, so text inside never gets cut. */
@Composable
fun fitDp(base: Dp): Dp = base * maxOf(1f, LocalDensity.current.fontScale)
