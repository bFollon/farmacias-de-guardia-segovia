package com.github.bfollon.farmaciasdeguardiaensegovia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.bfollon.farmaciasdeguardiaensegovia.data.Region
import com.github.bfollon.farmaciasdeguardiaensegovia.data.ZBS
import com.github.bfollon.farmaciasdeguardiaensegovia.services.CoordinateCache
import com.github.bfollon.farmaciasdeguardiaensegovia.services.DebugConfig
import com.github.bfollon.farmaciasdeguardiaensegovia.services.NetworkMonitor
import com.github.bfollon.farmaciasdeguardiaensegovia.services.RouteCache
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.screens.AboutScreen
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.screens.CacheRefreshScreen
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.screens.CacheStatusScreen
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.screens.CantalejoInfoScreen
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.screens.MainScreen
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.screens.ScheduleScreen
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.screens.SettingsScreen
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.screens.ZBSSelectionScreen
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.theme.FarmaciasDeGuardiaEnSegoviaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize network monitor
        NetworkMonitor.initialize(this)
        
        // Initialize caches and cleanup expired entries
        CoordinateCache.initialize(this)
        RouteCache.initialize(this)
        
        // Cleanup expired cache entries on app start
        CoordinateCache.cleanupExpiredEntries()
        RouteCache.cleanupExpiredEntries()
        
        setContent {
            FarmaciasDeGuardiaEnSegoviaTheme {
                AppNavigation()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    // State management for ZBS Selection modal
    var showZBSSelectionModal by remember { mutableStateOf(false) }
    val zbsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // State management for Schedule modal
    var showScheduleModal by remember { mutableStateOf(false) }
    var selectedLocationId by remember { mutableStateOf<String?>(null) }
    val scheduleSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // State management for Cantalejo Info modal (stacks on top of Schedule modal)
    var showCantalejoInfo by remember { mutableStateOf(false) }
    val cantalejoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // State management for Settings modal
    var showSettingsModal by remember { mutableStateOf(false) }
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // State management for About modal
    var showAboutModal by remember { mutableStateOf(false) }
    val aboutSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // State management for Cache Status modal (stacks on top of Settings)
    var showCacheStatusModal by remember { mutableStateOf(false) }
    val cacheStatusSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // State management for Cache Refresh modal (stacks on top of Settings)
    var showCacheRefreshModal by remember { mutableStateOf(false) }
    val cacheRefreshSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") {
            SplashScreen(
                onSplashFinished = {
                    DebugConfig.debugPrint("Navigating from splash screen")
                    navController.navigate("main") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }
        
        composable("main") {
            MainScreen(
                onRegionSelected = { region ->
                    when (region) {
                        Region.Companion.segoviaCapital, Region.Companion.cuellar, Region.Companion.elEspinar -> {
                            selectedLocationId = region.id
                            showScheduleModal = true
                        }

                        else -> {
                            // TODO: Implement Segovia Rural region
                        }
                    }
                },
                onZBSSelectionRequested = {
                    showZBSSelectionModal = true
                },
                onSettingsClick = {
                    showSettingsModal = true
                },
                onAboutClick = {
                    showAboutModal = true
                }
            )
        }
    }
    
    // Schedule Modal (state-based presentation)
    if (showScheduleModal && selectedLocationId != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showScheduleModal = false
                selectedLocationId = null
            },
            sheetState = scheduleSheetState
        ) {
            ScheduleScreen(
                locationId = selectedLocationId!!,
                onBack = {
                    showScheduleModal = false
                    selectedLocationId = null
                },
                onNavigateToCantalejoInfo = {
                    showCantalejoInfo = true
                }
            )
        }
    }
    
    // Cantalejo Info Modal (stacks on top of Schedule modal)
    if (showCantalejoInfo) {
        ModalBottomSheet(
            onDismissRequest = {
                showCantalejoInfo = false
            },
            sheetState = cantalejoSheetState
        ) {
            CantalejoInfoScreen(
                onDismiss = {
                    showCantalejoInfo = false
                }
            )
        }
    }
    
    // ZBS Selection Modal
    if (showZBSSelectionModal) {
        ModalBottomSheet(
            onDismissRequest = {
                showZBSSelectionModal = false
            },
            sheetState = zbsSheetState
        ) {
            ZBSSelectionScreen(
                onZBSSelected = { zbs ->
                    selectedLocationId = zbs.id
                    showScheduleModal = true
                    // Keep ZBS modal open underneath so it stacks properly
                },
                onDismiss = {
                    showZBSSelectionModal = false
                }
            )
        }
    }
    
    // Settings Modal
    if (showSettingsModal) {
        ModalBottomSheet(
            onDismissRequest = {
                showSettingsModal = false
            },
            sheetState = settingsSheetState
        ) {
            SettingsScreen(
                onDismiss = {
                    showSettingsModal = false
                },
                onAboutClick = {
                    showAboutModal = true
                },
                onCacheStatusClick = {
                    showCacheStatusModal = true
                },
                onCacheRefreshClick = {
                    showCacheRefreshModal = true
                }
            )
        }
    }
    
    // About Modal
    if (showAboutModal) {
        ModalBottomSheet(
            onDismissRequest = {
                showAboutModal = false
            },
            sheetState = aboutSheetState
        ) {
            AboutScreen(
                onDismiss = {
                    showAboutModal = false
                }
            )
        }
    }
    
    // Cache Status Modal (stacks on top of Settings modal)
    if (showCacheStatusModal) {
        ModalBottomSheet(
            onDismissRequest = {
                showCacheStatusModal = false
            },
            sheetState = cacheStatusSheetState
        ) {
            CacheStatusScreen(
                onDismiss = {
                    showCacheStatusModal = false
                }
            )
        }
    }
    
    // Cache Refresh Modal (stacks on top of Settings modal)
    if (showCacheRefreshModal) {
        ModalBottomSheet(
            onDismissRequest = {
                showCacheRefreshModal = false
            },
            sheetState = cacheRefreshSheetState
        ) {
            CacheRefreshScreen(
                onDismiss = {
                    showCacheRefreshModal = false
                }
            )
        }
    }
}