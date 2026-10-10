package dev.iconoclast.render.verify

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Renders reference PNGs of the original SVGs with inkscape, a few long-lived `--shell`
 * processes instead of one process per file. Existing PNGs newer than their SVG are reused.
 */
class InkscapeReferences(private val svgDir: File, private val refDir: File, private val size: Int) {

    fun file(name: String) = File(refDir, "$name.png")

    /** Renders every missing or stale reference. Returns how many were rendered. */
    fun ensure(names: List<String>, workers: Int): Int {
        refDir.mkdirs()
        val todo = names.filter {
            val ref = file(it)
            !ref.exists() || ref.lastModified() < File(svgDir, "$it.svg").lastModified()
        }
        if (todo.isEmpty()) return 0
        val chunks = todo.chunked((todo.size + workers - 1) / workers)
        val processes = chunks.mapIndexed { i, chunk ->
            val commands = File(refDir.parentFile, "inkscape-$i.commands")
            commands.writeText(
                chunk.joinToString("\n", postfix = "\nquit\n") { name ->
                    "file-open:${File(svgDir, "$name.svg").absolutePath}; " +
                        "export-filename:${file(name).absolutePath}; export-area-page; " +
                        "export-width:$size; export-height:$size; export-background-opacity:0; " +
                        "export-do; file-close"
                },
            )
            ProcessBuilder("inkscape", "--shell")
                .redirectInput(commands)
                .redirectOutput(File(refDir.parentFile, "inkscape-$i.log"))
                .redirectErrorStream(true)
                .start()
        }
        for (p in processes) {
            check(p.waitFor(2, TimeUnit.HOURS)) { "inkscape timed out" }
        }
        val missing = todo.filterNot { file(it).exists() }
        check(missing.isEmpty()) { "inkscape produced no PNG for ${missing.size} icons, first: ${missing.take(5)}" }
        return todo.size
    }
}
