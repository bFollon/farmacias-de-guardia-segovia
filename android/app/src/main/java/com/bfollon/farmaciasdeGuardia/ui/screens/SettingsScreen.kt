/*
 * Farmacias de Guardia - Segovia
 * Copyright (C) 2024 Bruno Follón
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.bfollon.farmaciasdeGuardia.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bfollon.farmaciasdeGuardia.ui.components.ErrorDisplay
import com.bfollon.farmaciasdeGuardia.ui.viewmodels.SettingsViewModel
import com.bfollon.farmaciasdeGuardia.data.model.Region

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Load cache status on screen load
    LaunchedEffect(Unit) {
        viewModel.loadCacheStatus()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Configuración",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cache Management Section
            item {
                CacheManagementSection(
                    uiState = uiState,
                    onForceRefresh = { viewModel.forceRefreshCache() },
                    onClearCache = { viewModel.clearCache() },
                    onClearGeocodingCache = { viewModel.clearGeocodingCache() }
                )
            }
            
            // Cache Status Section
            item {
                CacheStatusSection(
                    cacheStatus = uiState.cacheStatus,
                    refreshProgress = uiState.refreshProgress,
                    onRefreshRegion = { region -> viewModel.refreshRegion(region) }
                )
            }
            
            // Debug Information Section
            if (uiState.debugInfo.isNotEmpty()) {
                item {
                    DebugInfoSection(
                        debugInfo = uiState.debugInfo
                    )
                }
            }
            
            // Error Display
            uiState.errorMessage?.let { message ->
                item {
                    ErrorDisplay(
                        message = message,
                        onRetry = { viewModel.loadCacheStatus() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CacheManagementSection(
    uiState: SettingsUiState,
    onForceRefresh: () -> Unit,
    onClearCache: () -> Unit,
    onClearGeocodingCache: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Gestión de caché",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Force refresh button
            Button(
                onClick = onForceRefresh,
                enabled = !uiState.isRefreshing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null
                        )
                    }
                    Text(if (uiState.isRefreshing) "Actualizando..." else "Actualizar todos los horarios")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Clear cache buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onClearCache,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isRefreshing
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Limpiar horarios",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                
                OutlinedButton(
                    onClick = onClearGeocodingCache,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isRefreshing
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Limpiar ubicaciones",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CacheStatusSection(
    cacheStatus: Map<Region, Boolean>,
    refreshProgress: Map<Region, Float>,
    onRefreshRegion: (Region) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Estado del caché",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Region.values().forEach { region ->
                    RegionCacheStatusItem(
                        region = region,
                        hasCache = cacheStatus[region] ?: false,
                        progress = refreshProgress[region],
                        onRefresh = { onRefreshRegion(region) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RegionCacheStatusItem(
    region: Region,
    hasCache: Boolean,
    progress: Float?,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    if (hasCache) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (hasCache) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(end = 8.dp)
                )
                
                Column {
                    Text(
                        text = region.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (hasCache) "Datos disponibles" else "Sin datos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Progress indicator or refresh button
            progress?.let { p ->
                if (p > 0f && p < 1f) {
                    CircularProgressIndicator(
                        progress = p,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Actualizar ${region.displayName}",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } ?: run {
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Actualizar ${region.displayName}",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugInfoSection(
    debugInfo: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Información de depuración",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = debugInfo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}
