plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.paparazzi")
}

// CI passes the run number so every build has a higher versionCode, which
// Android requires to install an update in place. Local builds use 1.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// Release signing key, provided by CI from repository secrets. Without it
// (local builds, forks) release builds fall back to the debug key.
val releaseKeystore = System.getenv("RELEASE_KEYSTORE_FILE")?.let(::file)?.takeIf { it.exists() }

android {
    namespace = "io.github.yinlongdai.runwatch"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.yinlongdai.runwatch"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.2.$buildNumber"
        // GitHub repo whose Releases the in-app updater checks.
        buildConfigField("String", "UPDATE_REPO", "\"YinlongDai/WANDB-Android\"")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                // CI passes an empty string when the optional RELEASE_KEY_PASSWORD secret isn't set;
                // PKCS12 keystores (keytool's default) use the store password for the key too.
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")?.takeIf { it.isNotEmpty() }
                    ?: System.getenv("RELEASE_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Every update must be signed with the same key, or Android refuses to install it over the old one.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
