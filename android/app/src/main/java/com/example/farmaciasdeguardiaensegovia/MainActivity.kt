package com.example.farmaciasdeguardiaensegovia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.farmaciasdeguardiaensegovia.data.Region
import com.example.farmaciasdeguardiaensegovia.ui.screens.MainScreen
import com.example.farmaciasdeguardiaensegovia.ui.screens.ScheduleScreen
import com.example.farmaciasdeguardiaensegovia.ui.screens.ZBSSelectionScreen
import com.example.farmaciasdeguardiaensegovia.ui.theme.FarmaciasDeGuardiaEnSegoviaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FarmaciasDeGuardiaEnSegoviaTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") {
            SplashScreen(
                onSplashFinished = {
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
                        Region.segoviaCapital -> {
                            navController.navigate("schedule/${region.id}")
                        }
                        else -> {
                            // TODO: Implement other regions
                        }
                    }
                },
                onZBSSelectionRequested = {
                    navController.navigate("zbs_selection")
                },
                onSettingsClick = {
                    // TODO: Navigate to settings
                },
                onAboutClick = {
                    // TODO: Navigate to about
                }
            )
        }
        
        composable("schedule/{regionId}") { backStackEntry ->
            val regionId = backStackEntry.arguments?.getString("regionId")
            if (regionId == "segovia-capital") {
                ScheduleScreen(
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
        
        composable("zbs_selection") {
            ZBSSelectionScreen(
                onZBSSelected = { zbs ->
                    // TODO: Navigate to ZBS schedule view
                    navController.popBackStack()
                },
                onDismiss = {
                    navController.popBackStack()
                }
            )
        }
    }
}