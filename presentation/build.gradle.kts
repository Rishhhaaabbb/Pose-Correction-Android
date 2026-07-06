// presentation/build.gradle.kts

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)       // Required for Compose with Kotlin 2.0+
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.posemonitor.presentation"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
    }

    // With Kotlin 2.0+ and the kotlin.plugin.compose plugin,
    // composeOptions.kotlinCompilerExtensionVersion is NOT required.
    // The BOM manages Compose Compiler alignment automatically.
}

dependencies {
    implementation(project(":core:poseengine"))
    implementation(project(":domain"))

    // Compose BOM — pins all compose.* artifact versions consistently
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt navigation runtime only (no Hilt annotations in this module)
    implementation(libs.hilt.navigation.compose)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)

    // CameraX preview surface (presentation needs the view, data owns the use cases)
    implementation(libs.camera.view)

    // KotlinX
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.test.junit)
    androidTestImplementation(libs.test.junit.ext)
    androidTestImplementation(libs.test.espresso)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.test.junit)
    testImplementation(libs.test.kotlin)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui)  // needed for androidx.compose.ui.geometry.Offset
}