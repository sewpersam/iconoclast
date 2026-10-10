// The icon pack APK template. The designer rewrites its icons and re-signs it.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.icondesigner.templatepack"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        applicationId = providers.gradleProperty("icondesigner.packApplicationId").get()
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1"
    }
}

// The pack has no Kotlin code. Keep the stdlib that AGP adds out of the APK.
configurations.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
}
