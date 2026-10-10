// Pure Kotlin JVM. No Android dependency.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The bundle formats are shared with build-logic, which writes them.
sourceSets.main {
    kotlin.srcDir("src/format/kotlin")
}

// JVM-only conversion check: java.awt rasterizer plus inkscape references. Not shipped.
val verify = sourceSets.create("verify") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}

dependencies {
    testImplementation(libs.kotlin.test.junit)
}

// AGP relocates the converter's output directory, so read it from the task.
evaluationDependsOn(":iconsource")
val iconAssets = project(":iconsource").tasks.named("convertArcticonsIcons")
    .flatMap { (it.property("outputDir") as DirectoryProperty).dir("arcticons") }
val svgDir = rootProject.layout.projectDirectory.dir("third_party/arcticons/icons/white")
val verifyDir = layout.buildDirectory.dir("verify")

tasks.register<JavaExec>("verifyIconConversion") {
    group = "verification"
    description = "Rasterizes every converted icon at 96 px and gates IoU against inkscape renders."
    classpath = verify.runtimeClasspath
    mainClass = "dev.icondesigner.render.verify.VerifyConversionKt"
    maxHeapSize = "2g"
    systemProperty("java.awt.headless", "true")
    // Marlin samples 8 sub-rows per pixel by default, which quantizes horizontal edges.
    systemProperty("sun.java2d.renderer.subPixel_log2_Y", "8")
    val report = verifyDir.map { it.file("iou-report.txt") }
    inputs.dir(iconAssets)
    inputs.dir(svgDir)
    outputs.file(report)
    argumentProviders += CommandLineArgumentProvider {
        listOf(
            iconAssets.get().file("icons.bin").asFile.path,
            iconAssets.get().file("names.txt").asFile.path,
            svgDir.asFile.path,
            verifyDir.get().dir("inkscape-384").asFile.path,
            verifyDir.get().dir("diff").asFile.path,
            report.get().asFile.path,
            "96",
            "4",
        )
    }
}
