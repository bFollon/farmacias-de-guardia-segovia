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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyTimeSpan
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.theme.FarmaciasDeGuardiaEnSegoviaTheme
import java.time.LocalDateTime

/**
 * Enum representing shift types for Segovia Capital
 */
enum class ShiftType {
    DAY, NIGHT
}

/**
 * Bottom sheet component that explains shift particularities for Segovia Capital
 * Equivalent to iOS GuardiaInfoSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftInfoSheet(
    shiftType: ShiftType,
    modifier: Modifier = Modifier,
    date: LocalDateTime = LocalDateTime.now(),
    onDismiss: () -> Unit = {}
) {
    val isEarlyMorning = remember {
        val now = LocalDateTime.now()
        val hour = now.hour
        val minute = now.minute
        hour < 10 || (hour == 10 && minute < 15)
    }
    
    val isCurrentDay = remember {
        val now = LocalDateTime.now()
        date.toLocalDate() == now.toLocalDate()
    }
    
    BottomSheetScaffold(
        modifier = modifier,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Horarios de Guardia",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Shift explanation content
                when (shiftType) {
                    ShiftType.DAY -> {
                        DayShiftExplanation()
                    }
                    ShiftType.NIGHT -> {
                        NightShiftExplanation(
                            isCurrentDay = isCurrentDay,
                            isEarlyMorning = isEarlyMorning
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        },
        sheetPeekHeight = 0.dp,
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        content = {}
    )
}

@Composable
private fun DayShiftExplanation() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WbSunny,
                contentDescription = "Turno diurno",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Turno Diurno",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Text(
                    text = DutyTimeSpan.Companion.CapitalDay.displayFormat,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    
    Text(
        text = "El turno diurno empieza a las 10:15 y se extiende hasta las 22:00 del mismo día.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 24.sp
    )
}

@Composable
private fun NightShiftExplanation(
    isCurrentDay: Boolean,
    isEarlyMorning: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Nightlight,
                contentDescription = "Turno nocturno",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
            
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Turno Nocturno",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Text(
                    text = DutyTimeSpan.Companion.CapitalNight.displayFormat,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "El turno nocturno empieza a las 22:00 y se extiende hasta las 10:15 del día siguiente.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 24.sp
        )
        
        if (isCurrentDay && isEarlyMorning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Información",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )

                    Text(
                        text = "Por ello, la farmacia que está de guardia ahora comenzó su turno ayer a las 22:00.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

/**
 * Preview for day shift
 */
@Preview(showBackground = true)
@Composable
private fun ShiftInfoSheetDayPreview() {
    FarmaciasDeGuardiaEnSegoviaTheme {
        ShiftInfoSheet(
            shiftType = ShiftType.DAY,
            date = LocalDateTime.now()
        )
    }
}

/**
 * Preview for night shift
 */
@Preview(showBackground = true)
@Composable
private fun ShiftInfoSheetNightPreview() {
    FarmaciasDeGuardiaEnSegoviaTheme {
        ShiftInfoSheet(
            shiftType = ShiftType.NIGHT,
            date = LocalDateTime.now()
        )
    }
}

/**
 * Preview for night shift in early morning
 */
@Preview(showBackground = true)
@Composable
private fun ShiftInfoSheetNightEarlyMorningPreview() {
    FarmaciasDeGuardiaEnSegoviaTheme {
        ShiftInfoSheet(
            shiftType = ShiftType.NIGHT,
            date = LocalDateTime.now().minusDays(1) // Yesterday's date
        )
    }
}
