package com.github.bfollon.farmaciasdeguardiaensegovia

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.bfollon.farmaciasdeguardiaensegovia.services.DebugConfig
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.theme.FarmaciasDeGuardiaEnSegoviaTheme
import com.github.bfollon.farmaciasdeguardiaensegovia.viewmodels.SplashViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class RegionIcon(
    val emoji: String,
    val name: String,
    val isCompleted: Boolean = false
)

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val splashViewModel: SplashViewModel = viewModel { SplashViewModel(context) }

    // Observe loading states from ViewModel
    val regionLoadingStates by splashViewModel.regionLoadingStates.collectAsState()
    val loadingProgress by splashViewModel.loadingProgress.collectAsState()
    val isLoading by splashViewModel.isLoading.collectAsState()
    val currentLoadingRegion by splashViewModel.currentLoadingRegion.collectAsState()

    // Animation states
    var logoVisible by remember { mutableStateOf(false) }
    var textVisible by remember { mutableStateOf(false) }
    var progressVisible by remember { mutableStateOf(false) }
    var regionsVisible by remember { mutableStateOf(false) }

    // Region icons with emojis matching iOS - updated based on actual loading states
    val regions by remember {
        derivedStateOf {
            listOf(
                RegionIcon("🏙", "Segovia Capital", regionLoadingStates["Segovia Capital"] ?: false),
                RegionIcon("🌳", "Cuéllar", regionLoadingStates["Cuéllar"] ?: false),
                RegionIcon("⛰", "El Espinar", regionLoadingStates["El Espinar"] ?: false),
                RegionIcon("🚜", "Segovia Rural", regionLoadingStates["Segovia Rural"] ?: false)
            )
        }
    }

    // Progress animation - combines manual animation with actual loading progress
    val progress = remember { Animatable(0f) }

    // Update progress based on actual loading progress
    LaunchedEffect(loadingProgress) {
        // Animate smoothly to the actual loading progress
        progress.animateTo(
            loadingProgress,
            animationSpec = tween(300, easing = EaseOut)
        )
    }

    // Logo animations - matching iOS easeOut 0.8s
    val logoScale by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0.7f,
        animationSpec = tween(800, easing = EaseOut),
        label = "logoScale"
    )

    val logoAlpha by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0f,
        animationSpec = tween(800, easing = EaseOut),
        label = "logoAlpha"
    )

    // Text fade animation
    val textAlpha by animateFloatAsState(
        targetValue = if (textVisible) 1f else 0f,
        animationSpec = tween(600, easing = EaseOut),
        label = "textAlpha"
    )

    // Progress indicator animation
    val progressAlpha by animateFloatAsState(
        targetValue = if (progressVisible) 1f else 0f,
        animationSpec = tween(400, easing = EaseOut),
        label = "progressAlpha"
    )

    // Regions animation with staggered timing
    val regionsAlpha by animateFloatAsState(
        targetValue = if (regionsVisible) 1f else 0f,
        animationSpec = tween(500, easing = EaseOut),
        label = "regionsAlpha"
    )

    // Gradient colors matching iOS blue to green
    val gradientColors = listOf(
        Color(0xFF007AFF), // iOS blue
        Color(0xFF34C759)  // iOS green
    )

    // Background gradient - subtle like iOS
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            Color(0xFF007AFF).copy(alpha = 0.05f)
        )
    )

    // Launch animations with iOS-matching timing
    LaunchedEffect(Unit) {
        // Logo appears immediately (UI not blocked by loading)
        logoVisible = true

        // Start PDF loading in truly background coroutine after a tiny delay to ensure UI is rendered
        launch {
            delay(50) // Small delay to ensure splash UI is rendered first
            splashViewModel.startBackgroundLoading()
        }

        // Text appears after 0.5s (staggered)
        delay(500)
        textVisible = true

        // Progress appears after 0.6s
        delay(100)
        progressVisible = true

        // Regions appear after 0.7s (while progress is running)
        delay(100)
        regionsVisible = true

        // No manual region animation - they are now tied to actual loading states
        // Icons will animate automatically as regionLoadingStates changes

        // Wait for sequential loading to complete
        val startTime = System.currentTimeMillis()
        val minSplashTime = 3000L // Minimum 3 seconds for good UX
        val maxWaitTime = 15000L // Maximum 15 seconds to prevent hanging

        // Wait for actual loading completion, respecting min/max times
        while (System.currentTimeMillis() - startTime < maxWaitTime) {
            delay(100)

            val elapsedTime = System.currentTimeMillis() - startTime

            // Check if Segovia Capital is actually loaded (the real indicator)
            val segoviaCapitalCompleted = regionLoadingStates["Segovia Capital"] ?: false

            // If Segovia Capital is completed and we've waited minimum time, we can proceed
            if (segoviaCapitalCompleted && elapsedTime >= minSplashTime) {
                DebugConfig.debugPrint("SplashScreen: Segovia Capital loaded, proceeding after ${elapsedTime}ms")
                break
            }

            // Also check traditional isLoading state as backup (but with higher minimum time)
            if (!isLoading && elapsedTime >= minSplashTime) {
                DebugConfig.debugPrint("SplashScreen: isLoading=false, proceeding after ${elapsedTime}ms")
                break
            }
        }

        // If we hit max wait time, log it but proceed anyway
        if (System.currentTimeMillis() - startTime >= maxWaitTime) {
            DebugConfig.debugWarn("SplashScreen: Maximum wait time reached, proceeding anyway")
        }

        // Final progress to 100% if not already there (quick animation)
        if (progress.value < 1f) {
            progress.animateTo(1f, animationSpec = tween(200, easing = EaseOut))
        }

        // Brief hold then navigate immediately
        delay(200)
        onSplashFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Logo with scale and fade animations
            Image(
                painter = painterResource(id = R.drawable.splash_logo),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(200.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha)
                    .padding(bottom = 40.dp),
                contentScale = ContentScale.Fit
            )

            // App title with gradient text effect
            Text(
                text = "Farmacias de Guardia\nSegovia",
                style = TextStyle(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 38.sp,
                    brush = Brush.linearGradient(gradientColors)
                ),
                modifier = Modifier
                    .alpha(textAlpha)
                    .padding(bottom = 48.dp)
            )

            // Progress indicator
            Box(
                modifier = Modifier
                    .alpha(progressAlpha)
                    .padding(bottom = 32.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier
                        .width(200.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF007AFF),
                    trackColor = Color(0xFF007AFF).copy(alpha = 0.2f)
                )
            }

            // Region icons with animated emoji progression
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.alpha(regionsAlpha)
            ) {
                regions.forEach { region ->
                    RegionIconView(
                        region = region,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RegionIconView(
    region: RegionIcon,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (region.isCompleted) {
        Color(0xFF34C759).copy(alpha = 0.2f) // Green tint when completed
    } else {
        Color.Gray.copy(alpha = 0.1f)
    }

    val scale by animateFloatAsState(
        targetValue = if (region.isCompleted) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "regionScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .background(backgroundColor, CircleShape)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = region.emoji,
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    FarmaciasDeGuardiaEnSegoviaTheme {
        SplashScreen(onSplashFinished = {})
    }
}
