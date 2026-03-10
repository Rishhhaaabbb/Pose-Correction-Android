// data/build.gradle.kts

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.posemonitor.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        // MediaPipe native libs require ABI filters.
        // arm64-v8a covers all modern Android devices.
        // x86_64 covers Android emulators on Apple Silicon / Intel hosts.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Required: MediaPipe .task model files must not be compressed in the APK.
    androidResources {
        noCompress += listOf("task", "tflite")
    }
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(project(":core:poseengine"))
    implementation(project(":domain"))

    // MediaPipe Tasks Vision (includes PoseLandmarker)
    implementation(libs.mediapipe.tasks.vision)

    // Hilt — KSP processor (NOT kapt; kapt is incompatible with Kotlin 2.0)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // CameraX
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // KotlinX
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":core:poseengine"))
    // Core AndroidX
    implementation(libs.androidx.core.ktx)
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    // Testing
    testImplementation(libs.test.junit)
    testImplementation(libs.test.kotlin)
    testImplementation(libs.test.coroutines)
    testImplementation(libs.test.mockk)
    implementation(libs.kotlinx.datetime)
}