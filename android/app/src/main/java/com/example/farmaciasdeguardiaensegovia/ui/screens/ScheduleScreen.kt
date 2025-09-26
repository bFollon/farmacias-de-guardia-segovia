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

package com.example.farmaciasdeguardiaensegovia.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.farmaciasdeguardiaensegovia.data.DutyLocation
import com.example.farmaciasdeguardiaensegovia.data.DutyTimeSpan
import com.example.farmaciasdeguardiaensegovia.ui.components.NoPharmacyOnDutyCard
import com.example.farmaciasdeguardiaensegovia.ui.components.PharmacyCard
import com.example.farmaciasdeguardiaensegovia.ui.components.ShiftHeaderCard
import com.example.farmaciasdeguardiaensegovia.ui.components.ShiftInfoCard
import com.example.farmaciasdeguardiaensegovia.ui.viewmodels.ScheduleViewModel
import java.util.Calendar

/**
 * Main schedule screen showing pharmacy schedules for Segovia Capital
 * Equivalent to iOS PDFViewScreen and ScheduleContentView
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    locationId: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: ScheduleViewModel = viewModel {
        ScheduleViewModel(context, locationId ?: "segovia-capital")
    }
    val uiState by viewModel.uiState.collectAsState()
    
    var showDatePicker by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${uiState.location?.icon} ${uiState.location?.name}",
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Seleccionar fecha")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refrescar")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    LoadingContent()
                }
                uiState.error != null -> {
                    ErrorContent(
                        error = uiState.error ?: "Error desconocido",
                        onRetry = { viewModel.refresh() },
                        onDismiss = { viewModel.clearError() }
                    )
                }
                uiState.schedules.isEmpty() -> {
                    EmptyContent()
                }
                else -> {
                    ScheduleContent(
                        uiState = uiState,
                        onViewPDF = { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }
    
    // Date picker dialog
    if (showDatePicker) {
        DatePickerModal(
            onDateSelected = { calendar ->
                viewModel.setSelectedDate(calendar)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Cargando calendarios...",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Error",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cerrar")
                    }
                    Button(onClick = onRetry) {
                        Text("Reintentar")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.EventBusy,
                contentDescription = "Sin horarios",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "No hay farmacias de guardia programadas",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Intente refrescar o seleccione una fecha diferente",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ScheduleContent(
    uiState: ScheduleViewModel.ScheduleUiState,
    onViewPDF: (String) -> Unit
) {
    var showShiftInfo by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with date
        item {
            DateHeader(formattedDateTime = uiState.formattedDateTime)
        }

        item {
            Text(
                text = "Farmacias de Guardia",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        // Current schedule info
        uiState.currentSchedule?.let { schedule ->

            schedule.shifts[uiState.activeTimeSpan]?.let { pharmacies ->
                if (pharmacies.isNotEmpty()) {
                    pharmacies.firstOrNull()?.let { pharmacy ->
                        item {
                            ShiftHeaderCard(
                                uiState.activeTimeSpan!!,
                                isActive = uiState.activeTimeSpan.isActiveNow(),
                                onInfoClick = if(uiState.activeTimeSpan.requiresExplanation) {
                                    { showShiftInfo = true }
                                } else null
                            )
                        }
                        item {
                            PharmacyCard(
                                pharmacy = pharmacy,
                                isActive = uiState.activeTimeSpan?.isActiveNow() ?: false
                            )
                        }
                    }
                } else {
                    // No pharmacies on duty for this timespan
                    item {
                        ShiftHeaderCard(
                            uiState.activeTimeSpan!!,
                            isActive = true
                        )
                    }
                    item {
                        NoPharmacyOnDuty(uiState.location)
                    }
                }
            } ?: run {
                // No schedule for this timespan at all
                item {
                    NoPharmacyOnDuty(uiState.location)
                }
            }
        } ?: run {
            // No schedule for this timespan at all
            item {
                NoPharmacyOnDuty(uiState.location)
            }
        }
        
        // Disclaimer and PDF link
        item {
            DisclaimerCard(
                location = uiState.location!!, // TODO fix
                onViewPDF = onViewPDF
            )
        }


    }

    ShiftInfoModalSheet(uiState.activeTimeSpan, isVisible = showShiftInfo, onDismiss = { showShiftInfo = false })
}

@Composable
private fun DateHeader(formattedDateTime: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Fecha y hora",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = formattedDateTime,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun DisclaimerCard(
    location: DutyLocation,
    onViewPDF: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Aviso",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "La información mostrada puede no ser exacta. Por favor, consulte siempre la fuente oficial:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            TextButton(
                onClick = { onViewPDF(location.associatedRegion.pdfURL) },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Calendario de Guardias - ${location.associatedRegion.name}")
            }
            Text(
                text = "¿Ha encontrado algún error? Repórtelo aquí",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun DatePickerModal(
    onDateSelected: (Calendar) -> Unit,
    onDismiss: () -> Unit
) {
    // For now, show a simple date picker placeholder
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar fecha") },
        text = { Text("Función de selección de fecha en desarrollo") },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
private fun NoPharmacyOnDuty(location: DutyLocation?) =
    NoPharmacyOnDutyCard(
        message = "No hay farmacia de guardia asignada para ${location?.name} en esta fecha.",
        additionalInfo = "Por favor, consulte las farmacias de guardia de otras zonas cercanas o el calendario oficial."
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftInfoModalSheet(
    dutyTimeSpan: DutyTimeSpan?,
    isVisible: Boolean,
    onDismiss: () -> Unit = {}
) {
    if (isVisible && dutyTimeSpan != null) {
        ModalBottomSheet(
            content = {
                ShiftInfoCard(dutyTimeSpan = dutyTimeSpan)
            },
            onDismissRequest = onDismiss
        )
    }
}
