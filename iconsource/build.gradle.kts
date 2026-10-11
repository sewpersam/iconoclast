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

// DrawableSearchTest ranks searches over every converted drawable name at the pin;
// CalendarFallbackTest resolves through the compiled appfilter.
tasks.withType<Test>().configureEach {
    val convert = tasks.named<ConvertIconsTask>("convertArcticonsIcons")
    inputs.dir(convert.flatMap { it.outputDir })
    systemProperty("arcticons.names", convert.get().outputDir.get().asFile.resolve("arcticons/names.txt").path)
    val appfilter = tasks.named<CompileAppfilterTask>("compileArcticonsAppfilter")
    inputs.dir(appfilter.flatMap { it.outputDir })
    systemProperty("arcticons.components", appfilter.get().outputDir.get().asFile.resolve("arcticons/components.bin").path)
}

dependencies {
    api(project(":render"))
    testImplementation(libs.kotlin.test.junit)
}
