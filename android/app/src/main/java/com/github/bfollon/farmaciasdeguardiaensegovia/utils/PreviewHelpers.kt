package com.github.bfollon.farmaciasdeguardiaensegovia.utils

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
object PreviewSizeClasses {
    val Compact = WindowSizeClass.calculateFromSize(DpSize(400.dp, 800.dp))
    val Medium = WindowSizeClass.calculateFromSize(DpSize(700.dp, 800.dp))
    val Expanded = WindowSizeClass.calculateFromSize(DpSize(1000.dp, 800.dp))
}