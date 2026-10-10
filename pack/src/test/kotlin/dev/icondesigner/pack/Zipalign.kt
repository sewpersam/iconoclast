package dev.icondesigner.pack

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
