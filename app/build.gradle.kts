plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ua.ecowi"
    compileSdk = 37

    defaultConfig {
        applicationId = "ua.ecowi"
        // Android 8+. Responsive widget sizes, wallpaper colors and the system widget radius
        // need Android 12+ and fall back gracefully below; Live Updates (Now Bar) need Android 16.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Personal app: keep it simple. If you enable R8, proguard-rules.pro already keeps Paho.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // English is the base language (res/values), Ukrainian is a translation (res/values-uk).
        // Strip other languages pulled in by libraries and expose the per-app language
        // picker on Android 13+ (locale config is generated from res/resources.properties).
        localeFilters += setOf("en", "uk")
        generateLocaleConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.paho.mqttv3)

    testImplementation(libs.junit)
}
