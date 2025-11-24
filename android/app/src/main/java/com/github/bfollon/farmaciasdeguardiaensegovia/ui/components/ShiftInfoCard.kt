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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
 * Card component that explains shift particularities for Segovia Capital
 * Can be used as a standalone card or within dialogs
 * Equivalent to iOS GuardiaInfoSheet content
 */
@Composable
fun ShiftInfoCard(
    dutyTimeSpan: DutyTimeSpan,
    showHeader: Boolean = true
) {

    val isEarlyMorning = remember {
        val now = LocalDateTime.now()
        val hour = now.hour
        val minute = now.minute
        hour < 10 || (hour == 10 && minute < 15)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showHeader) {
            ResponsiveText(
                text = "Horarios de Guardia",
                compactSize = MaterialTheme.typography.headlineSmall.fontSize,
                mediumSize = MaterialTheme.typography.headlineSmall.fontSize,
                expandedSize = MaterialTheme.typography.headlineSmall.fontSize,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        ShiftContent(dutyTimeSpan, isEarlyMorning = isEarlyMorning)

    }
}

@Composable
private fun ShiftContent(
    dutyTimeSpan: DutyTimeSpan,
    isEarlyMorning: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Shift header with icon and time
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WbSunny,
                contentDescription = dutyTimeSpan.displayName,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ResponsiveText(
                    text = dutyTimeSpan.displayName,
                    compactSize = MaterialTheme.typography.titleMedium.fontSize,
                    mediumSize = MaterialTheme.typography.titleMedium.fontSize,
                    expandedSize = MaterialTheme.typography.titleMedium.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                ResponsiveText(
                    text = dutyTimeSpan.displayFormat,
                    compactSize = MaterialTheme.typography.bodyLarge.fontSize,
                    mediumSize = MaterialTheme.typography.bodyLarge.fontSize,
                    expandedSize = MaterialTheme.typography.bodyLarge.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Explanation text
        ResponsiveText(
            text = dutyTimeSpan.shiftInfo,
            compactSize = MaterialTheme.typography.bodyMedium.fontSize,
            mediumSize = MaterialTheme.typography.bodyMedium.fontSize,
            expandedSize = MaterialTheme.typography.bodyMedium.fontSize,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )

        if (dutyTimeSpan.spansMultipleDays && isEarlyMorning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )

                    ResponsiveText(
                        text = "Por ello, la farmacia que está de guardia ahora comenzó su turno ayer a las 22:00.",
                        compactSize = MaterialTheme.typography.bodySmall.fontSize,
                        mediumSize = MaterialTheme.typography.bodySmall.fontSize,
                        expandedSize = MaterialTheme.typography.bodySmall.fontSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

/**
 * Preview for day shift card
 */
@Preview(showBackground = true)
@Composable
private fun ShiftInfoCardDayPreview() {
    FarmaciasDeGuardiaEnSegoviaTheme {
        ShiftInfoCard(
            DutyTimeSpan.Companion.CapitalDay,
        )
    }
}

/**
 * Preview for night shift card
 */
@Preview(showBackground = true)
@Composable
private fun ShiftInfoCardNightPreview() {
    FarmaciasDeGuardiaEnSegoviaTheme {
        ShiftInfoCard(
            DutyTimeSpan.Companion.CapitalNight,
        )
    }
}

