// Architecture tests (ADR-009). A plain JVM module that depends on no production module: the Konture
// Gradle plugin (applied in settings.gradle.kts) hands it the real build topology, and the library
// parses the Kotlin sources of every module with PSI. Runs first in CI — cheapest gate, fails fastest.
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(libs.konture)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // The rules read every module's Kotlin sources at test time (PSI), but Gradle only knows this
    // module's own inputs — without this, a change in shared/ leaves `test` UP-TO-DATE and a
    // violation slips through locally (found in the wave-0 spike; CI always starts clean).
    inputs.files(
        rootProject.fileTree(".") {
            include("shared/**/src/**/*.kt", "androidApp/src/**/*.kt", "**/build.gradle.kts", "settings.gradle.kts")
            exclude("**/build/**", "konture-test/**")
        },
    ).withPathSensitivity(PathSensitivity.RELATIVE)
    // Every rule prints the module/file/line of a violation; keep that visible in CI logs.
    testLogging {
        events("failed", "skipped")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
