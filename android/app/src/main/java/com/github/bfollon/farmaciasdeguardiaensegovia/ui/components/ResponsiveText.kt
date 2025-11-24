/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.bfollon.farmaciasdeguardiaensegovia.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Screen width breakpoints for responsive design
 * Based on Material Design 3 window size classes
 */
object ResponsiveBreakpoints {
    val COMPACT_THRESHOLD = 360.dp  // Very narrow phones
    val MEDIUM_THRESHOLD = 400.dp   // Standard phones
    // Anything >= MEDIUM_THRESHOLD is considered expanded
}

/**
 * Calculates a responsive text size based on screen width breakpoints
 *
 * @param compactSize Size for screens < 360dp width (very narrow)
 * @param mediumSize Size for screens 360-400dp width (standard phones)
 * @param expandedSize Size for screens >= 400dp width (large phones, tablets)
 * @return The appropriate TextUnit for current screen width
 */
@Composable
fun responsiveTextSize(
    compactSize: TextUnit,
    mediumSize: TextUnit,
    expandedSize: TextUnit
): TextUnit {
    val configuration = LocalConfiguration.current
    return when {
        configuration.screenWidthDp < ResponsiveBreakpoints.COMPACT_THRESHOLD.value -> compactSize
        configuration.screenWidthDp < ResponsiveBreakpoints.MEDIUM_THRESHOLD.value -> mediumSize
        else -> expandedSize
    }
}

/**
 * Calculates a responsive text size using a scaling factor approach
 * Useful when you want proportional scaling rather than discrete sizes
 *
 * @param baseSize The base text size (used for expanded screens)
 * @param compactScale Scale factor for compact screens (default: 0.8)
 * @param mediumScale Scale factor for medium screens (default: 0.9)
 * @return The scaled TextUnit for current screen width
 */
@Composable
fun responsiveScaledTextSize(
    baseSize: TextUnit,
    compactScale: Float = 0.8f,
    mediumScale: Float = 0.9f
): TextUnit {
    val configuration = LocalConfiguration.current
    val scale = when {
        configuration.screenWidthDp < ResponsiveBreakpoints.COMPACT_THRESHOLD.value -> compactScale
        configuration.screenWidthDp < ResponsiveBreakpoints.MEDIUM_THRESHOLD.value -> mediumScale
        else -> 1.0f
    }
    return baseSize * scale
}

/**
 * Calculates a responsive dimension (Dp) based on screen width breakpoints
 * Useful for heights, widths, padding, margins, etc.
 *
 * @param compactSize Size for screens < 360dp width (very narrow)
 * @param mediumSize Size for screens 360-400dp width (standard phones)
 * @param expandedSize Size for screens >= 400dp width (large phones, tablets)
 * @return The appropriate Dp value for current screen width
 */
@Composable
fun responsiveDimension(
    compactSize: androidx.compose.ui.unit.Dp,
    mediumSize: androidx.compose.ui.unit.Dp,
    expandedSize: androidx.compose.ui.unit.Dp
): androidx.compose.ui.unit.Dp {
    val configuration = LocalConfiguration.current
    return when {
        configuration.screenWidthDp < ResponsiveBreakpoints.COMPACT_THRESHOLD.value -> compactSize
        configuration.screenWidthDp < ResponsiveBreakpoints.MEDIUM_THRESHOLD.value -> mediumSize
        else -> expandedSize
    }
}

/**
 * A responsive Text composable that automatically adjusts font size based on screen width
 *
 * Usage:
 * ```
 * ResponsiveText(
 *     text = "My Title",
 *     compactSize = 24.sp,
 *     mediumSize = 28.sp,
 *     expandedSize = 32.sp,
 *     fontWeight = FontWeight.Bold
 * )
 * ```
 *
 * @param text The text to display
 * @param compactSize Font size for screens < 360dp width
 * @param mediumSize Font size for screens 360-400dp width
 * @param expandedSize Font size for screens >= 400dp width
 * @param modifier Modifier to be applied to the Text
 * @param color Text color
 * @param brush Gradient brush for text (takes precedence over color if specified)
 * @param fontStyle Font style (normal, italic)
 * @param fontWeight Font weight
 * @param fontFamily Font family
 * @param letterSpacing Letter spacing
 * @param textDecoration Text decoration (underline, strikethrough)
 * @param textAlign Text alignment
 * @param lineHeight Line height
 * @param overflow Text overflow behavior
 * @param softWrap Whether text should soft wrap
 * @param maxLines Maximum number of lines
 * @param minLines Minimum number of lines
 */
@Composable
fun ResponsiveText(
    text: String,
    compactSize: TextUnit,
    mediumSize: TextUnit,
    expandedSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    brush: Brush? = null,
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
    style: TextStyle = LocalTextStyle.current
) {
    val fontSize = responsiveTextSize(
        compactSize = compactSize,
        mediumSize = mediumSize,
        expandedSize = expandedSize
    )

    val textStyle = if (brush != null) {
        style.copy(
            fontSize = fontSize,
            fontStyle = fontStyle ?: style.fontStyle,
            fontWeight = fontWeight ?: style.fontWeight,
            fontFamily = fontFamily ?: style.fontFamily,
            letterSpacing = if (letterSpacing != TextUnit.Unspecified) letterSpacing else style.letterSpacing,
            textDecoration = textDecoration ?: style.textDecoration,
            textAlign = textAlign ?: style.textAlign,
            lineHeight = if (lineHeight != TextUnit.Unspecified) lineHeight else style.lineHeight,
            brush = brush
        )
    } else {
        style.copy(
            fontSize = fontSize,
            color = if (color != Color.Unspecified) color else style.color,
            fontStyle = fontStyle ?: style.fontStyle,
            fontWeight = fontWeight ?: style.fontWeight,
            fontFamily = fontFamily ?: style.fontFamily,
            letterSpacing = if (letterSpacing != TextUnit.Unspecified) letterSpacing else style.letterSpacing,
            textDecoration = textDecoration ?: style.textDecoration,
            textAlign = textAlign ?: style.textAlign,
            lineHeight = if (lineHeight != TextUnit.Unspecified) lineHeight else style.lineHeight
        )
    }

    Text(
        text = text,
        modifier = modifier,
        style = textStyle,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines
    )
}
