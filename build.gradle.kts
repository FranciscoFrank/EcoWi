buildscript {
    dependencies {
        // AGP 9 compiles Kotlin itself; pin KGP so it matches the Compose compiler plugin version.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
