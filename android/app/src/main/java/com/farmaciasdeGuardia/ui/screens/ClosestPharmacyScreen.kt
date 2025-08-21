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
package com.farmaciasdeGuardia.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.farmaciasdeGuardia.ui.components.ErrorDisplay
import com.farmaciasdeGuardia.ui.components.PharmacyCard
import com.farmaciasdeGuardia.ui.viewmodels.ClosestPharmacyViewModel
import com.farmaciasdeGuardia.ui.viewmodels.SearchStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClosestPharmacyScreen(
    onNavigateBack: () -> Unit,
    viewModel: ClosestPharmacyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Farmacia más cercana",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                // Permission not granted
                !uiState.hasLocationPermission -> {
                    LocationPermissionContent(
                        onRequestPermission = { viewModel.requestLocationPermission() }
                    )
                }
                
                // Currently searching
                uiState.isSearching -> {
                    SearchProgressContent(
                        currentStep = uiState.currentSearchStep,
                        stepProgress = uiState.stepProgress
                    )
                }
                
                // Error occurred
                uiState.errorMessage != null -> {
                    ErrorDisplay(
                        message = uiState.errorMessage!!,
                        onRetry = { viewModel.findClosestPharmacy() }
                    )
                }
                
                // Search completed with result
                uiState.closestPharmacy != null -> {
                    SearchResultContent(
                        pharmacy = uiState.closestPharmacy!!,
                        distance = uiState.distance,
                        onFindAgain = { viewModel.findClosestPharmacy() },
                        onGetDirections = { viewModel.openDirections() }
                    )
                }
                
                // Initial state
                else -> {
                    InitialSearchContent(
                        onStartSearch = { viewModel.findClosestPharmacy() }
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationPermissionContent(
    onRequestPermission: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Permiso de ubicación requerido",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Para encontrar la farmacia más cercana, necesitamos acceso a tu ubicación.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = null
                )
                Text("Permitir ubicación")
            }
        }
    }
}

@Composable
private fun SearchProgressContent(
    currentStep: SearchStep,
    stepProgress: Float
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        // Progress indicator
        CircularProgressIndicator(
            progress = stepProgress,
            modifier = Modifier.size(72.dp),
            strokeWidth = 6.dp,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Current step
        Text(
            text = currentStep.description,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Esto puede tomar unos segundos...",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun InitialSearchContent(
    onStartSearch: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Icon(
            Icons.Default.MyLocation,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Buscar farmacia más cercana",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Encontraremos la farmacia de guardia más cercana a tu ubicación actual.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onStartSearch,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null
                )
                Text("Buscar farmacia")
            }
        }
    }
}

@Composable
private fun SearchResultContent(
    pharmacy: com.farmaciasdeGuardia.data.model.Pharmacy,
    distance: String?,
    onFindAgain: () -> Unit,
    onGetDirections: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Farmacia más cercana",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                distance?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "A $it de distancia",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Pharmacy details
        PharmacyCard(
            pharmacy = pharmacy,
            isActive = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Action buttons
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onGetDirections,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Directions,
                        contentDescription = null
                    )
                    Text("Cómo llegar")
                }
            }
            
            OutlinedButton(
                onClick = onFindAgain,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Buscar de nuevo")
            }
        }
    }
}
