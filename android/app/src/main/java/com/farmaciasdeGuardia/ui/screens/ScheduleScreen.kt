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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Today
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
import com.farmaciasdeGuardia.ui.components.LoadingScreen
import com.farmaciasdeGuardia.ui.components.PharmacyCard
import com.farmaciasdeGuardia.ui.components.ShiftHeaderCard
import com.farmaciasdeGuardia.ui.viewmodels.ScheduleViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPDF: (String) -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Initialize the schedule on screen load
    LaunchedEffect(Unit) {
        viewModel.loadSchedule()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Farmacias de Guardia",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        uiState.selectedRegion?.let { region ->
                            Text(
                                text = region.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.forceRefresh() },
                        enabled = !uiState.isLoading
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Actualizar"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                LoadingScreen(
                    modifier = Modifier.padding(paddingValues),
                    message = "Cargando horarios..."
                )
            }
            
            uiState.errorMessage != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    ErrorDisplay(
                        message = uiState.errorMessage!!,
                        onRetry = { viewModel.loadSchedule() }
                    )
                }
            }
            
            uiState.schedule.isEmpty() -> {
                NoScheduleContent(
                    modifier = Modifier.padding(paddingValues),
                    onRetry = { viewModel.loadSchedule() },
                    onViewPDF = onNavigateToPDF,
                    errorReportUrl = uiState.errorReportUrl
                )
            }
            
            else -> {
                ScheduleContent(
                    uiState = uiState,
                    modifier = Modifier.padding(paddingValues),
                    onViewPDF = onNavigateToPDF
                )
            }
        }
    }
}

@Composable
private fun ScheduleContent(
    uiState: ScheduleUiState,
    modifier: Modifier = Modifier,
    onViewPDF: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Current status header
        item {
            CurrentStatusCard(
                currentTimespan = uiState.currentTimespan,
                activePharmacy = uiState.activePharmacy,
                formattedDate = uiState.formattedCurrentDate
            )
        }
        
        // Schedule entries grouped by date
        val groupedSchedule = uiState.schedule.groupBy { it.date }
        groupedSchedule.forEach { (date, pharmacies) ->
            item {
                ShiftHeaderCard(
                    date = date,
                    formattedDate = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("es", "ES"))
                        .format(date),
                    isToday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(date) == SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(Date())
                )
            }
            
            items(pharmacies) { pharmacy ->
                PharmacyCard(
                    pharmacy = pharmacy,
                    isActive = pharmacy == uiState.activePharmacy
                )
            }
        }
        
        // View PDF button
        uiState.selectedRegion?.let { region ->
            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onViewPDF(region.pdfUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ver PDF original")
                }
            }
        }
    }
}

@Composable
private fun CurrentStatusCard(
    currentTimespan: String?,
    activePharmacy: com.farmaciasdeGuardia.data.model.Pharmacy?,
    formattedDate: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    Icons.Default.Today,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            currentTimespan?.let { timespan ->
                Text(
                    text = "Turno actual: $timespan",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            activePharmacy?.let { pharmacy ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Farmacia de guardia: ${pharmacy.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (pharmacy.address.isNotEmpty()) {
                    Text(
                        text = pharmacy.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NoScheduleContent(
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
    onViewPDF: (String) -> Unit,
    errorReportUrl: String?
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No hay horarios disponibles",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "No se pudieron cargar los horarios para esta región. Puedes intentar actualizar o ver el PDF original.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = onRetry) {
                Text("Reintentar")
            }
            
            errorReportUrl?.let { url ->
                OutlinedButton(onClick = { onViewPDF(url) }) {
                    Text("Ver PDF original")
                }
            }
        }
    }
}
