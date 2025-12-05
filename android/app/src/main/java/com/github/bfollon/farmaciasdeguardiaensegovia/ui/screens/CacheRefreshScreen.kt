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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.bfollon.farmaciasdeguardiaensegovia.data.Region
import com.github.bfollon.farmaciasdeguardiaensegovia.data.UpdateProgressState
import com.github.bfollon.farmaciasdeguardiaensegovia.viewmodels.CacheRefreshViewModel

/**
 * Cache Refresh Screen - Shows progress when checking for PDF updates
 * Matches iOS CacheRefreshView design with Material 3
 * Displayed as a ModalBottomSheet
 */
@Composable
fun CacheRefreshScreen(
    onDismiss: () -> Unit,
    viewModel: CacheRefreshViewModel = viewModel()
) {
    val refreshStates by viewModel.refreshStates.collectAsState()
    val isCompleted by viewModel.isCompleted.collectAsState()
    val wasOffline by viewModel.wasOffline.collectAsState()
    
    val regions = listOf(
        Region.segoviaCapital,
        Region.cuellar,
        Region.elEspinar,
        Region.segoviaRural
    )
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section header
            item {
                Text(
                    text = "Estado de Actualización",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // Region cards
            items(regions) { region ->
                val state = refreshStates[region.id] ?: UpdateProgressState.Checking
                CacheRefreshCard(region, state)
            }
        }
        
        // Completion view (appears at bottom when done)
        AnimatedVisibility(visible = isCompleted) {
            CompletionView(wasOffline = wasOffline)
        }
    }
}

/**
 * Card showing refresh status for a single region
 */
@Composable
private fun CacheRefreshCard(region: Region, state: UpdateProgressState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Region info
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = region.icon,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = region.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Status indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (state) {
                    is UpdateProgressState.Checking -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Comprobando...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    is UpdateProgressState.Downloading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Descargando...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    is UpdateProgressState.UpToDate,
                    is UpdateProgressState.Downloaded -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF66BB6A), // Green
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Actualizado",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF66BB6A)
                        )
                    }
                    
                    is UpdateProgressState.Error -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFD32F2F), // Red
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFD32F2F)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Completion view shown when all updates are complete
 */
@Composable
private fun CompletionView(wasOffline: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (wasOffline) {
                // Offline - show error state
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFFA726), // Amber warning
                    modifier = Modifier.size(48.dp)
                )
                
                Text(
                    text = "Sin conexión a Internet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "No se pudo actualizar la caché. Conecte a Internet e intente de nuevo.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else {
                // Online - show success state
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF66BB6A), // Green
                    modifier = Modifier.size(48.dp)
                )
                
                Text(
                    text = "¡Actualización Completada!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Todos los PDFs han sido comprobados",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
