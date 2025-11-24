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

package com.github.bfollon.farmaciasdeguardiaensegovia.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.components.ResponsiveText

/**
 * Settings screen that displays cache management and information options
 * Matches iOS SettingsView structure with Material 3 design system
 * Displayed as a ModalBottomSheet
 */
@Composable
fun SettingsScreen(
    onDismiss: () -> Unit,
    onAboutClick: () -> Unit,
    onCacheStatusClick: () -> Unit,
    onCacheRefreshClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // PDF Cache Section
        CachePDFSection(
            onCheckUpdatesClick = onCacheRefreshClick,
            onViewCacheStatusClick = onCacheStatusClick
        )
        
        // Information Section
        InformationSection(
            onAboutClick = onAboutClick
        )
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * PDF Cache section with informational text and action buttons
 */
@Composable
private fun CachePDFSection(
    onCheckUpdatesClick: () -> Unit,
    onViewCacheStatusClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Header
        ResponsiveText(
            text = "Caché de PDFs",
            compactSize = 12.sp,
            mediumSize = MaterialTheme.typography.bodyMedium.fontSize,
            expandedSize = 16.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Informational text block
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ResponsiveText(
                        text = "PDFs en caché",
                        compactSize = 14.sp,
                        mediumSize = MaterialTheme.typography.bodyLarge.fontSize,
                        expandedSize = MaterialTheme.typography.titleMedium.fontSize,
                        fontWeight = FontWeight.Bold
                    )

                    ResponsiveText(
                        text = "Los horarios PDF se almacenan localmente para una carga más rápida y acceso sin conexión.",
                        compactSize = 12.sp,
                        mediumSize = 13.sp,
                        expandedSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Check Updates Button
                SettingsListItem(
                    icon = Icons.Default.Refresh,
                    iconTint = MaterialTheme.colorScheme.primary,
                    text = "Buscar Actualizaciones",
                    showTrailingIcon = false,
                    onClick = onCheckUpdatesClick
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                
                // View Cache Status Button
                SettingsListItem(
                    icon = Icons.Default.Info,
                    iconTint = MaterialTheme.colorScheme.primary,
                    text = "Ver Estado de la caché",
                    showTrailingIcon = true,
                    onClick = onViewCacheStatusClick
                )
            }
        }
    }
}

/**
 * Information section with About button
 */
@Composable
private fun InformationSection(
    onAboutClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Header
        ResponsiveText(
            text = "Información",
            compactSize = 12.sp,
            mediumSize = MaterialTheme.typography.bodyMedium.fontSize,
            expandedSize = 16.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // About Button
                SettingsListItem(
                    icon = Icons.Default.Info,
                    iconTint = MaterialTheme.colorScheme.primary,
                    text = "Acerca de",
                    showTrailingIcon = true,
                    onClick = onAboutClick
                )
            }
        }
    }
}

/**
 * Reusable list item component for settings options
 */
@Composable
private fun SettingsListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    text: String,
    showTrailingIcon: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint
                )
                ResponsiveText(
                    text = text,
                    compactSize = 14.sp,
                    mediumSize = MaterialTheme.typography.bodyLarge.fontSize,
                    expandedSize = MaterialTheme.typography.titleMedium.fontSize,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            if (showTrailingIcon) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

