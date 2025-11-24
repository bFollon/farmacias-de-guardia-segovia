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

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.github.bfollon.farmaciasdeguardiaensegovia.data.Pharmacy
import java.net.URLEncoder

/**
 * Component for displaying pharmacy information
 * Equivalent to iOS PharmacyView
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PharmacyCard(
    pharmacy: Pharmacy,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 8.dp else 4.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Active indicator or duty schedule warning
            if (isActive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Activa ahora",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Activa ahora",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                // Duty schedule warning banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color.Yellow.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color(0xFFFF9800)
                                .copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Advertencia",
                        tint = Color(0xFFFF9800), // Orange color
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Fuera del horario de guardia",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Black,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Pharmacy name
            Text(
                text = pharmacy.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Address with map button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Dirección",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = pharmacy.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        val encodedQuery = buildMapsQuery(pharmacy.name, pharmacy.address)
                        val intent = Intent(Intent.ACTION_VIEW, "geo:0,0?q=$encodedQuery".toUri())
                        context.startActivity(intent)
                    }
                )
            }

            // Phone with call button
            if (pharmacy.phone.isNotEmpty() && pharmacy.phone != "No disponible") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Teléfono",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = pharmacy.formattedPhone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            val intent =
                                Intent(Intent.ACTION_DIAL, "tel:${pharmacy.phone}".toUri())
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // Additional info
            pharmacy.additionalInfo?.let { info ->
                if (info.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Información adicional",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = info,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Build a Google Maps query with smart location context
 * Checks if "Segovia" and "Spain" are present and only appends if missing
 */
private fun buildMapsQuery(pharmacyName: String, address: String): String {
    // Build query starting with pharmacy name and address
    var query = "$pharmacyName, $address"
    
    // Check if address already contains "Segovia" (case-insensitive)
    if (!address.contains("Segovia", ignoreCase = true)) {
        query += ", Segovia"
    }
    
    // Check if address already contains "Spain" (case-insensitive)
    if (!address.contains("Spain", ignoreCase = true)) {
        query += ", Spain"
    }
    
    // URL-encode the final query
    return URLEncoder.encode(query, "UTF-8")
}
