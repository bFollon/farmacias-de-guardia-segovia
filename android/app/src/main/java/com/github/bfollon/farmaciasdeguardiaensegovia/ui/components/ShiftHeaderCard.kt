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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyTimeSpan

/**
 * Component for displaying shift headers (day/night)
 * Equivalent to iOS ShiftHeaderView
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftHeaderCard(
    timeSpan: DutyTimeSpan,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onInfoClick: (() -> Unit)? = null
) {
    val (iconData, color) = Triple(
        Icons.Default.WbSunny,
        timeSpan.displayName,
        timeSpan.displayFormat
    ) to if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    val (icon, title, timeRange) = iconData
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                ResponsiveText(
                    text = title,
                    compactSize = MaterialTheme.typography.titleSmall.fontSize,
                    mediumSize = MaterialTheme.typography.titleSmall.fontSize,
                    expandedSize = MaterialTheme.typography.titleSmall.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
                ResponsiveText(
                    text = timeRange,
                    compactSize = MaterialTheme.typography.bodySmall.fontSize,
                    mediumSize = MaterialTheme.typography.bodySmall.fontSize,
                    expandedSize = MaterialTheme.typography.bodySmall.fontSize,
                    color = MaterialTheme.colorScheme.outline
                )
                if (isActive) {
                    ResponsiveText(
                        text = "Activo ahora",
                        compactSize = MaterialTheme.typography.labelSmall.fontSize,
                        mediumSize = MaterialTheme.typography.labelSmall.fontSize,
                        expandedSize = MaterialTheme.typography.labelSmall.fontSize,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            if (onInfoClick != null) {
                IconButton(onClick = onInfoClick) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Más información",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
