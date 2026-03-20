plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ethora.sample"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ethora.sample"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        vectorDrawables {
            useSupportLibrary = true
        }

        // ── Ethora SDK credentials ─────────────────────────────────────
        // Run `npx @ethora/setup` to fill these automatically,
        // or replace the CHANGE_ME values manually.
        buildConfigField("String", "ETHORA_APP_ID", "\"CHANGE_ME\"")
        buildConfigField("String", "ETHORA_APP_TOKEN", "\"CHANGE_ME\"")
        buildConfigField("String", "ETHORA_API_BASE_URL", "\"CHANGE_ME\"")
        buildConfigField("String", "ETHORA_XMPP_SERVER_URL", "\"CHANGE_ME\"")
        buildConfigField("String", "ETHORA_XMPP_HOST", "\"CHANGE_ME\"")
        buildConfigField("String", "ETHORA_XMPP_CONFERENCE", "\"CHANGE_ME\"")
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // Ethora Chat SDK
    implementation("com.github.dappros:ethora-sdk-android:v1.0.19")

    // AndroidX + Compose
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")

    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
