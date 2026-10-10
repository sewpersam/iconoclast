plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.icondesigner.app"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        applicationId = providers.gradleProperty("icondesigner.applicationId").get()
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1"
        val packPackage = providers.gradleProperty("icondesigner.packApplicationId").get()
        buildConfigField("String", "PACK_PACKAGE", "\"$packPackage\"")
        manifestPlaceholders["packPackage"] = packPackage
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // The ART byte identity check renders the :render goldens on the device.
    sourceSets["androidTest"].assets.directories += rootProject.file("render/src/test/resources/golden").path
    sourceSets["androidTest"].kotlin.directories += rootProject.file("render/src/golden/kotlin").path
    sourceSets["test"].kotlin.directories += rootProject.file("render/src/golden/kotlin").path
}

// The pack template, built by :template-pack with aapt2, ships in the assets under pack/.
evaluationDependsOn(":template-pack")
val builtTemplate = project(":template-pack").tasks.named("buildTemplatePack")
    .flatMap { (it.property("outputApk") as RegularFileProperty) }
/** Places the template at pack/template.apk under [outputDir], which AGP adds to the assets. */
abstract class TemplatePackAssets : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val templateApk: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val dir = outputDir.get().asFile.apply { deleteRecursively() }.resolve("pack").apply { mkdirs() }
        templateApk.get().asFile.copyTo(dir.resolve("template.apk"))
    }
}
val templateAssets = tasks.register<TemplatePackAssets>("templatePackAssets") {
    templateApk = builtTemplate
}
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(templateAssets, TemplatePackAssets::outputDir)
    }
}

dependencies {
    implementation(project(":render"))
    implementation(project(":iconsource"))
    implementation(project(":pack"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}

// PackBuilderTest rewrites the real template with icons from the golden sample bundle.
tasks.withType<Test>().configureEach {
    inputs.file(builtTemplate)
    systemProperty("template.apk", builtTemplate.get().asFile.path)
    systemProperty("golden.samples", rootProject.file("render/src/test/resources/golden/samples.bin").path)
}
