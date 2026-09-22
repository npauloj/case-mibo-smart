import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The legacy partner catalogue (ADR-007): a JVM-only library that stands in for a partner SDK the
// app cannot rewrite. Java holds the SDK itself (`src/main/java`); Kotlin holds the one bridge the
// app calls (`src/main/kotlin`), which is also what the Java test calls back into — the reverse
// direction of the interop, and the reason this module carries both plugins (ADR-023).
plugins {
    `java-library`
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // `:shared:data`'s Android target compiles to JVM 11; a dependency above that would not link.
        jvmTarget = JvmTarget.JVM_11
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 11
    // The labels are Portuguese: javac would otherwise read them in the platform encoding, which on
    // a Windows machine is not the one the files are written in.
    options.encoding = "UTF-8"
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
