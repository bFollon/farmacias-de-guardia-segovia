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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reusable offline warning card component
 * Shows "Sin conexión" message with warning icon
 * Matches info card style for consistency
 * 
 * @param modifier Modifier for the card
 * @param isClickable Whether the card should be clickable (for dialog trigger)
 * @param onClick Callback when card is clicked (only used if isClickable = true)
 */
@Composable
fun OfflineWarningCard(
    modifier: Modifier = Modifier,
    isClickable: Boolean = true,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .then(
                if (isClickable) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFA726).copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Sin conexión",
                tint = Color(0xFFFFA726),
                modifier = Modifier.size(20.dp)
            )

            Text(
                text = "Sin conexión - usando datos almacenados",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFFA726),
            )
        }
    }
}

