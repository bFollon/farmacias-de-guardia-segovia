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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bfollon.farmaciasdeguardiaensegovia.data.Region
import com.github.bfollon.farmaciasdeguardiaensegovia.services.NetworkMonitor
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.components.ClosestPharmacyButton
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.components.OfflineWarningCard
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.theme.FarmaciasDeGuardiaEnSegoviaTheme
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.theme.IOSBlue
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.theme.IOSGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onRegionSelected: (Region) -> Unit = {},
    onZBSSelectionRequested: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onAboutClick: () -> Unit = {}
) {
    // Check network status on screen load
    var isOffline by remember { mutableStateOf(false) }
    var showOfflineDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        isOffline = !NetworkMonitor.isOnline()
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Offline dialog (shown when card is tapped)
            if (showOfflineDialog) {
                OfflineDialog(
                    onDismiss = { showOfflineDialog = false },
                    onGoToSettings = {
                        showOfflineDialog = false
                        onSettingsClick()
                    }
                )
            }
            
            // Top bar with settings button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onSettingsClick
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configuración",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Main title with gradient effect - responsive font size
            Text(
                text = "Farmacias de Guardia",
                style = MaterialTheme.typography.headlineMedium.copy(
                    brush = Brush.linearGradient(
                        colors = listOf(IOSBlue, IOSGreen)
                    )
                ),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle
            Text(
                text = "Seleccione su región para consultar las farmacias de guardia.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Offline warning card (appears below subtitle when offline)
            if (isOffline) {
                OfflineWarningCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    isClickable = true,
                    onClick = { showOfflineDialog = true }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Closest pharmacy finder
            ClosestPharmacyButton(
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Region selection grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                items(Region.Companion.allRegions) { region ->
                    RegionCard(
                        region = region,
                        onClick = {
                            if (region.id == "segovia-rural") {
                                onZBSSelectionRequested()
                            } else {
                                onRegionSelected(region)
                            }
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // About button
            TextButton(
                onClick = onAboutClick
            ) {
                Text(
                    text = "Acerca de",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = IOSBlue
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Dialog explaining offline mode with option to navigate to settings
 */
@Composable
fun OfflineDialog(
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Text(
                text = "⚠️",
                style = MaterialTheme.typography.titleLarge
            )
        },
        title = {
            Text(
                text = "Modo sin conexión",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "No hay conexión a Internet. La aplicación está usando datos almacenados localmente.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Los horarios mostrados corresponden a la última actualización descargada.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onGoToSettings) {
                Text(
                    text = "Ir a Ajustes",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cerrar",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        iconContentColor = Color(0xFFFFA726),
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun RegionCard(
    region: Region,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = IOSBlue.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                colors = listOf(
                    IOSBlue.copy(alpha = 0.3f),
                    IOSBlue.copy(alpha = 0.3f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Region icon emoji
            Text(
                text = region.icon,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Region name
            Text(
                text = region.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 20.sp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    FarmaciasDeGuardiaEnSegoviaTheme {
        MainScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun RegionCardPreview() {
    FarmaciasDeGuardiaEnSegoviaTheme {
        RegionCard(
            region = Region.Companion.segoviaCapital,
            onClick = {}
        )
    }
}
