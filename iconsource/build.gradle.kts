import dev.iconoclast.buildlogic.arcticons.CompileAppfilterTask
import dev.iconoclast.buildlogic.arcticons.ConvertIconsTask

plugins {
    alias(libs.plugins.android.library)
    id("iconoclast.arcticons")
}

android {
    namespace = "dev.iconoclast.iconsource"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
}

// The converted bundle and compiled appfilter ship as assets under arcticons/.
androidComponents {
    onVariants { variant ->
        val assets = variant.sources.assets ?: return@onVariants
        assets.addGeneratedSourceDirectory(tasks.named<ConvertIconsTask>("convertArcticonsIcons"), ConvertIconsTask::outputDir)
        assets.addGeneratedSourceDirectory(tasks.named<CompileAppfilterTask>("compileArcticonsAppfilter"), CompileAppfilterTask::outputDir)
    }
}

dependencies {
    api(project(":render"))
    testImplementation(libs.kotlin.test.junit)
}
