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

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.bfollon.farmaciasdeguardiaensegovia.data.AppConfig
import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyLocation
import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyTimeSpan
import com.github.bfollon.farmaciasdeguardiaensegovia.data.Pharmacy
import com.github.bfollon.farmaciasdeguardiaensegovia.data.ZBS
import com.github.bfollon.farmaciasdeguardiaensegovia.services.NetworkMonitor
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.components.CantalejoDisclaimerCard
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.components.NoPharmacyOnDutyCard
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.components.PharmacyCard
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.components.ResponsiveText
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.components.ShiftHeaderCard
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.components.ShiftInfoCard
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.viewmodels.ScheduleViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

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
    val viewModel: ScheduleViewModel = viewModel(key = locationId) {
        ScheduleViewModel(context, locationId ?: "segovia-capital")
    }
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    var showDatePicker by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    
    // Check network status
    var isOffline by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isOffline = !NetworkMonitor.isOnline()
    }
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // iOS-style header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Top row: Date picker button (left) and Refresh button (right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Date picker button with text (like iOS "Hoy")
                    TextButton(
                        onClick = { showDatePicker = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        ResponsiveText(
                            text = if (uiState.selectedDate?.let { selectedCal ->
                                val today = Calendar.getInstance()
                                selectedCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                                selectedCal.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                                selectedCal.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)
                            } == true) "Hoy" else {
                                uiState.selectedDate?.let { formatSelectedDate(it) } ?: "Hoy"
                            },
                            compactSize = 14.sp,
                            mediumSize = 15.sp,
                            expandedSize = 16.sp,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    
                    // Refresh button
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refrescar"
                        )
                    }
                }
                
                // Region name row (prominent title)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ResponsiveText(
                        text = uiState.location?.icon ?: "",
                        compactSize = MaterialTheme.typography.headlineSmall.fontSize,
                        mediumSize = 28.sp,
                        expandedSize = 30.sp
                    )
                    ResponsiveText(
                        text = uiState.location?.name ?: "",
                        compactSize = 24.sp,
                        mediumSize = 26.sp,
                        expandedSize = 28.sp,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Main content
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
                    EmptyContent(isOffline = isOffline)
                }
                else -> {
                    ScheduleContent(
                        uiState = uiState,
                        onViewPDF = {
                            // Validate URL before opening browser
                            coroutineScope.launch {
                                val validatedURL = viewModel.validateAndGetPDFURL()
                                if (validatedURL != null) {
                                    val intent = Intent(Intent.ACTION_VIEW, validatedURL.toUri())
                                    context.startActivity(intent)
                                }
                                // Error is shown automatically via dialog below
                            }
                        },
                        onNavigateToCantalejoInfo = onNavigateToCantalejoInfo
                    )
                }
            }
        }
    }
    
    // Loading dialog for PDF URL validation
    if (uiState.isValidatingPDFURL) {
        AlertDialog(
            onDismissRequest = { /* Can't dismiss while loading */ },
            title = { ResponsiveText("Verificando enlace", compactSize = MaterialTheme.typography.titleLarge.fontSize, mediumSize = 19.sp, expandedSize = 20.sp) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    ResponsiveText("Verificando enlace del PDF...", compactSize = MaterialTheme.typography.bodyMedium.fontSize, mediumSize = MaterialTheme.typography.bodyMedium.fontSize, expandedSize = 15.sp)
                }
            },
            confirmButton = { /* No button while loading */ }
        )
    }

    // Error dialog for PDF URL errors
    uiState.pdfURLError?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPDFURLError() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    ResponsiveText("Error de acceso", compactSize = MaterialTheme.typography.titleLarge.fontSize, mediumSize = 19.sp, expandedSize = 20.sp)
                }
            },
            text = { ResponsiveText(errorMessage, compactSize = MaterialTheme.typography.bodyMedium.fontSize, mediumSize = MaterialTheme.typography.bodyMedium.fontSize, expandedSize = 15.sp) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearPDFURLError() }) {
                    ResponsiveText("Aceptar", compactSize = MaterialTheme.typography.bodyMedium.fontSize, mediumSize = MaterialTheme.typography.bodyMedium.fontSize, expandedSize = 15.sp)
                }
            }
        )
    }
    
    // Date picker dialog
    if (showDatePicker) {
        DatePickerDialog(
            currentSelectedDate = uiState.selectedDate,
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
            ResponsiveText(
                text = "Cargando calendarios...",
                compactSize = 14.sp,
                mediumSize = 15.sp,
                expandedSize = 16.sp,
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
                ResponsiveText(
                    text = "Error",
                    compactSize = 22.sp,
                    mediumSize = 24.sp,
                    expandedSize = 26.sp,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                ResponsiveText(
                    text = error,
                    compactSize = MaterialTheme.typography.bodyMedium.fontSize,
                    mediumSize = MaterialTheme.typography.bodyMedium.fontSize,
                    expandedSize = MaterialTheme.typography.bodyLarge.fontSize,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onDismiss) {
                        ResponsiveText("Cerrar", compactSize = MaterialTheme.typography.bodyMedium.fontSize, mediumSize = MaterialTheme.typography.bodyMedium.fontSize, expandedSize = 15.sp)
                    }
                    Button(onClick = onRetry) {
                        ResponsiveText("Reintentar", compactSize = MaterialTheme.typography.bodyMedium.fontSize, mediumSize = MaterialTheme.typography.bodyMedium.fontSize, expandedSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyContent(isOffline: Boolean = false) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isOffline) {
                // Offline + no cache scenario
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "Sin conexión",
                    modifier = Modifier.size(64.dp),
                    tint = androidx.compose.ui.graphics.Color(0xFFFFA726) // Amber warning color
                )
                ResponsiveText(
                    text = "Sin conexión y sin datos almacenados",
                    compactSize = 22.sp,
                    mediumSize = 24.sp,
                    expandedSize = 26.sp,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
                ResponsiveText(
                    text = "No hay conexión a Internet y no hay datos descargados para esta región.",
                    compactSize = MaterialTheme.typography.bodyMedium.fontSize,
                    mediumSize = MaterialTheme.typography.bodyMedium.fontSize,
                    expandedSize = MaterialTheme.typography.bodyLarge.fontSize,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                ResponsiveText(
                    text = "Conecte a Internet e intente refrescar para descargar los horarios.",
                    compactSize = MaterialTheme.typography.bodyMedium.fontSize,
                    mediumSize = MaterialTheme.typography.bodyMedium.fontSize,
                    expandedSize = MaterialTheme.typography.bodyLarge.fontSize,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else {
                // Normal empty state (no schedules in cache)
                Icon(
                    imageVector = Icons.Default.EventBusy,
                    contentDescription = "Sin horarios",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                ResponsiveText(
                    text = "No hay farmacias de guardia programadas",
                    compactSize = 22.sp,
                    mediumSize = 24.sp,
                    expandedSize = 26.sp,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                ResponsiveText(
                    text = "Intente refrescar o seleccione una fecha diferente",
                    compactSize = MaterialTheme.typography.bodyMedium.fontSize,
                    mediumSize = MaterialTheme.typography.bodyMedium.fontSize,
                    expandedSize = MaterialTheme.typography.bodyLarge.fontSize,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ScheduleContent(
    uiState: ScheduleViewModel.ScheduleUiState,
    onViewPDF: () -> Unit,
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
            ResponsiveText(
                text = "Farmacias de Guardia",
                compactSize = 24.sp,
                mediumSize = 26.sp,
                expandedSize = 28.sp,
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
                        if(uiState.location == DutyLocation.Companion.fromZBS(ZBS.Companion.CANTALEJO)) {
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
            // Get pharmacy info for error reporting - works for both current and selected date views
            val (pharmacy, shift) = when {
                // Selected date view - get first pharmacy from the first shift
                uiState.selectedDate != null && uiState.allShiftsForSelectedDate.isNotEmpty() -> {
                    val firstShift = uiState.allShiftsForSelectedDate.firstOrNull()
                    val firstPharmacy = firstShift?.second?.firstOrNull()
                    val shiftName = firstShift?.first?.let { timeSpan ->
                        when {
                            timeSpan == DutyTimeSpan.FullDay -> "24 horas"
                            timeSpan == DutyTimeSpan.CapitalDay -> "Diurno"
                            timeSpan == DutyTimeSpan.CapitalNight -> "Nocturno"
                            else -> timeSpan.displayName
                        }
                    }
                    Pair(firstPharmacy, shiftName)
                }
                // Current schedule view
                else -> {
                    val currentPharmacy = uiState.currentSchedule?.shifts?.get(uiState.activeTimeSpan)?.firstOrNull()
                    val shiftName = uiState.activeTimeSpan?.let { timeSpan ->
                        when {
                            timeSpan == DutyTimeSpan.FullDay -> "24 horas"
                            timeSpan == DutyTimeSpan.CapitalDay -> "Diurno"
                            timeSpan == DutyTimeSpan.CapitalNight -> "Nocturno"
                            else -> timeSpan.displayName
                        }
                    }
                    Pair(currentPharmacy, shiftName)
                }
            }
            
            DisclaimerCard(
                location = uiState.location!!, // TODO fix
                currentPharmacy = pharmacy,
                shiftName = shift,
                onViewPDF = onViewPDF
            )
        }
        
        // Last updated indicator (subtle cache age footnote at bottom)
        if (uiState.downloadDate != null) {
            item {
                LastUpdatedIndicator(downloadDate = uiState.downloadDate)
            }
        }


    }

    ShiftInfoModalSheet(uiState.activeTimeSpan, isVisible = showShiftInfo, onDismiss = { showShiftInfo = false })
}

/**
 * Subtle footnote indicator showing when the cached data was last updated
 * Shows relative time for recent updates, absolute date for older ones
 * Positioned at the very bottom of the schedule like a footnote
 */
@Composable
private fun LastUpdatedIndicator(downloadDate: Long) {
    val formattedDate = remember(downloadDate) {
        val now = System.currentTimeMillis()
        val diff = now - downloadDate
        val daysDiff = TimeUnit.MILLISECONDS.toDays(diff)
        
        when {
            daysDiff == 0L -> {
                val hoursDiff = TimeUnit.MILLISECONDS.toHours(diff)
                if (hoursDiff == 0L) {
                    "Actualizado hace menos de una hora"
                } else if (hoursDiff == 1L) {
                    "Actualizado hace 1 hora"
                } else {
                    "Actualizado hace $hoursDiff horas"
                }
            }
            daysDiff == 1L -> "Actualizado ayer"
            daysDiff < 7L -> "Actualizado hace $daysDiff días"
            else -> {
                // For older updates, show absolute date
                val formatter = SimpleDateFormat("d 'de' MMMM, yyyy", Locale("es", "ES"))
                "Actualizado el ${formatter.format(Date(downloadDate))}"
            }
        }
    }
    
    // Footnote style at bottom - centered and very subtle
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        ResponsiveText(
            text = "📥 $formattedDate",
            compactSize = 10.sp,
            mediumSize = 11.sp,
            expandedSize = 12.sp,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
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
            ResponsiveText(
                text = displayText,
                compactSize = 15.sp,
                mediumSize = MaterialTheme.typography.bodyLarge.fontSize,
                expandedSize = MaterialTheme.typography.titleMedium.fontSize,
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
    currentPharmacy: Pharmacy? = null,
    shiftName: String? = null,
    onViewPDF: () -> Unit
) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ResponsiveText(
                text = "Aviso",
                compactSize = MaterialTheme.typography.bodyMedium.fontSize,
                mediumSize = MaterialTheme.typography.bodyMedium.fontSize,
                expandedSize = MaterialTheme.typography.bodyLarge.fontSize,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            ResponsiveText(
                text = "La información mostrada puede no ser exacta. Por favor, consulte siempre la fuente oficial:",
                compactSize = MaterialTheme.typography.labelMedium.fontSize,
                mediumSize = MaterialTheme.typography.bodySmall.fontSize,
                expandedSize = 13.sp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            ResponsiveText(
                text = "Calendario de Guardias - ${location.associatedRegion.name}",
                compactSize = MaterialTheme.typography.labelMedium.fontSize,
                mediumSize = MaterialTheme.typography.bodySmall.fontSize,
                expandedSize = 13.sp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    onViewPDF()
                }
            )

            // Error reporting link - always clickable, different body based on whether there's a pharmacy
            ResponsiveText(
                text = "¿Ha encontrado algún error? Repórtelo aquí",
                compactSize = MaterialTheme.typography.labelMedium.fontSize,
                mediumSize = MaterialTheme.typography.bodySmall.fontSize,
                expandedSize = 13.sp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    val emailBody = if (currentPharmacy != null && shiftName != null) {
                        // Pharmacy exists - report error with pharmacy details
                        AppConfig.EmailLinks.currentScheduleContentErrorBody(
                            shiftName = shiftName,
                            pharmacyName = currentPharmacy.name,
                            pharmacyAddress = currentPharmacy.address
                        )
                    } else {
                        // No pharmacy assigned - report missing pharmacy
                        AppConfig.EmailLinks.currentNoPharmacyAssignedErrorBody(
                            location = location.name
                        )
                    }
                    val mailtoUri = AppConfig.EmailLinks.errorReport(body = emailBody)
                    val intent = Intent(Intent.ACTION_VIEW, mailtoUri.toUri())
                    context.startActivity(intent)
                }
            )
        }
    }
}

/**
 * Converts a local date to UTC midnight milliseconds for DatePicker initialization.
 * DatePicker expects UTC milliseconds but we want to show the date in local timezone.
 * If no date is provided, uses today's date.
 */
private fun getDateAtMidnightUtc(selectedDate: Calendar? = null): Long {
    // Use provided date or fall back to today
    val sourceCal = selectedDate ?: Calendar.getInstance()
    val year = sourceCal.get(Calendar.YEAR)
    val month = sourceCal.get(Calendar.MONTH)
    val day = sourceCal.get(Calendar.DAY_OF_MONTH)
    
    // Create UTC calendar with the same date but at midnight UTC
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utcCal.set(Calendar.YEAR, year)
    utcCal.set(Calendar.MONTH, month)
    utcCal.set(Calendar.DAY_OF_MONTH, day)
    utcCal.set(Calendar.HOUR_OF_DAY, 0)
    utcCal.set(Calendar.MINUTE, 0)
    utcCal.set(Calendar.SECOND, 0)
    utcCal.set(Calendar.MILLISECOND, 0)
    
    return utcCal.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialog(
    currentSelectedDate: Calendar?,
    onDateSelected: (Calendar) -> Unit,
    onDismiss: () -> Unit,
    onTodaySelected: () -> Unit
) {
    // Create sheet state for full-screen modal
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Get the current selected date (or today if none) as UTC midnight for proper DatePicker initialization
    val initialUtcMillis = getDateAtMidnightUtc(currentSelectedDate)
    
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialUtcMillis,
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
        
        // User has selected a date - convert from UTC back to local timezone
        datePickerState.selectedDateMillis?.let { utcMillis ->
            // DatePicker returns UTC milliseconds, convert to local date
            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = utcMillis 
            }
            
            // Create local calendar with the same date
            val localCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            onDateSelected(localCal)
            onDismiss()
        }
    }
    
    // Use ModalBottomSheet for proper stacking
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
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
                    ResponsiveText("Hoy", compactSize = MaterialTheme.typography.bodyMedium.fontSize, mediumSize = MaterialTheme.typography.bodyMedium.fontSize, expandedSize = 15.sp)
                }

                ResponsiveText(
                    text = "Seleccionar fecha",
                    compactSize = 15.sp,
                    mediumSize = MaterialTheme.typography.bodyLarge.fontSize,
                    expandedSize = MaterialTheme.typography.titleMedium.fontSize,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                TextButton(onClick = onDismiss) {
                    ResponsiveText("Cerrar", compactSize = MaterialTheme.typography.bodyMedium.fontSize, mediumSize = MaterialTheme.typography.bodyMedium.fontSize, expandedSize = 15.sp)
                }
            }
            
            // Date picker - full size with immediate selection
            DatePicker(
                state = datePickerState,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Bottom padding for better spacing
            Spacer(modifier = Modifier.size(32.dp))
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
