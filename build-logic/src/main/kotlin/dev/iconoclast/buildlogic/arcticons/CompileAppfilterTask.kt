package dev.iconoclast.buildlogic.arcticons

import dev.iconoclast.render.format.ComponentTableFormat
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Compiles appfilter.xml into `arcticons/components.bin` under [outputDir]. Never fails on bad entries. */
@CacheableTask
abstract class CompileAppfilterTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val appfilter: RegularFileProperty

    /** The converter's names.txt: line i is drawable index i. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val names: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun compile() {
        val index = names.get().asFile.readLines().filter { it.isNotEmpty() }
            .withIndex().associate { (i, name) -> name to i }
        val result = appfilter.get().asFile.inputStream().buffered().use { compileAppfilter(it, index) }
        val table = ComponentTableFormat.encode(result.entries)
        val dir = outputDir.get().asFile.resolve("arcticons")
        dir.deleteRecursively()
        dir.mkdirs()
        dir.resolve("components.bin").writeBytes(table)

        val report = buildString {
            appendLine("items ${result.items}")
            appendLine("entries ${result.entries.size}")
            appendLine("shortcuts ${result.shortcuts}")
            appendLine("shortFormExpanded ${result.expanded}")
            appendLine("calendarSkipped ${result.calendarSkipped}")
            appendLine("malformed ${result.malformed.size}")
            appendLine("unknownDrawable ${result.unknownDrawable.size}")
            appendLine("duplicates ${result.duplicates}")
            appendLine("conflicting ${result.conflicting.size}")
            appendLine("table.bytes ${table.size}")
            result.malformed.forEach { appendLine("malformed $it") }
            result.unknownDrawable.forEach { appendLine("unknownDrawable $it") }
            result.conflicting.forEach { appendLine("conflicting $it") }
        }
        reportFile.get().asFile.apply { parentFile.mkdirs() }.writeText(report)
        logger.lifecycle(
            "Arcticons appfilter: ${result.entries.size} components from ${result.items} items, " +
                "${result.calendarSkipped} calendar skipped, table ${table.size} bytes.",
        )
        if (result.malformed.isNotEmpty() || result.unknownDrawable.isNotEmpty()) {
            logger.warn(
                "Arcticons appfilter: skipped ${result.malformed.size} malformed and " +
                    "${result.unknownDrawable.size} unknown-drawable entries. Report: ${reportFile.get().asFile}",
            )
        }
    }
}
