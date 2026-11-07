plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing and version come from the environment (GitHub Actions, see
// .github/workflows/release.yml) or from ~/.gradle/gradle.properties for local builds:
//   ECOWI_KEYSTORE          / ecowi.keystore           path to the .jks file
//   ECOWI_KEYSTORE_PASSWORD / ecowi.keystore.password
//   ECOWI_KEY_ALIAS         / ecowi.key.alias
//   ECOWI_KEY_PASSWORD      / ecowi.key.password
//   ECOWI_VERSION_NAME, ECOWI_VERSION_CODE              set from the git tag on release
// Without a keystore the release build is signed with the debug key, so it still installs.
fun setting(env: String, property: String? = null): String? =
    (providers.environmentVariable(env).orNull ?: property?.let { providers.gradleProperty(it).orNull })
        ?.takeIf { it.isNotBlank() }

val releaseKeystore: File? = setting("ECOWI_KEYSTORE", "ecowi.keystore")?.let { file(it) }?.takeIf { it.isFile }

android {
    namespace = "ua.ecowi"
    compileSdk = 37

    defaultConfig {
        applicationId = "ua.ecowi"
        // Android 8+. Responsive widget sizes, wallpaper colors and the system widget radius
        // need Android 12+ and fall back gracefully below; Live Updates (Now Bar) need Android 16.
        minSdk = 26
        targetSdk = 36
        versionCode = setting("ECOWI_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = setting("ECOWI_VERSION_NAME") ?: "1.0"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = setting("ECOWI_KEYSTORE_PASSWORD", "ecowi.keystore.password")
                keyAlias = setting("ECOWI_KEY_ALIAS", "ecowi.key.alias")
                keyPassword = setting("ECOWI_KEY_PASSWORD", "ecowi.key.password")
            }
        }
    }

    buildTypes {
        release {
            // Kept simple on purpose. If you enable R8, proguard-rules.pro already keeps Paho.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
