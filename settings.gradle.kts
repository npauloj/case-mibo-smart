rootProject.name = "case-mibo-smart"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    // Architecture tests (ADR-009): applied at the settings level so the plugin sees every module and
    // hands the real build topology to :konture-test. Version pinned in gradle/libs.versions.toml.
    id("io.github.baole.konture") version "0.8.4"
}

include(":shared:domain")
include(":shared:data")
include(":shared:app")
include(":androidApp")
include(":konture-test")
// The legacy partner SDK, JVM only: consumed by `:shared:data`'s Android source set alone (ADR-007).
include(":legacy-catalog")
