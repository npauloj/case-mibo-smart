plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
    alias(libs.plugins.mokkery) apply false
    alias(libs.plugins.sqldelight) apply false
    // Coverage is measured, never gated (ADR-006): the root aggregates the modules that hold logic.
    alias(libs.plugins.kover)
}

dependencies {
    kover(projects.shared.domain)
    kover(projects.shared.app)
}

kover {
    reports {
        filters {
            excludes {
                // Platform bridges and Compose UI are proven by previews / manual checks, not by unit tests.
                packages("*.platform", "*.platform.*", "*.ui.*")
                classes("*ComposableSingletons*", "*Preview*", "*Screen", "*ScreenContent*", "*Kt\$*")
                annotatedBy("androidx.compose.runtime.Composable")
            }
        }
        total {
            xml { onCheck = false }
            html { onCheck = false }
        }
    }
}
