plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.shiftalarm.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shiftalarm.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 23
        versionName = "1.16.0"
    }

    signingConfigs {
        create("release") {
            // Keystore + passwords are provided via -P properties (CI uses
            // GitHub Secrets). No defaults: a release build fails loudly if
            // the signing material is missing instead of silently using a
            // hardcoded password.
            storeFile = file("release.keystore")
            storePassword = findProperty("RELEASE_STORE_PASSWORD") as String?
            keyAlias = "shiftalarm"
            keyPassword = findProperty("RELEASE_KEY_PASSWORD") as String?
        }
    }

    buildTypes {
        debug {
            // Use default debug signing for local development
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    // AdMob banner ads (test ids by default; real ids configurable in Settings)
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    // One-time purchase to remove ads (works once the app ships on Google Play)
    implementation("com.android.billingclient:billing-ktx:7.1.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
}