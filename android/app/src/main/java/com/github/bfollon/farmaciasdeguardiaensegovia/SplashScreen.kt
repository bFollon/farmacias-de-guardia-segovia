package com.github.bfollon.farmaciasdeguardiaensegovia

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.bfollon.farmaciasdeguardiaensegovia.services.DebugConfig
import com.github.bfollon.farmaciasdeguardiaensegovia.ui.components.OfflineWarningCard
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
    val isOffline by splashViewModel.isOffline.collectAsState()

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

    // Launch animations with iOS-matching timing
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        val minSplashTime = 3000L // Minimum 3 seconds for good UX

        // Start PDF loading in background immediately
        launch {
            delay(50) // Small delay to ensure splash UI is rendered first
            splashViewModel.startBackgroundLoading()
        }

        // Staggered UI animations for polished look
        logoVisible = true
        delay(500)
        textVisible = true
        delay(100)
        progressVisible = true
        delay(100)
        regionsVisible = true

        // Wait for loading to complete (suspends here until done or timeout)
        splashViewModel.awaitLoadingCompletion()
        DebugConfig.debugPrint("SplashScreen: Loading complete")

        // Ensure minimum splash time for branding/UX
        val elapsed = System.currentTimeMillis() - startTime
        val remaining = maxOf(0, minSplashTime - elapsed)
        if (remaining > 0) {
            DebugConfig.debugPrint("SplashScreen: Waiting ${remaining}ms to meet minimum splash time")
            delay(remaining)
        }

        // Final progress animation to 100%
        if (progress.value < 1f) {
            progress.animateTo(1f, animationSpec = tween(200, easing = EaseOut))
        }

        // Brief hold for smooth transition
        delay(200)

        // Navigate to main screen
        DebugConfig.debugPrint("SplashScreen: Navigating to main screen")
        onSplashFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background),
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
                style = MaterialTheme.typography.headlineMedium.copy(
                    brush = Brush.linearGradient(gradientColors)
                ),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 38.sp,
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
                modifier = Modifier
                    .alpha(regionsAlpha)
                    .padding(bottom = if (isOffline) 16.dp else 0.dp)
            ) {
                regions.forEach { region ->
                    RegionIconView(
                        region = region,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            
            // Offline warning card (appears below icons when offline)
            if (isOffline) {
                OfflineWarningCard(
                    modifier = Modifier
                        .alpha(regionsAlpha)
                        .padding(horizontal = 32.dp),
                    isClickable = false
                )
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
            style = MaterialTheme.typography.bodyMedium,
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
