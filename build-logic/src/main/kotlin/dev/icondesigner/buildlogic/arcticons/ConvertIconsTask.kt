package dev.icondesigner.buildlogic.arcticons

import dev.icondesigner.render.format.BundleFormat
import dev.icondesigner.render.format.Icon
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Converts every Arcticons SVG into `arcticons/icons.bin` plus `arcticons/names.txt`
 * under [outputDir]. Icon i in the bundle is line i of the names file, sorted by name.
 */
@CacheableTask
abstract class ConvertIconsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val svgFiles: ConfigurableFileCollection

    @get:Input
    abstract val workers: Property<Int>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun convert() {
        val start = System.nanoTime()
        val files = sortByDrawableName(svgFiles.files)
        val names = files.map { it.name.removeSuffix(".svg") }
        val icons = arrayOfNulls<Icon>(files.size)
        val perIcon = arrayOfNulls<ConversionStats>(files.size)
        val failures = java.util.concurrent.ConcurrentLinkedQueue<String>()

        val pool = Executors.newFixedThreadPool(workers.get())
        try {
            files.forEachIndexed { i, file ->
                pool.execute {
                    val stats = ConversionStats()
                    try {
                        icons[i] = convertSvg(file.readText(), stats)
                        perIcon[i] = stats
                    } catch (e: Throwable) {
                        // Collected and rethrown below with every failing file named.
                        failures += "${file.name}: $e"
                    }
                }
            }
        } finally {
            pool.shutdown()
        }
        if (!pool.awaitTermination(1, TimeUnit.HOURS)) {
            pool.shutdownNow()
            throw GradleException("Arcticons conversion did not finish within an hour")
        }
        if (failures.isNotEmpty()) {
            throw GradleException(
                "Arcticons: ${failures.size} SVGs failed to convert:\n" + failures.sorted().joinToString("\n"),
            )
        }

        val total = ConversionStats()
        perIcon.forEach { total.addAll(it!!) }
        val bundle = BundleFormat.encode(icons.map { it!! })
        val dir = outputDir.get().asFile.resolve("arcticons")
        dir.deleteRecursively()
        dir.mkdirs()
        dir.resolve("icons.bin").writeBytes(bundle)
        dir.resolve("names.txt").writeText(names.joinToString("\n", postfix = "\n"))
        dir.resolve("NOTICE.txt").writeText(NOTICE)
        val seconds = (System.nanoTime() - start) / 1e9

        val primitives = icons.sumOf { it!!.primitives.size }
        val report = buildString {
            appendLine("icons ${files.size}")
            appendLine("primitives $primitives")
            appendLine("bundle.bytes ${bundle.size}")
            appendLine("names.bytes ${dir.resolve("names.txt").length()}")
            appendLine("seconds %.1f".format(seconds))
            total.counts.forEach { (k, v) -> appendLine("$k $v") }
        }
        reportFile.get().asFile.apply { parentFile.mkdirs() }.writeText(report)
        logger.lifecycle(
            "Arcticons: converted ${files.size} icons, $primitives primitives, bundle ${bundle.size} bytes, " +
                "%.1f s. Report: ${reportFile.get().asFile}".format(seconds),
        )
    }

    private companion object {
        const val NOTICE = """Icon data in this directory is converted from Arcticons
(https://github.com/Arcticons-Team/Arcticons) by the Arcticons Team and contributors.
Licensed under Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0),
https://creativecommons.org/licenses/by-sa/4.0/. Converted to a binary path format.
"""
    }
}

/** Sorts by the bare drawable name, the order DrawableNames binary-searches names.txt in. */
internal fun sortByDrawableName(files: Collection<File>): List<File> = files.sortedBy { it.name.removeSuffix(".svg") }
