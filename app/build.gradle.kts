import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services") apply false
}

val hasGoogleServicesJson = listOf(
    file("google-services.json"),
    file("src/google-services.json"),
    file("src/debug/google-services.json"),
    file("src/release/google-services.json")
).any { it.exists() }

if (hasGoogleServicesJson) {
    apply(plugin = "com.google.gms.google-services")
    println("sample-chat-app: google-services.json found, enabling Firebase Google Services plugin")
} else {
    println("sample-chat-app: google-services.json not found, building without Google Services plugin")
}

fun loadEnvFile(): Map<String, String> {
    val candidates = listOf(
        rootProject.file("chat-app/.env"),
        rootProject.file(".env"),
        project.file(".env"),
        file("${rootDir}/sample-chat-app/.env")
    )
    val envFile = candidates.firstOrNull { it.exists() && it.isFile } ?: return emptyMap()
    println("sample-chat-app: using env file ${envFile.absolutePath}")
    return envFile.readLines()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
        .associate { line ->
            val idx = line.indexOf("=")
            val key = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim().removeSurrounding("\"")
            key to value
        }
}

val fileEnv = loadEnvFile()

fun envOrDefault(vararg keys: String, default: String = ""): String {
    for (key in keys) {
        val fromSystem = System.getenv(key)?.takeIf { it.isNotBlank() }
        if (fromSystem != null) return fromSystem
        val fromFile = fileEnv[key]?.takeIf { it.isNotBlank() }
        if (fromFile != null) return fromFile
    }
    return default
}

android {
    namespace = "com.ethora.samplechatapp"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // Keep package aligned with IDE run target to avoid "Activity class does not exist" mismatch.
        applicationId = "com.ethora"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "ETHORA_APP_ID", "\"${envOrDefault("ETHORA_APP_ID", "APP_ID", default = "CHANGE_ME_APP_ID")}\"")
        buildConfigField("String", "ETHORA_APP_TOKEN", "\"${envOrDefault("ETHORA_APP_TOKEN", "APP_TOKEN", default = "")}\"")
        buildConfigField("String", "ETHORA_API_BASE_URL", "\"${envOrDefault("ETHORA_API_BASE_URL", "API_BASE_URL", default = "")}\"")
        buildConfigField("String", "ETHORA_USER_JWT", "\"${envOrDefault("ETHORA_USER_JWT", "USER_TOKEN", default = "")}\"")
        buildConfigField("String", "ETHORA_ROOM_JID", "\"${envOrDefault("ETHORA_ROOM_JID", "ROOM_JID", default = "")}\"")
        buildConfigField("String", "ETHORA_XMPP_SERVER_URL", "\"${envOrDefault("ETHORA_XMPP_SERVER_URL", "XMPP_SERVER_URL", default = "")}\"")
        buildConfigField("String", "ETHORA_XMPP_HOST", "\"${envOrDefault("ETHORA_XMPP_HOST", "XMPP_HOST", default = "")}\"")
        buildConfigField("String", "ETHORA_XMPP_CONFERENCE", "\"${envOrDefault("ETHORA_XMPP_CONFERENCE", "XMPP_CONFERENCE", default = "")}\"")
        buildConfigField(
            "String",
            "ETHORA_DNS_FALLBACK_OVERRIDES",
            "\"${envOrDefault("ETHORA_DNS_FALLBACK_OVERRIDES", "DNS_FALLBACK_OVERRIDES", default = "")}\""
        )
        println(
            "sample-chat-app BuildConfig: applicationId=com.ethora, " +
                "appId=${envOrDefault("ETHORA_APP_ID", "APP_ID", default = "CHANGE_ME_APP_ID")}, " +
                "apiBase=${envOrDefault("ETHORA_API_BASE_URL", "API_BASE_URL", default = "")}"
        )
    }

    // Match source test host: use custom debug keystore if it exists.
    signingConfigs {
        getByName("debug") {
            val debugKeystore = file("debug.keystore")
            if (debugKeystore.exists()) {
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            val debugKeystore = file("debug.keystore")
            if (debugKeystore.exists()) {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
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

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // Standard Android license/manifest hygiene + duplicate-metadata guards.
            // The OSGi MANIFEST.MF duplication comes from okhttp3:logging-interceptor
            // and org.jspecify:jspecify both shipping the same path — exclude it so
            // mergeDebugJavaResource does not fail. `pickFirsts` for notice/license
            // files covers similar duplicates from transitive XMPP/Smack libs.
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/versions/*/OSGI-INF/MANIFEST.MF",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )
            pickFirsts += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    // Published SDK artifact via JitPack (repo declared in settings.gradle.kts).
    // See https://github.com/dappros/ethora-sdk-android/releases for tags.
    //
    // NOTE: Use the single-artifact root coordinate, NOT the multi-module
    // form shown in the SDK README. The SDK's Gradle config has two
    // publications (root project + :ethora-component subproject) that
    // collide on 'com.github.dappros:ethora-sdk-android:<version>', and
    // JitPack only publishes the root one — the
    // 'com.github.dappros.ethora-sdk-android:ethora-component:<version>'
    // coordinate the README promises 404s. Tracked SDK-side; revisit
    // once the duplicate-publication warning in the build log is fixed.
    val localSdkProject = rootProject.findProject(":ethora-component")
    val localSdkAarCandidates = listOf(
        rootProject.file("../ethora-component/build/outputs/aar/ethora-component-debug.aar"),
        file("/tmp/android_build/ethora-chat-android/ethora-component/outputs/aar/ethora-component-debug.aar")
    )
    val localSdkAar = localSdkAarCandidates.firstOrNull { it.exists() }
    // SDK coordinate is resolved in this order:
    //   1) project(":ethora-component") if sample-app was included in a composite build
    //   2) locally-built AAR at a known path
    //   3) maven coordinate — default JitPack tag, override with
    //      `-Pethora.sdkVersion=local-SNAPSHOT` after `./gradlew publishToMavenLocal`
    //      from the SDK root (mavenLocal() is registered in settings.gradle.kts).
    val sdkVersion = providers.gradleProperty("ethora.sdkVersion").orElse("v1.0.21").get()
    if (localSdkProject != null) {
        implementation(localSdkProject)
        println("sample-chat-app: using local :ethora-component project dependency")
    } else if (localSdkAar != null) {
        implementation(files(localSdkAar))
        println("sample-chat-app: using local SDK AAR ${localSdkAar.absolutePath}")
    } else {
        println("sample-chat-app: using SDK coordinate com.github.dappros:ethora-sdk-android:$sdkVersion")
        implementation("com.github.dappros:ethora-sdk-android:$sdkVersion")
    }
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-common")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-installations")
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
