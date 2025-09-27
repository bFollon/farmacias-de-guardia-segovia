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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.runtime.LaunchedEffect
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
import com.example.farmaciasdeguardiaensegovia.data.ZBS
import com.example.farmaciasdeguardiaensegovia.ui.components.CantalejoDisclaimerCard
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
    onNavigateToCantalejoInfo: () -> Unit = {},
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
                        },
                        onNavigateToCantalejoInfo = onNavigateToCantalejoInfo
                    )
                }
            }
        }
    }
    
    // Date picker dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDateSelected = { calendar ->
                viewModel.setSelectedDate(calendar)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
            onTodaySelected = {
                viewModel.resetToToday()
                showDatePicker = false
            }
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
    onViewPDF: (String) -> Unit,
    onNavigateToCantalejoInfo: () -> Unit = {},
) {
    var showShiftInfo by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with date
        item {
            DateHeader(
                selectedDate = uiState.selectedDate,
                currentDateTime = uiState.formattedDateTime
            )
        }

        item {
            Text(
                text = "Farmacias de Guardia",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        // Show content based on whether a date is selected or not
        if (uiState.selectedDate != null) {
            // Show all shifts for selected date
            if (uiState.allShiftsForSelectedDate.isNotEmpty()) {
                uiState.allShiftsForSelectedDate.forEach { (timeSpan, pharmacies) ->
                    item {
                        ShiftHeaderCard(
                            timeSpan,
                            isActive = false, // Always false for selected dates
                            onInfoClick = if(timeSpan.requiresExplanation) {
                                { showShiftInfo = true }
                            } else null
                        )
                    }
                    
                    pharmacies.forEach { pharmacy ->
                        item {
                            PharmacyCard(
                                pharmacy = pharmacy,
                                isActive = false // Always false for selected dates
                            )
                        }
                    }
                }
            } else {
                // No shifts with pharmacies for selected date
                item {
                    NoPharmacyOnDuty(uiState.location)
                }
            }
        } else {
            // Show current active schedule (default behavior)
            uiState.currentSchedule?.let { schedule ->
                schedule.shifts[uiState.activeTimeSpan]?.let { pharmacies ->
                    if (pharmacies.isNotEmpty()) {
                        if(uiState.location == DutyLocation.fromZBS(ZBS.CANTALEJO)) {
                            item {
                                CantalejoDisclaimerCard(
                                    onClick = onNavigateToCantalejoInfo
                                )
                            }
                        }

                        item {
                            ShiftHeaderCard(
                                uiState.activeTimeSpan!!,
                                isActive = uiState.activeTimeSpan.isActiveNow(),
                                onInfoClick = if(uiState.activeTimeSpan.requiresExplanation) {
                                    { showShiftInfo = true }
                                } else null
                            )
                        }

                        pharmacies.forEach { pharmacy ->
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
private fun DateHeader(
    selectedDate: Calendar?,
    currentDateTime: String
) {
    val displayText = selectedDate?.let { calendar ->
        val today = Calendar.getInstance()
        if (calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            calendar.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
            calendar.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)) {
            "Hoy"
        } else {
            formatSelectedDate(calendar)
        }
    } ?: currentDateTime

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
                text = displayText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

private fun formatSelectedDate(calendar: Calendar): String {
    val dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "Lunes"
        Calendar.TUESDAY -> "Martes"
        Calendar.WEDNESDAY -> "Miércoles"
        Calendar.THURSDAY -> "Jueves"
        Calendar.FRIDAY -> "Viernes"
        Calendar.SATURDAY -> "Sábado"
        Calendar.SUNDAY -> "Domingo"
        else -> "Lunes"
    }

    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val month = when (calendar.get(Calendar.MONTH)) {
        Calendar.JANUARY -> "enero"
        Calendar.FEBRUARY -> "febrero"
        Calendar.MARCH -> "marzo"
        Calendar.APRIL -> "abril"
        Calendar.MAY -> "mayo"
        Calendar.JUNE -> "junio"
        Calendar.JULY -> "julio"
        Calendar.AUGUST -> "agosto"
        Calendar.SEPTEMBER -> "septiembre"
        Calendar.OCTOBER -> "octubre"
        Calendar.NOVEMBER -> "noviembre"
        Calendar.DECEMBER -> "diciembre"
        else -> "enero"
    }
    val year = calendar.get(Calendar.YEAR)

    return "$dayOfWeek, $day de $month de $year"
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialog(
    onDateSelected: (Calendar) -> Unit,
    onDismiss: () -> Unit,
    onTodaySelected: () -> Unit
) {
    // Get current date at midnight - use a completely separate calculation from business logic
    val todayAtMidnight = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    
    // Date picker state with proper current date
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = todayAtMidnight,
        // TODO: Calculate dynamically from loaded schedules instead of fixed range
        yearRange = IntRange(
            Calendar.getInstance().apply { add(Calendar.MONTH, -6) }.get(Calendar.YEAR),
            Calendar.getInstance().apply { add(Calendar.MONTH, 6) }.get(Calendar.YEAR)
        )
    )
    
    // Track when user actually selects a date (not the initial state)
    var initialDateSet by remember { mutableStateOf(false) }
    
    // Handle immediate date selection when user taps a date
    LaunchedEffect(datePickerState.selectedDateMillis) {
        if (!initialDateSet) {
            initialDateSet = true
            return@LaunchedEffect
        }
        
        // User has selected a date - apply it immediately
        datePickerState.selectedDateMillis?.let { millis ->
            val calendar = Calendar.getInstance().apply { timeInMillis = millis }
            onDateSelected(calendar)
            onDismiss()
        }
    }
    
    // Use a full-screen dialog approach
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .clickable { onDismiss() }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.Center)
                .clickable(enabled = false) { }, // Prevent clicks from propagating to background
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header with title and "Hoy" button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onTodaySelected) {
                        Text("Hoy")
                    }
                    
                    Text(
                        text = "Seleccionar fecha",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    TextButton(onClick = onDismiss) {
                        Text("Cerrar")
                    }
                }
                
                // Date picker - full size with immediate selection
                DatePicker(
                    state = datePickerState,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
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
