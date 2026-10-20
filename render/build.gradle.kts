// Pure Kotlin JVM. No Android dependency.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The bundle formats are shared with build-logic, which writes them.
sourceSets.main {
    kotlin.srcDir("src/format/kotlin")
}

// The golden set definition is shared with :app's on-device byte identity test.
sourceSets.test {
    kotlin.srcDir("src/golden/kotlin")
}

// JVM-only conversion check: java.awt rasterizer plus inkscape references. Not shipped.
val verify = sourceSets.create("verify") {
    // The benchmark times the golden set's effect looks.
    kotlin.srcDir("src/golden/kotlin")
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
    mainClass = "dev.iconoclast.render.verify.VerifyConversionKt"
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

// ./gradlew :render:test -PupdateGoldens rewrites the golden PNGs and their sample bundle.
tasks.test {
    maxHeapSize = "1g"
    if (providers.gradleProperty("updateGoldens").isPresent) {
        systemProperty("golden.update", "true")
        systemProperty("golden.dir", layout.projectDirectory.dir("src/test/resources/golden").asFile.path)
        inputs.dir(iconAssets)
        systemProperty("golden.source", iconAssets.get().asFile.path)
        outputs.upToDateWhen { false }
        filter.includeTestsMatching("*GoldenTest.goldens*")
    }
}

tasks.register<JavaExec>("benchmarkRenderer") {
    group = "verification"
    description = "Times the renderer on the JVM against the spec 3.3 budgets."
    classpath = verify.runtimeClasspath
    mainClass = "dev.iconoclast.render.verify.RenderBenchmarkKt"
    maxHeapSize = "2g"
    // -Prows=INVERT,JADE_LIKE limits the theme rows.
    providers.gradleProperty("rows").orNull?.let { systemProperty("rows", it) }
    inputs.dir(iconAssets)
    argumentProviders += CommandLineArgumentProvider {
        listOf(iconAssets.get().file("icons.bin").asFile.path, "4")
    }
}

// ./gradlew :render:brushContactSheet [-Ptile=216] writes the brush strokes contact sheet.
tasks.register<JavaExec>("brushContactSheet") {
    group = "verification"
    description = "Renders 12 sample glyphs under Jade, Brush A, B, C and the old streaks to docs/verification/brush-strokes."
    classpath = verify.runtimeClasspath
    mainClass = "dev.iconoclast.render.verify.BrushContactSheetKt"
    maxHeapSize = "1g"
    systemProperty("java.awt.headless", "true")
    val out = rootProject.layout.projectDirectory.file("docs/verification/brush-strokes/contact-sheet.png")
    args(
        layout.projectDirectory.file("src/test/resources/golden/samples.bin").asFile.path,
        out.asFile.path,
        providers.gradleProperty("tile").getOrElse("216"),
    )
}

// ./gradlew :render:feedbackSheets -Pout=<dir> writes the phone feedback round 1 contact sheets.
tasks.register<JavaExec>("feedbackSheets") {
    group = "verification"
    description = "Renders the feedback looks, brush strokes before and after, and the knockout sheets."
    classpath = verify.runtimeClasspath
    mainClass = "dev.iconoclast.render.verify.FeedbackSheetKt"
    maxHeapSize = "1g"
    systemProperty("java.awt.headless", "true")
    args(
        layout.projectDirectory.file("src/test/resources/golden/samples.bin").asFile.path,
        providers.gradleProperty("out").getOrElse(rootProject.layout.projectDirectory.dir("docs/verification/feedback-1").asFile.path),
    )
}

// ./gradlew :render:bgSizeSheet -Pout=<dir> writes the background Size contact sheet.
tasks.register<JavaExec>("bgSizeSheet") {
    group = "verification"
    description = "Renders the background Size looks at 100, 85, 70 and 50%."
    classpath = verify.runtimeClasspath
    mainClass = "dev.iconoclast.render.verify.BgSizeSheetKt"
    maxHeapSize = "1g"
    systemProperty("java.awt.headless", "true")
    args(
        layout.projectDirectory.file("src/test/resources/golden/samples.bin").asFile.path,
        providers.gradleProperty("out").getOrElse(rootProject.layout.projectDirectory.dir("docs/verification/bg-size").asFile.path),
    )
}

// ./gradlew :render:paintSheet -Pout=<dir> -Pprefix=<name> writes Painted brush stroke sheets at 216 and 432 px.
tasks.register<JavaExec>("paintSheet") {
    group = "verification"
    description = "Renders Painted brush strokes at grid and pack size."
    classpath = verify.runtimeClasspath
    mainClass = "dev.iconoclast.render.verify.PaintSheetKt"
    maxHeapSize = "1g"
    systemProperty("java.awt.headless", "true")
    args(
        layout.projectDirectory.file("src/test/resources/golden/samples.bin").asFile.path,
        providers.gradleProperty("out").getOrElse(rootProject.layout.projectDirectory.dir("docs/verification/brush-texture").asFile.path),
        providers.gradleProperty("prefix").getOrElse("after"),
    )
}

tasks.register<JavaExec>("strokeProfile") {
    group = "verification"
    description = "Times Jade's brush strokes at preview size, uncached; -Pjfr=<file> records a flight recording."
    classpath = verify.runtimeClasspath
    mainClass = "dev.iconoclast.render.verify.StrokeProfileKt"
    maxHeapSize = "1g"
    providers.gradleProperty("jfr").orNull?.let { jvmArgs("-XX:StartFlightRecording=filename=$it,settings=profile") }
    args(layout.projectDirectory.file("src/test/resources/golden/samples.bin").asFile.path)
}
