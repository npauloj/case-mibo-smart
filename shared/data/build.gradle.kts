import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.mokkery)
    // The device-list cache (ADR-006). Pinned in the catalog since wave 0; this is the module that
    // owns persistence, so it is the only one that applies it.
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("MiboSmartDatabase") {
            packageName = "io.github.npauloj.mibosmart.data.local.db"
        }
    }
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    androidLibrary {
        namespace = "io.github.npauloj.mibosmart.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTestBuilder {}
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.domain)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.koin.core)
            implementation(libs.sqldelight.runtime)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android.driver)
            // The partner's legacy Java SDK (ADR-007): a JVM library, so only the Android source set
            // can see it — and only this module may depend on it (`:konture-test` enforces that).
            implementation(projects.legacyCatalog)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.turbine)
        }
        // The cache round trip is proven against a real SQLite file on the JVM host: the Android and
        // iOS drivers need a device, the JDBC one does not, and the SQL is the same on all three.
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}
