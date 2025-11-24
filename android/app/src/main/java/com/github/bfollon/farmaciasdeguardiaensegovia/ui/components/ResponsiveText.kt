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
