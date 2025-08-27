package com.example.farmaciasdeguardiaensegovia

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
import com.example.farmaciasdeguardiaensegovia.ui.theme.FarmaciasDeGuardiaEnSegoviaTheme
import com.example.farmaciasdeguardiaensegovia.viewmodels.SplashViewModel
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
    val segoviaCapitalLoaded by splashViewModel.segoviaCapitalLoaded.collectAsState()
    val loadingProgress by splashViewModel.loadingProgress.collectAsState()
    val isLoading by splashViewModel.isLoading.collectAsState()
    
    // Animation states
    var logoVisible by remember { mutableStateOf(false) }
    var textVisible by remember { mutableStateOf(false) }
    var progressVisible by remember { mutableStateOf(false) }
    var regionsVisible by remember { mutableStateOf(false) }
    
    // Region icons with emojis matching iOS
    var regions by remember {
        mutableStateOf(listOf(
            RegionIcon("🏙", "Segovia Capital", false),
            RegionIcon("🌳", "Cuéllar", false),  
            RegionIcon("⛰", "El Espinar", false),
            RegionIcon("🚜", "Segovia Rural", false)
        ))
    }
    
    // Update regions when Segovia Capital is loaded
    LaunchedEffect(segoviaCapitalLoaded) {
        if (segoviaCapitalLoaded) {
            regions = regions.map { region ->
                if (region.name == "Segovia Capital") {
                    region.copy(isCompleted = true)
                } else region
            }
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
        // Start PDF loading immediately in background
        splashViewModel.startBackgroundLoading()
        
        // Logo appears immediately
        logoVisible = true
        
        // Text appears after 0.5s (staggered)
        delay(500)
        textVisible = true
        
        // Progress appears after 0.6s
        delay(100)
        progressVisible = true
        
        // Regions appear after 0.7s (while progress is running)
        delay(100)
        regionsVisible = true
        
        // Simulate other region completion animations for visual effect
        delay(800) // Wait a bit for Segovia Capital to load
        
        // Animate remaining regions (visual only - not tied to actual loading)
        val remainingRegions = listOf("Cuéllar", "El Espinar", "Segovia Rural")
        remainingRegions.forEach { regionName ->
            delay(400) // Stagger the animations
            if (!segoviaCapitalLoaded || regionName != "Segovia Capital") {
                regions = regions.map { region ->
                    if (region.name == regionName) region.copy(isCompleted = true) else region
                }
            }
        }
        
        // Wait for loading to complete (minimum 2 seconds, or until loading is done)
        val minSplashTime = 2000L
        val startTime = System.currentTimeMillis()
        
        while (isLoading && (System.currentTimeMillis() - startTime) < minSplashTime) {
            delay(100)
        }
        
        // Ensure minimum splash time even if loading finishes quickly
        val elapsedTime = System.currentTimeMillis() - startTime
        if (elapsedTime < minSplashTime) {
            delay(minSplashTime - elapsedTime)
        }
        
        // Final progress to 100% if not already there
        if (progress.value < 1f) {
            progress.animateTo(1f, animationSpec = tween(300, easing = EaseOut))
        }
        
        // Hold final state briefly then navigate
        delay(500)
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
