plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.icondesigner.pack"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
}
