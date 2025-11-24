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

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.github.bfollon.farmaciasdeguardiaensegovia.services.ClosestPharmacyResult
import java.net.URLEncoder
import androidx.core.net.toUri
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.components.ResponsiveText

/**
 * Material 3 ModalBottomSheet that displays the closest pharmacy result
 * Matches iOS content structure but uses Material 3 design system
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClosestPharmacyResultBottomSheet(
    result: ClosestPharmacyResult,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 40.dp), // Extra bottom padding for gesture area
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with pharmacy icon and title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocalPharmacy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                ResponsiveText(
                    text = "Farmacia más cercana",
                    compactSize = MaterialTheme.typography.headlineSmall.fontSize,
                    mediumSize = MaterialTheme.typography.headlineMedium.fontSize,
                    expandedSize = MaterialTheme.typography.headlineMedium.fontSize,
                    textAlign = TextAlign.Center
                )
                
                ResponsiveText(
                    text = "De guardia y abierta ahora",
                    compactSize = MaterialTheme.typography.bodyMedium.fontSize,
                    mediumSize = MaterialTheme.typography.bodyLarge.fontSize,
                    expandedSize = MaterialTheme.typography.bodyLarge.fontSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            
            // Card 1: Pharmacy name and distance/time info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Pharmacy name
                    ResponsiveText(
                        text = result.pharmacy.name,
                        compactSize = MaterialTheme.typography.titleMedium.fontSize,
                        mediumSize = MaterialTheme.typography.titleLarge.fontSize,
                        expandedSize = MaterialTheme.typography.titleLarge.fontSize,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Inline distance and travel time (iOS style)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Car icon + distance
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        ResponsiveText(
                            text = result.formattedDistance,
                            compactSize = MaterialTheme.typography.titleMedium.fontSize,
                            mediumSize = MaterialTheme.typography.titleMedium.fontSize,
                            expandedSize = MaterialTheme.typography.titleMedium.fontSize,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        
                        // Travel time (if available)
                        if (result.formattedTravelTime.isNotEmpty()) {
                            ResponsiveText(
                                text = "•",
                                compactSize = MaterialTheme.typography.titleMedium.fontSize,
                            mediumSize = MaterialTheme.typography.titleMedium.fontSize,
                            expandedSize = MaterialTheme.typography.titleMedium.fontSize,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            ResponsiveText(
                                text = result.formattedTravelTime,
                                compactSize = MaterialTheme.typography.titleMedium.fontSize,
                            mediumSize = MaterialTheme.typography.titleMedium.fontSize,
                            expandedSize = MaterialTheme.typography.titleMedium.fontSize,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        // Walking time (if available)
                        if (result.formattedWalkingTime.isNotEmpty()) {
                            ResponsiveText(
                                text = "•",
                                compactSize = MaterialTheme.typography.titleMedium.fontSize,
                            mediumSize = MaterialTheme.typography.titleMedium.fontSize,
                            expandedSize = MaterialTheme.typography.titleMedium.fontSize,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            ResponsiveText(
                                text = result.formattedWalkingTime,
                                compactSize = MaterialTheme.typography.titleMedium.fontSize,
                            mediumSize = MaterialTheme.typography.titleMedium.fontSize,
                            expandedSize = MaterialTheme.typography.titleMedium.fontSize,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            // Divider
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            
            // Card 2: Contact information (Address and Phone)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Address section
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ResponsiveText(
                            text = "Dirección",
                            compactSize = MaterialTheme.typography.titleMedium.fontSize,
                            mediumSize = MaterialTheme.typography.titleMedium.fontSize,
                            expandedSize = MaterialTheme.typography.titleMedium.fontSize,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        ResponsiveText(
                            text = result.pharmacy.address,
                            compactSize = MaterialTheme.typography.bodyMedium.fontSize,
                    mediumSize = MaterialTheme.typography.bodyLarge.fontSize,
                    expandedSize = MaterialTheme.typography.bodyLarge.fontSize,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable {
                                openInMaps(context, result)
                            }
                        )
                    }
                    
                    // Phone section (if available)
                    if (result.pharmacy.phone.isNotEmpty() && result.pharmacy.phone != "No disponible") {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ResponsiveText(
                                text = "Teléfono",
                                compactSize = MaterialTheme.typography.titleMedium.fontSize,
                            mediumSize = MaterialTheme.typography.titleMedium.fontSize,
                            expandedSize = MaterialTheme.typography.titleMedium.fontSize,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            ResponsiveText(
                                text = result.pharmacy.phone,
                                compactSize = MaterialTheme.typography.bodyMedium.fontSize,
                    mediumSize = MaterialTheme.typography.bodyLarge.fontSize,
                    expandedSize = MaterialTheme.typography.bodyLarge.fontSize,
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.clickable {
                                    callPharmacy(context, result.pharmacy.phone)
                                }
                            )
                        }
                    }
                }
            }
            
            // Divider
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            
            // Card 3: Service information
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ResponsiveText(
                        text = "Información del servicio",
                        compactSize = MaterialTheme.typography.titleMedium.fontSize,
                            mediumSize = MaterialTheme.typography.titleMedium.fontSize,
                            expandedSize = MaterialTheme.typography.titleMedium.fontSize,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    // Region info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ResponsiveText(
                            text = "Región:",
                            compactSize = MaterialTheme.typography.bodyMedium.fontSize,
                    mediumSize = MaterialTheme.typography.bodyLarge.fontSize,
                    expandedSize = MaterialTheme.typography.bodyLarge.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ResponsiveText(
                            text = result.regionDisplayName,
                            compactSize = MaterialTheme.typography.bodyMedium.fontSize,
                    mediumSize = MaterialTheme.typography.bodyLarge.fontSize,
                    expandedSize = MaterialTheme.typography.bodyLarge.fontSize,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // Schedule info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ResponsiveText(
                            text = "Horario:",
                            compactSize = MaterialTheme.typography.bodyMedium.fontSize,
                    mediumSize = MaterialTheme.typography.bodyLarge.fontSize,
                    expandedSize = MaterialTheme.typography.bodyLarge.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ResponsiveText(
                            text = result.timeSpan.displayName,
                            compactSize = MaterialTheme.typography.bodyMedium.fontSize,
                    mediumSize = MaterialTheme.typography.bodyLarge.fontSize,
                    expandedSize = MaterialTheme.typography.bodyLarge.fontSize,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // Additional info (if available)
                    result.pharmacy.additionalInfo?.let { info ->
                        if (info.isNotEmpty()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ResponsiveText(
                                    text = "Información adicional",
                                    compactSize = MaterialTheme.typography.titleMedium.fontSize,
                                    mediumSize = MaterialTheme.typography.titleMedium.fontSize,
                                    expandedSize = MaterialTheme.typography.titleMedium.fontSize,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                ResponsiveText(
                                    text = info,
                                    compactSize = MaterialTheme.typography.bodyMedium.fontSize,
                                    mediumSize = MaterialTheme.typography.bodyMedium.fontSize,
                                    expandedSize = MaterialTheme.typography.bodyLarge.fontSize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Action buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Open in Maps button
                Button(
                    onClick = {
                        openInMaps(context, result)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Abrir en Mapas")
                }
                
                // Call button (if phone available)
                if (result.pharmacy.phone.isNotEmpty() && result.pharmacy.phone != "No disponible") {
                    FilledTonalButton(
                        onClick = {
                            callPharmacy(context, result.pharmacy.phone)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Llamar")
                    }
                }
            }
        }
    }
}

/**
 * Open pharmacy location in maps app
 */
private fun openInMaps(context: Context, result: ClosestPharmacyResult) {
    // Use enhanced query with pharmacy name (same as iOS)
    val query = "${result.pharmacy.name}, ${result.pharmacy.address}, Segovia, Spain"
    val encodedQuery = URLEncoder.encode(query, "UTF-8")
    
    // Try to open in Google Maps first, fallback to generic maps intent
    val googleMapsIntent = Intent(Intent.ACTION_VIEW, "geo:0,0?q=$encodedQuery".toUri())
    googleMapsIntent.setPackage("com.google.android.apps.maps")
    
    if (googleMapsIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(googleMapsIntent)
    } else {
        // Fallback to generic maps intent
        val genericMapsIntent = Intent(Intent.ACTION_VIEW, "geo:0,0?q=$encodedQuery".toUri())
        context.startActivity(genericMapsIntent)
    }
}

/**
 * Call the pharmacy
 */
private fun callPharmacy(context: Context, phoneNumber: String) {
    val cleanPhoneNumber = phoneNumber.replace(" ", "")
    val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhoneNumber"))
    context.startActivity(callIntent)
}
