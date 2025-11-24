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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.components.ResponsiveText
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.bfollon.farmaciasdeguardiaensegovia.data.RegionCacheStatus
import com.github.bfollon.farmaciasdeguardiaensegovia.viewmodels.CacheStatusViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cache Status Screen - Displays PDF cache status for all regions
 * Matches iOS CacheStatusView design with Material 3
 * Displayed as a ModalBottomSheet
 */
@Composable
fun CacheStatusScreen(
    onDismiss: () -> Unit,
    viewModel: CacheStatusViewModel = viewModel()
) {
    val cacheStatuses by viewModel.cacheStatuses.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    if (isLoading) {
        // Loading state
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator()
                ResponsiveText(
                    text = "Comprobando estado de la caché...",
                    compactSize = MaterialTheme.typography.bodySmall.fontSize,
                mediumSize = MaterialTheme.typography.bodyMedium.fontSize,
                expandedSize = MaterialTheme.typography.bodyMedium.fontSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Section
            item {
                CacheStatusHeader(cacheStatuses)
            }
            
            // Region Status Cards
            items(cacheStatuses) { status ->
                CacheStatusCard(status)
            }
            
            // Info Section
            item {
                CacheInfoCard()
            }
        }
    }
}

/**
 * Header showing last update check time
 */
@Composable
private fun CacheStatusHeader(cacheStatuses: List<RegionCacheStatus>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ResponsiveText(
            text = "Estado de la caché de PDFs",
            compactSize = MaterialTheme.typography.titleMedium.fontSize,
                mediumSize = MaterialTheme.typography.titleMedium.fontSize,
                expandedSize = MaterialTheme.typography.titleMedium.fontSize,
            fontWeight = FontWeight.Bold
        )
        
        val lastChecked = cacheStatuses.firstOrNull()?.lastChecked
        if (lastChecked != null) {
            val formatter = SimpleDateFormat("d 'sept' yyyy, HH:mm", Locale.forLanguageTag("es-ES"))
            ResponsiveText(
                text = "Última búsqueda de actualizaciones: ${formatter.format(Date(lastChecked))}",
                compactSize = MaterialTheme.typography.bodySmall.fontSize,
                mediumSize = MaterialTheme.typography.bodyMedium.fontSize,
                expandedSize = MaterialTheme.typography.bodyMedium.fontSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            ResponsiveText(
                text = "La caché nunca se ha comprobado para actualizaciones",
                compactSize = MaterialTheme.typography.bodySmall.fontSize,
                mediumSize = MaterialTheme.typography.bodyMedium.fontSize,
                expandedSize = MaterialTheme.typography.bodyMedium.fontSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Card displaying cache status for a single region
 */
@Composable
private fun CacheStatusCard(status: RegionCacheStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with region and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ResponsiveText(
                        text = status.region.icon,
                        compactSize = MaterialTheme.typography.titleLarge.fontSize,
                        mediumSize = MaterialTheme.typography.headlineSmall.fontSize,
                        expandedSize = MaterialTheme.typography.headlineSmall.fontSize
                    )
                    ResponsiveText(
                        text = status.region.name,
                        compactSize = MaterialTheme.typography.titleMedium.fontSize,
                mediumSize = MaterialTheme.typography.titleMedium.fontSize,
                expandedSize = MaterialTheme.typography.titleMedium.fontSize,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Status badge
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            !status.isCached -> Icons.Default.Warning
                            status.needsUpdate -> Icons.Default.Refresh
                            else -> Icons.Default.CheckCircle
                        },
                        contentDescription = null,
                        tint = status.statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                    ResponsiveText(
                        text = status.statusText,
                        compactSize = MaterialTheme.typography.labelMedium.fontSize,
                        mediumSize = MaterialTheme.typography.bodySmall.fontSize,
                        expandedSize = MaterialTheme.typography.bodySmall.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = status.statusColor
                    )
                }
            }
            
            // Details (if cached)
            if (status.isCached) {
                HorizontalDivider()
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ResponsiveText(
                            text = "Descargado:",
                            compactSize = MaterialTheme.typography.bodySmall.fontSize,
                mediumSize = MaterialTheme.typography.bodyMedium.fontSize,
                expandedSize = MaterialTheme.typography.bodyMedium.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ResponsiveText(
                            text = status.formattedDownloadDate,
                            compactSize = MaterialTheme.typography.bodySmall.fontSize,
                mediumSize = MaterialTheme.typography.bodyMedium.fontSize,
                expandedSize = MaterialTheme.typography.bodyMedium.fontSize,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ResponsiveText(
                            text = "Tamaño:",
                            compactSize = MaterialTheme.typography.bodySmall.fontSize,
                mediumSize = MaterialTheme.typography.bodyMedium.fontSize,
                expandedSize = MaterialTheme.typography.bodyMedium.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ResponsiveText(
                            text = status.formattedFileSize,
                            compactSize = MaterialTheme.typography.bodySmall.fontSize,
                mediumSize = MaterialTheme.typography.bodyMedium.fontSize,
                expandedSize = MaterialTheme.typography.bodyMedium.fontSize,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // Update available warning
                    if (status.needsUpdate) {
                        HorizontalDivider()
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFFA726), // Orange
                                modifier = Modifier.size(16.dp)
                            )
                            ResponsiveText(
                                text = "Hay una actualización disponible para este PDF",
                                compactSize = MaterialTheme.typography.bodySmall.fontSize,
                mediumSize = MaterialTheme.typography.bodyMedium.fontSize,
                expandedSize = MaterialTheme.typography.bodyMedium.fontSize,
                                color = Color(0xFFFFA726)
                            )
                        }
                    }
                }
            } else {
                // Not cached message
                HorizontalDivider()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    ResponsiveText(
                        text = "PDF no descargado - se obtendrá cuando sea necesario",
                        compactSize = MaterialTheme.typography.bodySmall.fontSize,
                mediumSize = MaterialTheme.typography.bodyMedium.fontSize,
                expandedSize = MaterialTheme.typography.bodyMedium.fontSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Information card explaining status colors
 */
@Composable
private fun CacheInfoCard() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ResponsiveText(
            text = "Los PDFs se almacenan localmente para una carga más rápida y acceso sin conexión.",
            compactSize = MaterialTheme.typography.bodySmall.fontSize,
                mediumSize = MaterialTheme.typography.bodyMedium.fontSize,
                expandedSize = MaterialTheme.typography.bodyMedium.fontSize,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    ResponsiveText(
                        text = "Información de la caché",
                        compactSize = MaterialTheme.typography.bodySmall.fontSize,
                    mediumSize = MaterialTheme.typography.titleSmall.fontSize,
                    expandedSize = MaterialTheme.typography.titleSmall.fontSize,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    InfoColorRow(
                        color = Color(0xFF66BB6A),
                        text = "Verde: PDF descargado y actualizado"
                    )
                    InfoColorRow(
                        color = Color(0xFFFFA726),
                        text = "Naranja: Actualización del PDF disponible"
                    )
                    InfoColorRow(
                        color = Color(0xFFD32F2F),
                        text = "Rojo: PDF no descargado"
                    )
                }
            }
        }
    }
}

/**
 * Row showing a color indicator with explanatory text
 */
@Composable
private fun InfoColorRow(color: Color, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ResponsiveText(
            text = "•",
            color = color,
            compactSize = MaterialTheme.typography.bodyMedium.fontSize,
            mediumSize = MaterialTheme.typography.bodyMedium.fontSize,
            expandedSize = MaterialTheme.typography.bodyLarge.fontSize,
            fontWeight = FontWeight.Bold
        )
        ResponsiveText(
            text = text,
            compactSize = MaterialTheme.typography.bodyMedium.fontSize,
            mediumSize = MaterialTheme.typography.bodyMedium.fontSize,
            expandedSize = MaterialTheme.typography.bodyLarge.fontSize
        )
    }
}
