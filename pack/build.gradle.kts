// Turns rendered icons into a signed icon pack APK and installs it.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.iconoclast.pack"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
}

dependencies {
    implementation(libs.apksig)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlin.test.junit)
}

// The unit tests rewrite the real template and check it with zipalign from build-tools.
evaluationDependsOn(":template-pack")
val templateApk = project(":template-pack").tasks.named("buildTemplatePack")
    .flatMap { (it.property("outputApk") as RegularFileProperty) }
val zipalign = androidComponents.sdkComponents.sdkDirectory
    .map { it.file("build-tools/${libs.versions.buildTools.get()}/zipalign").asFile.path }
val aapt2 = androidComponents.sdkComponents.sdkDirectory
    .map { it.file("build-tools/${libs.versions.buildTools.get()}/aapt2").asFile.path }
tasks.withType<Test>().configureEach {
    inputs.file(templateApk)
    systemProperty("template.apk", templateApk.get().asFile.path)
    systemProperty("zipalign", zipalign.get())
    systemProperty("aapt2", aapt2.get())
    systemProperty("pack.base", providers.gradleProperty("iconoclast.packApplicationId").get())
}
