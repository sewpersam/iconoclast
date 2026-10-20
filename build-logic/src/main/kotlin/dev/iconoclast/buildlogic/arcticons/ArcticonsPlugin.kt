package dev.iconoclast.buildlogic.arcticons

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Registers the Arcticons converter and appfilter compiler. The applying build script
 * adds their output directories to its assets.
 */
class ArcticonsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val source = project.rootProject.layout.projectDirectory.dir("third_party/arcticons")
        // Iconoclast's own glyphs and appfilter entries, bundled with the app, never upstreamed.
        val bundled = project.rootProject.layout.projectDirectory.dir("iconsource/bundled")
        val generated = project.layout.buildDirectory.dir("generated/arcticons")
        val reports = project.layout.buildDirectory.dir("reports/arcticons")

        val convert = project.tasks.register("convertArcticonsIcons", ConvertIconsTask::class.java) {
            it.group = "arcticons"
            it.description = "Converts the Arcticons SVGs into the binary icon bundle."
            it.svgFiles.from(project.fileTree(source.dir("icons/white")) { t -> t.include("*.svg") })
            it.svgFiles.from(project.fileTree(bundled) { t -> t.include("*.svg") })
            it.workers.convention(4)
            it.outputDir.convention(generated.map { d -> d.dir("icons") })
            it.reportFile.convention(reports.map { d -> d.file("conversion.txt") })
        }
        project.tasks.register("compileArcticonsAppfilter", CompileAppfilterTask::class.java) {
            it.group = "arcticons"
            it.description = "Compiles the Arcticons appfilter into the component table."
            it.appfilter.convention(source.file("app/src/main/assets/appfilter.xml"))
            it.bundledAppfilter.convention(bundled.file("appfilter.xml"))
            it.names.convention(convert.flatMap { c -> c.outputDir.file("arcticons/names.txt") })
            it.outputDir.convention(generated.map { d -> d.dir("appfilter") })
            it.reportFile.convention(reports.map { d -> d.file("appfilter.txt") })
        }
    }
}
