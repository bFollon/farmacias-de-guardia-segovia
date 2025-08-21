/*
 * Farmacias de Guardia - Segovia
 * Copyright (C) 2024 Bruno Follón
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.farmaciasdeGuardia.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.farmaciasdeGuardia.ui.screens.MainScreen
import com.farmaciasdeGuardia.ui.screens.ScheduleScreen
import com.farmaciasdeGuardia.ui.screens.ClosestPharmacyScreen
import com.farmaciasdeGuardia.ui.screens.SettingsScreen
import com.farmaciasdeGuardia.ui.screens.ZBSSelectionScreen
import com.farmaciasdeGuardia.ui.screens.ZBSScheduleScreen
import com.farmaciasdeGuardia.ui.screens.AboutScreen
import com.farmaciasdeGuardia.ui.screens.PDFViewScreen

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Schedule : Screen("schedule")
    object ClosestPharmacy : Screen("closest_pharmacy")
    object Settings : Screen("settings")
    object ZBSSelection : Screen("zbs_selection")
    object ZBSSchedule : Screen("zbs_schedule/{region}") {
        fun createRoute(region: String) = "zbs_schedule/$region"
    }
    object About : Screen("about")
    object PDFView : Screen("pdf_view/{url}") {
        fun createRoute(url: String) = "pdf_view/$url"
    }
}

@Composable
fun FarmaciasNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Main.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToSchedule = { navController.navigate(Screen.Schedule.route) },
                onNavigateToClosestPharmacy = { navController.navigate(Screen.ClosestPharmacy.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToZBSSelection = { navController.navigate(Screen.ZBSSelection.route) },
                onNavigateToAbout = { navController.navigate(Screen.About.route) }
            )
        }
        
        composable(Screen.Schedule.route) {
            ScheduleScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPDF = { url -> 
                    navController.navigate(Screen.PDFView.createRoute(url))
                }
            )
        }
        
        composable(Screen.ClosestPharmacy.route) {
            ClosestPharmacyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.ZBSSelection.route) {
            ZBSSelectionScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToZBSSchedule = { region ->
                    navController.navigate(Screen.ZBSSchedule.createRoute(region))
                }
            )
        }
        
        composable(Screen.ZBSSchedule.route) { backStackEntry ->
            val region = backStackEntry.arguments?.getString("region") ?: ""
            ZBSScheduleScreen(
                region = region,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.About.route) {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.PDFView.route) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: ""
            PDFViewScreen(
                url = url,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
