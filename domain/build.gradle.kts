// domain/build.gradle.kts

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Domain depends only on core. No Android, no MediaPipe, no Hilt.
// All repository interfaces are defined here as Kotlin interfaces.

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:poseengine"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.javax.inject)

    testImplementation(libs.test.junit)
    testImplementation(libs.test.kotlin)
    testImplementation(libs.test.coroutines)
    testImplementation(libs.test.mockk)
}