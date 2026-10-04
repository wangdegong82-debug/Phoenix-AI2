plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseKeystorePath = System.getenv("PHOENIX_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("PHOENIX_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("PHOENIX_KEY_ALIAS")
val releaseKeyPassword = System.getenv("PHOENIX_KEY_PASSWORD")
val releaseSigningEnabled =
    !releaseKeystorePath.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.phoenix.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.phoenix.ai"
        minSdk = 26
        targetSdk = 35
        versionCode = 210000
        versionName = "2.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    if (releaseSigningEnabled) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (releaseSigningEnabled) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
