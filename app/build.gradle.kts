// app/build.gradle.kts

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

val localProperties = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { load(it) }
    }
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        envFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.posemonitor2"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.iisc.posecorrection"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        noCompress += "mp4"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            isDebuggable = true
            buildConfigField(
                "String",
                "GOOGLE_WEB_CLIENT_ID",
                "\"${localProperties.getProperty("GOOGLE_WEB_CLIENT_ID_DEBUG", "")}\""
            )
            buildConfigField(
                "String",
                "GROQ_API_KEY",
                "\"${localProperties.getProperty("GROQ_API_KEY", "")}\""
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField(
                "String",
                "GOOGLE_WEB_CLIENT_ID",
                "\"${localProperties.getProperty("GOOGLE_WEB_CLIENT_ID_RELEASE", "")}\""
            )
            buildConfigField(
                "String",
                "GROQ_API_KEY",
                "\"${localProperties.getProperty("GROQ_API_KEY", "")}\""
            )
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE*.md"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:poseengine"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":presentation"))

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.datastore.preferences)

    // Compose BOM + UI  ← correct aliases from your toml
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // Testing
    testImplementation(libs.test.junit)
    androidTestImplementation(libs.test.junit.ext)
    androidTestImplementation(libs.test.espresso)
    testImplementation(libs.test.kotlin)
    testImplementation(project(":core:poseengine"))
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-process:2.9.0")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.play.services.auth)
    // Firebase
    implementation(platform(libs.compose.bom)) // BOM already added above, but good practice
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Material components (used by DayNight theme)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.material)
    
    // Media3
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // Ktor (for GroqFeedbackProvider AI coaching)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
}
