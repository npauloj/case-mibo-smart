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

// The live-video kill switch (SPEC V1, ADR-006). Default on; set `smarthome.liveVideoEnabled=false`
// to run the app on the shared account without ever opening a streaming session.
val liveVideoEnabled: String =
    localProperties?.getProperty("smarthome.liveVideoEnabled") ?: "true"

// The lock-writes kill switch (SPEC L2, L7). Default **off**, unlike the video one: `mudar-volume`
// and `habilitar-abrir-remoto` change a physical door on a shared account, so writing to one is
// opted into with `smarthome.lockWritesEnabled=true`, never inherited from a default.
val lockWritesEnabled: String =
    localProperties?.getProperty("smarthome.lockWritesEnabled") ?: "false"

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
