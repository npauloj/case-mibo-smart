import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// The partner host is a local setting, never a versioned one (ADR-008): it comes from
// `local.properties` (see local.properties.example) and reaches the app through BuildConfig. CI and a
// fresh clone have no such file and fall back to a fictitious host — tests never reach the network.
val localProperties: Properties? =
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.orNull
        ?.let { contents -> Properties().apply { load(contents.reader()) } }

val smartHomeApiHost: String =
    localProperties?.getProperty("smarthome.apiHost") ?: "https://api.example.invalid"

// The streaming host. It is a *different* address from the api host, not a synonym: the four
// `streaming/*` calls only exist there, and `criar-fluxo-video` answers on both with different
// shapes (measured 2026-09-23 — see `SmartHomeApi.streamingBaseUrl`).
val smartHomePortalHost: String =
    localProperties?.getProperty("smarthome.portalHost") ?: "https://portal.example.invalid"

// The live-video kill switch (SPEC V1, ADR-006). Default on; set `smarthome.liveVideoEnabled=false`
// to run the app on the shared account without ever opening a streaming session.
val liveVideoEnabled: String =
    localProperties?.getProperty("smarthome.liveVideoEnabled") ?: "true"

// The lock-writes kill switch (SPEC L2, L7). Default on since ADR-028: the lock commands are the
// feature under evaluation. Set `smarthome.lockWritesEnabled=false` to build an app that never writes
// to a physical door on the shared account.
val lockWritesEnabled: String =
    localProperties?.getProperty("smarthome.lockWritesEnabled") ?: "true"

dependencies {
    implementation(projects.shared.app)
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
}

android {
    namespace = "io.github.npauloj.mibosmart"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.npauloj.mibosmart"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SMARTHOME_API_HOST", "\"$smartHomeApiHost\"")
        buildConfigField("String", "SMARTHOME_PORTAL_HOST", "\"$smartHomePortalHost\"")
        buildConfigField("boolean", "SMARTHOME_LIVE_VIDEO_ENABLED", liveVideoEnabled)
        buildConfigField("boolean", "SMARTHOME_LOCK_WRITES_ENABLED", lockWritesEnabled)
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
