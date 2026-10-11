package dev.iconoclast.app.coverage

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * What the last successful Apply installed, spec 2.5: every launchable component seen
 * then (themed or not, `package/class`), the theme's id and the time in epoch ms. New
 * installs are found by diffing against it when the app opens, since PACKAGE_ADDED never
 * reaches a manifest receiver.
 */
@Serializable
data class AppliedCoverage(val themeId: String, val appliedAt: Long, val components: Set<String>)

/**
 * The last Apply's coverage, one JSON file in [dir], written atomically. A missing or
 * damaged file reads as never applied: the diff then has nothing to compare against,
 * which only hides the "new since Apply" badge until the next Apply.
 */
class CoverageStore(private val dir: File) {
    private val file = File(dir, FILE)

    fun read(): AppliedCoverage? = try {
        if (file.exists()) JSON.decodeFromString(Stored.serializer(), file.readText()).let {
            if (it.version == VERSION) AppliedCoverage(it.themeId, it.appliedAt, it.components.toSet()) else null
        } else null
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: IOException) {
        null
    }

    /** Throws IOException when the disk fails. */
    fun write(c: AppliedCoverage) {
        dir.mkdirs()
        val tmp = File(dir, ".$FILE.tmp")
        FileOutputStream(tmp).use {
            it.write(JSON.encodeToString(Stored.serializer(), Stored(VERSION, c.themeId, c.appliedAt, c.components.sorted())).toByteArray())
            it.fd.sync()
        }
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    /** The file format: sorted components, so one Apply gives one file. */
    @Serializable
    private class Stored(val version: Int, val themeId: String, val appliedAt: Long, val components: List<String>)

    private companion object {
        const val FILE = "last-apply.json"
        const val VERSION = 1
        val JSON = Json { prettyPrint = true }
    }
}
