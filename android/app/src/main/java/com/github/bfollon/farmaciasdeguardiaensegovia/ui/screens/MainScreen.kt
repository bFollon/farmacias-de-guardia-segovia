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

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    onAboutClick: () -> Unit = {},
) {
    // Check network status on screen load
    var isOffline by remember { mutableStateOf(false) }
    var showOfflineDialog by remember { mutableStateOf(false) }

    val spacerSeparation = 0.05f
    val mainScreenPadding = 16.dp

    LaunchedEffect(Unit) {
        isOffline = !NetworkMonitor.isOnline()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeContent,
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
//                .statusBarsPadding()
//                .navigationBarsPadding(),
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

            Spacer(modifier = Modifier.weight(spacerSeparation))

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
//                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            )

            Spacer(modifier = Modifier.weight(spacerSeparation))

            // Subtitle
            Text(
                text = "Seleccione su región para consultar las farmacias de guardia.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = mainScreenPadding)
            )

            Spacer(modifier = Modifier.weight(spacerSeparation))

            // Offline warning card (appears below subtitle when offline)
            if (isOffline) {
                OfflineWarningCard(
                    modifier = Modifier.padding(horizontal = mainScreenPadding),
                    isClickable = true,
                    onClick = { showOfflineDialog = true }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.weight(spacerSeparation))

            // Closest pharmacy finder
            ClosestPharmacyButton(
                modifier = Modifier.padding(horizontal = mainScreenPadding)
            )

            Spacer(modifier = Modifier.weight(spacerSeparation))

            // Region selection grid
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = mainScreenPadding)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Region.allRegions.chunked(2).forEach { regionPair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        regionPair.forEach { region ->
                            RegionCard(
                                region = region,
                                onClick = {
                                    if (region.id == "segovia-rural") {
                                        onZBSSelectionRequested()
                                    } else {
                                        onRegionSelected(region)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(spacerSeparation))
//
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
        modifier = modifier,
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
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            // Granular responsive sizing based on card width
            val (iconStyle, textStyle, padding) = when {
                maxWidth < 160.dp -> Triple(
                    MaterialTheme.typography.headlineSmall,
                    MaterialTheme.typography.bodySmall,
                    8.dp
                )

                maxWidth < 180.dp -> Triple(
                    MaterialTheme.typography.headlineMedium,
                    MaterialTheme.typography.bodyMedium,
                    12.dp
                )

                else -> Triple(
                    MaterialTheme.typography.headlineLarge,
                    MaterialTheme.typography.bodyLarge,
                    12.dp
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(padding, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Region icon emoji
                Text(
                    text = region.icon,
                    style = iconStyle,
                )

                // Region name
                Text(
                    text = region.name,
                    style = textStyle,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ScreenPreview() {
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
