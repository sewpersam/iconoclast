// Pure Kotlin JVM. No Android dependency.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(libs.kotlin.test.junit)
}
