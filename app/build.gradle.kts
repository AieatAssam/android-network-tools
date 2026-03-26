plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ── CI-supplied properties ────────────────────────────────────────────────────
// Pass via: ./gradlew :app:assembleRelease -PversionName=1.2.3 -PversionCode=100
val ciVersionName: String? = findProperty("versionName") as String?
val ciVersionCode: Int?    = (findProperty("versionCode") as String?)?.toIntOrNull()

// Signing – set these secrets in GitHub Actions:
//   RELEASE_KEYSTORE_BASE64, RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD
val ciStoreFile:     String? = findProperty("storeFile")     as String?
val ciStorePassword: String? = findProperty("storePassword") as String?
val ciKeyAlias:      String? = findProperty("keyAlias")      as String?
val ciKeyPassword:   String? = findProperty("keyPassword")   as String?

android {
    namespace  = "com.example.netswissknife.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.netswissknife"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = ciVersionCode ?: 1
        versionName   = ciVersionName ?: "1.0.0"
    }

    signingConfigs {
        if (ciStoreFile != null) {
            create("release") {
                storeFile     = file(ciStoreFile)
                storePassword = ciStorePassword
                keyAlias      = ciKeyAlias
                keyPassword   = ciKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled    = true
            isShrinkResources  = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (ciStoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-network"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coroutines
    implementation(libs.coroutines.core)

    // ICMP traceroute (replaces binary-dependent implementation)
    implementation(libs.icmpenguin)

    // MapLibre Compose (CARTO Voyager tiles, no API key) for traceroute world map
    implementation(libs.maplibre.compose)
}
