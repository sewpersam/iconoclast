// Pure Kotlin JVM. No Android dependency.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The bundle formats are shared with build-logic, which writes them.
sourceSets.main {
    kotlin.srcDir("src/format/kotlin")
}

dependencies {
    testImplementation(libs.kotlin.test.junit)
}
