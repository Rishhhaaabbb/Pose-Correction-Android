// Top-level build file — no dependencies declared here.
// All version declarations live in gradle/libs.versions.toml.

buildscript {
    dependencies {
        classpath("com.google.gms:google-services:4.4.0")
    }
}

plugins {
    alias(libs.plugins.android.application)     apply false
    alias(libs.plugins.android.library)         apply false
    alias(libs.plugins.kotlin.android)          apply false
    alias(libs.plugins.kotlin.jvm)              apply false
    alias(libs.plugins.kotlin.serialization)    apply false
    alias(libs.plugins.kotlin.compose)          apply false
    alias(libs.plugins.hilt)                    apply false
    alias(libs.plugins.ksp)                     apply false
}