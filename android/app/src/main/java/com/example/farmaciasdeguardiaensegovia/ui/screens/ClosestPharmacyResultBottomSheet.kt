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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmaciasdeguardiaensegovia.services.ClosestPharmacyResult
import java.net.URLEncoder

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
                .wrapContentHeight()
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
                
                Text(
                    text = "Farmacia más cercana",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "De guardia y abierta ahora",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            
            // Pharmacy information card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Pharmacy name
                    Text(
                        text = result.pharmacy.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Distance and travel time
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        
                        Column {
                            Text(
                                text = result.formattedDistance,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (result.formattedTravelTime.isNotEmpty()) {
                                Text(
                                    text = "🚗 ${result.formattedTravelTime}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (result.formattedWalkingTime.isNotEmpty()) {
                                Text(
                                    text = "🚶 ${result.formattedWalkingTime}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    // Address
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = result.pharmacy.address,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    
                    // Phone (if available)
                    if (result.pharmacy.phone.isNotEmpty() && result.pharmacy.phone != "No disponible") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Text(
                                text = result.pharmacy.phone,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    
                    // Region/ZBS info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = result.regionDisplayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    
                    // Duty time info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = result.timeSpan.displayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    
                    // Additional info (if available)
                    result.pharmacy.additionalInfo?.let { info ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Text(
                                text = info,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
private fun openInMaps(context: android.content.Context, result: ClosestPharmacyResult) {
    // Use enhanced query with pharmacy name (same as iOS)
    val query = "${result.pharmacy.name}, ${result.pharmacy.address}, Segovia, Spain"
    val encodedQuery = URLEncoder.encode(query, "UTF-8")
    
    // Try to open in Google Maps first, fallback to generic maps intent
    val googleMapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encodedQuery"))
    googleMapsIntent.setPackage("com.google.android.apps.maps")
    
    if (googleMapsIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(googleMapsIntent)
    } else {
        // Fallback to generic maps intent
        val genericMapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encodedQuery"))
        context.startActivity(genericMapsIntent)
    }
}

/**
 * Call the pharmacy
 */
private fun callPharmacy(context: android.content.Context, phoneNumber: String) {
    val cleanPhoneNumber = phoneNumber.replace(" ", "")
    val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhoneNumber"))
    context.startActivity(callIntent)
}
