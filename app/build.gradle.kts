// Standalone Wear OS app (PLAN.md 2절) — Kotlin + Jetpack Compose for Wear
// OS, no phone companion module. Depends on :core for the puzzle engine
// and Lichess API client, which stay Android-free for plain-JVM testing.
plugins {
    alias(libs.plugins.android.application)
    // As of AGP 9.0, Kotlin/Android support is built into the Android
    // Gradle Plugin itself — the separate org.jetbrains.kotlin.android
    // plugin is not just unnecessary but a hard error to apply alongside it
    // (confirmed by an actual build attempt, not assumed from memory).
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.closeonjae.chesspuzzle"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.closeonjae.chesspuzzle"
        // Wear OS 3+ (RemoteAuthClient, Wear Compose Material3) — RESEARCH.md 2절.
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Kotlin toolchain (jvmTarget etc.) is configured via the built-in Kotlin
    // support in AGP 9 — compileOptions above covers it, no separate
    // kotlinOptions {} block (that DSL no longer exists as of AGP 9).

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.wear.input)
    implementation(libs.wear.phone.interactions)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
}
