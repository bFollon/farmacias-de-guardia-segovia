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
import com.github.bfollon.farmaciasdeguardiaensegovia.services.CoordinateCache
import com.github.bfollon.farmaciasdeguardiaensegovia.services.RouteCache
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.screens.ClosestPharmacyResultBottomSheet
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.theme.IOSBlue
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.theme.IOSGreen
import com.github.bfollon.farmaciasdeguardiaensegovia.viewmodels.ClosestPharmacyViewModel

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
    
    // Initialize caches
    LaunchedEffect(Unit) {
        CoordinateCache.initialize(context)
        RouteCache.initialize(context)
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
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        val buttonHeight = responsiveDimension(
            compactSize = 120.dp,
            mediumSize = 140.dp,
            expandedSize = 160.dp
        )
        val spacingLarge = responsiveDimension(
            compactSize = 4.dp,
            mediumSize = 6.dp,
            expandedSize = 8.dp
        )
        val spacingSmall = responsiveDimension(
            compactSize = 2.dp,
            mediumSize = 3.dp,
            expandedSize = 4.dp
        )

        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(buttonHeight),
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
                    .fillMaxHeight()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (uiState.isSearching) {
                    // Show search progress (matching iOS)
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = IOSBlue
                    )

                    Spacer(modifier = Modifier.height(spacingLarge))

                    Text(
                        text = viewModel.getSearchStepText(uiState.searchStep),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    // Show retry count if retrying
                    if (uiState.retryCount > 0) {
                        Spacer(modifier = Modifier.height(spacingSmall))

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

                    Spacer(modifier = Modifier.height(spacingLarge))

                    Text(
                        text = "Farmacia más cercana",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(spacingSmall))

                    Text(
                        text = "Encuentra la farmacia de guardia más cercana",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Error message outside Card
        uiState.errorMessage?.let { errorMessage ->
            Spacer(modifier = Modifier.height(spacingLarge))

            Text(
                text = errorMessage,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
        }
    }
    
    // Show result bottom sheet when available
    uiState.result?.let { result ->
        if (uiState.showingResult) {
            ClosestPharmacyResultBottomSheet(
                result = result,
                onDismiss = viewModel::dismissResult
            )
        }
    }
}
