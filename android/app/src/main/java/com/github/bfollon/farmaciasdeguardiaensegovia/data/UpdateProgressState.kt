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

package com.github.bfollon.farmaciasdeguardiaensegovia.data

/**
 * Progress state for PDF update operations
 * Used to track and display the status of update checks
 */
sealed class UpdateProgressState {
    object Checking : UpdateProgressState()
    object UpToDate : UpdateProgressState()
    object Downloading : UpdateProgressState()
    object Downloaded : UpdateProgressState()
    data class Error(val message: String) : UpdateProgressState()
}
