import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.mokkery)
    alias(libs.plugins.kover)
    alias(libs.plugins.roborazzi)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            export(projects.shared.domain)
        }
        // SQLDelight's native driver reaches this module through :shared:data, and its sqliter
        // cinterop leaves the `sqlite3_*` symbols to the final link. The framework inherits them from
        // the app that embeds it, but the *test* binary links on its own and has no such host, so
        // `linkDebugTestIosSimulatorArm64` failed with "Undefined symbols ... _sqlite3_bind_blob".
        // :shared:data links because its own iOS tests never reach the driver and Kotlin/Native drops
        // it; here the Koin wiring does reach it. `binaries.all` covers the test binary too.
        iosTarget.binaries.all { linkerOpts("-lsqlite3") }
    }

    androidLibrary {
        namespace = "io.github.npauloj.mibosmart.app"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTestBuilder {}.configure {
            // Robolectric needs the real resource table to inflate a theme; without it Compose
            // cannot resolve a single attribute and the capture dies before drawing.
            isIncludeAndroidResources = true
            // Compose's runtime logs through `android.util.Log` while composing, and on the JVM host
            // the unmocked stub throws — which is enough to abort any composition, even an empty one.
            // `LiveVideoScreenLifecycleTest` composes the screen's lifecycle wiring without a device,
            // so the stubs answer with defaults instead of throwing. Test-only, no production effect.
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.domain)
            implementation(projects.shared.data)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.material.icons.core)

            implementation(libs.navigation.compose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            // The live-video actual of `camera.platform` lives in this source set, and `:androidApp`
            // depends on this module rather than the other way round — declared there, Media3 would
            // be invisible to the code that uses it (ADR-005).
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }

        // Screenshot tests. JUnit4 and Robolectric live ONLY here, never in commonTest:
        // CLAUDE.md keeps commonTest on multiplatform libraries, and Robolectric is a JVM/Android
        // host runtime. Goldens are recorded and verified on the CI Linux runner, never committed
        // from a developer machine — font rendering differs and every image would churn.
        getByName("androidHostTest").dependencies {
            implementation(libs.junit4)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.previewScanner)
            implementation(libs.composablePreviewScanner)
            implementation(libs.compose.uiTestJUnit4)
        }
    }
}

// Screenshot tests are generated from the previews that already exist — the 49 `@Preview` functions
// under `app/**` are the state inventory, and writing a second list of them by hand would be a list
// that drifts. Goldens are recorded and compared on the CI Linux runner only.
roborazzi {
    generateComposePreviewRobolectricTests {
        enable = false
        packages = listOf("io.github.npauloj.mibosmart.app")
        // The previews are `internal`, beside the screens they describe (shared/CLAUDE.md).
        includePrivatePreviews = true
    }
}

tasks.withType<Test>().configureEach {
    // Roborazzi asks for this explicitly: without it the capture path is lower fidelity and images
    // differ between machines for reasons that have nothing to do with the UI.
    systemProperty("robolectric.pixelCopyRenderMode", "hardware")


    // Robolectric composes one screen at a time per fork, so the captures were serial. They are
    // parallel now because `ScreenshotTest` was split into one class per screen — Gradle hands
    // *classes* to forks, and forty-two captures in one class occupy exactly one fork whatever this
    // number says.
    //
    // The cap is the point, and it is **memory**, not cores. Each fork carries its own Robolectric
    // sandbox and Compose runtime on top of the Gradle daemon. `availableProcessors() / 2` picked 6
    // on a 12-core developer machine and the recording run was killed for memory pressure — the
    // second such kill this file has caused (ADR-024 records the first). The CI runner has 4 vCPU,
    // so it would have landed on 2 and never shown the problem.
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 2)
}
// Every user-facing string is a Compose resource (SPEC E6); the generated accessor is pinned to a
// package of ours so the import does not depend on how the plugin derives one.
compose.resources {
    publicResClass = false
    packageOfResClass = "io.github.npauloj.mibosmart.app.resources"
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
