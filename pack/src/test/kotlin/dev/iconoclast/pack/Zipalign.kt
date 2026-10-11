package dev.iconoclast.pack

import java.io.File
import kotlin.test.assertEquals

/** Runs `zipalign -c -p -v 4` from build-tools and asserts it passes. Returns its output. */
fun assertZipaligned(apk: File): String {
    val process = ProcessBuilder(System.getProperty("zipalign"), "-c", "-p", "-v", "4", apk.path)
        .redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    assertEquals(0, process.waitFor(), "zipalign -c failed:\n" + output.lines().filter { "BAD" in it || "fail" in it.lowercase() }.take(20).joinToString("\n"))
    return output
}

fun templateBytes(): ByteArray = File(requireNotNull(System.getProperty("template.apk"))).readBytes()

/** Writes each replacement to its own file under [dir], rewrites [template] into [out], and returns [out]. */
fun rewriteToFile(template: ByteArray, replacements: Map<String, ByteArray>, dir: File, out: File): File {
    val inputs = File(dir, out.name + ".in").apply { deleteRecursively(); mkdirs() }
    val files = replacements.entries.withIndex().associate { (i, e) -> e.key to File(inputs, "$i").apply { writeBytes(e.value) } }
    ZipRewriter.rewrite(template, files, out)
    return out
}
