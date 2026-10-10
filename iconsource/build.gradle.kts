plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.icondesigner.iconsource"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
}

dependencies {
    api(project(":render"))
}
