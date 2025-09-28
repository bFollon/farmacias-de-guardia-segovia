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

package com.example.farmaciasdeguardiaensegovia.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.farmaciasdeguardiaensegovia.services.CoordinateCache
import com.example.farmaciasdeguardiaensegovia.ui.screens.ClosestPharmacyResultScreen
import com.example.farmaciasdeguardiaensegovia.ui.theme.IOSBlue
import com.example.farmaciasdeguardiaensegovia.ui.theme.IOSGreen
import com.example.farmaciasdeguardiaensegovia.viewmodels.ClosestPharmacyViewModel
import com.example.farmaciasdeguardiaensegovia.viewmodels.SearchStep

/**
 * Button component for finding the closest pharmacy
 * Equivalent to iOS ClosestPharmacyView
 */
@Composable
fun ClosestPharmacyButton(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: ClosestPharmacyViewModel = viewModel { ClosestPharmacyViewModel(context) }
    val uiState by viewModel.uiState.collectAsState()
    
    // Initialize coordinate cache
    LaunchedEffect(Unit) {
        CoordinateCache.initialize(context)
    }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineLocationGranted || coarseLocationGranted) {
            viewModel.findClosestPharmacy()
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = IOSBlue.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp),
        onClick = {
            if (viewModel.hasLocationPermission()) {
                viewModel.findClosestPharmacy()
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.isSearching) {
                // Show search progress (matching iOS)
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = IOSBlue
                )
                
                Text(
                    text = viewModel.getSearchStepText(uiState.searchStep),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                
                // Show retry count if retrying
                if (uiState.retryCount > 0) {
                    Text(
                        text = "Reintentando... (${uiState.retryCount}/3)",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Show normal button state
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = IOSGreen,
                    modifier = Modifier.size(32.dp)
                )
                
                Text(
                    text = "Farmacia más cercana",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "Encuentra la farmacia de guardia más cercana",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            
            // Error message
            uiState.errorMessage?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
    
    // Show result screen when available
    uiState.result?.let { result ->
        if (uiState.showingResult) {
            ClosestPharmacyResultScreen(
                result = result,
                onDismiss = viewModel::dismissResult
            )
        }
    }
}
