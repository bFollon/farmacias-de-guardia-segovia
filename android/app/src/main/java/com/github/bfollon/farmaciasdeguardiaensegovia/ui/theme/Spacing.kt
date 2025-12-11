/*
 * Copyright (C) 2024 Bruno Follón
 *
 * This file is part of Farmacias de Guardia Segovia.
 *
 * Farmacias de Guardia Segovia is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Farmacias de Guardia Segovia is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Farmacias de Guardia Segovia. If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.bfollon.farmaciasdeguardiaensegovia.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Centralized spacing constants for consistent padding and spacing throughout the app.
 *
 * Usage:
 * - XSmall: Minimal spacing (dividers, small gaps between elements)
 * - Small: Reduced spacing for dense layouts
 * - Medium: Component internal padding (cards, buttons)
 * - Base: Standard screen/section horizontal padding (PRIMARY - most common)
 * - Large: Large sections or special spacing needs
 * - XLarge: Bottom sheets, modals, settings screens
 * - XXLarge: Maximum spacing (splash screens, hero sections)
 * - XXXLarge: Very large spacing (when extra breathing room needed)
 */
object Spacing {
    /** Minimal spacing: 4dp - Used for dividers and minimal gaps */
    val XSmall = 4.dp

    /** Reduced spacing: 8dp - Used for dense layouts */
    val Small = 8.dp

    /** Component internal padding: 12dp - Used inside cards, buttons, etc. */
    val Medium = 12.dp

    /** Standard screen padding: 16dp - PRIMARY spacing for most screens */
    val Base = 16.dp

    /** Large sections: 20dp - Used for special spacing needs */
    val Large = 20.dp

    /** Extra large: 24dp - Used for bottom sheets, modals, settings */
    val XLarge = 24.dp

    /** Maximum spacing: 32dp - Used for splash screens and hero sections */
    val XXLarge = 32.dp

    /** Very large spacing: 40dp - Used when extra breathing room is needed */
    val XXXLarge = 40.dp
}
