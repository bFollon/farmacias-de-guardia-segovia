plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
}

android {
    namespace = "com.github.bfollon.farmaciasdeguardiaensegovia"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.bfollon.farmaciasdeguardiaensegovia"
        minSdk = 26
        targetSdk = 36
        versionCode = 26
        versionName = "1.1.12"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Enables code-related app optimization.
            isMinifyEnabled = true

            // Enables resource shrinking.
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.windowsSizeClass)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Material3 for XML theme support (edge-to-edge compliance)
    implementation("com.google.android.material:material:1.12.0")
    
    // ViewModel support
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
        // PDF Processing
    implementation(libs.itext.kernel)
    
    // HTTP client for PDF downloads and web scraping
    implementation(libs.okhttp)
    
    // Date picker
    implementation(libs.compose.material.dialogs.datetime)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // Serialization for data models
    implementation(libs.kotlinx.serialization.json)
    
    // Location services
    implementation(libs.play.services.location)

    // Force modern androidx.fragment version (replaces ancient 1.0.0 from Play Services)
    implementation(libs.androidx.fragment.ktx)

    // Window Size Classes for responsive layouts
    implementation(libs.androidx.compose.adaptive)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}