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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmaciasdeguardiaensegovia.services.ClosestPharmacyResult
import com.example.farmaciasdeguardiaensegovia.ui.theme.IOSBlue
import com.example.farmaciasdeguardiaensegovia.ui.theme.IOSGreen
import java.net.URLEncoder

/**
 * Screen that displays the closest pharmacy result
 * Equivalent to iOS ClosestPharmacyResultView
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClosestPharmacyResultScreen(
    result: ClosestPharmacyResult,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showingMapOptions by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resultado") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocalPharmacy,
                    contentDescription = null,
                    tint = IOSGreen,
                    modifier = Modifier.size(60.dp)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Farmacia más cercana",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "De guardia y abierta ahora",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Pharmacy info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Pharmacy name
                    Text(
                        text = result.pharmacy.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // Distance and travel time
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            tint = IOSBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        
                        Column {
                            Text(
                                text = result.formattedDistance,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (result.formattedTravelTime.isNotEmpty()) {
                                Text(
                                    text = "🚗 ${result.formattedTravelTime}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (result.formattedWalkingTime.isNotEmpty()) {
                                Text(
                                    text = "🚶 ${result.formattedWalkingTime}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    // Address
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                        
                        Text(
                            text = result.pharmacy.address,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    // Phone
                    if (result.pharmacy.phone.isNotEmpty() && result.pharmacy.phone != "No disponible") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = IOSGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            
                            Text(
                                text = result.pharmacy.phone,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    // Region/ZBS info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                        
                        Text(
                            text = result.regionDisplayName,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    // Duty time info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                        
                        Text(
                            text = result.timeSpan.displayName,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    // Additional info if available
                    result.pharmacy.additionalInfo?.let { info ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp)
                            )
                            
                            Text(
                                text = info,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Action buttons
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Open in Maps button
                        Button(
                            onClick = {
                                openInMaps(context, result)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IOSBlue
                            )
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
                            Button(
                                onClick = {
                                    callPharmacy(context, result.pharmacy.phone)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = IOSGreen
                                )
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
            
            Spacer(modifier = Modifier.height(20.dp))
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
