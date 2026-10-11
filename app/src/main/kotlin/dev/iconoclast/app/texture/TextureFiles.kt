package dev.iconoclast.app.texture

import dev.iconoclast.render.texture.TextureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Imported textures on disk, spec 2.4: one canonical PNG per texture, `<sha256>.png` in
 * [dir]. The disk is the record: a texture is present when its file is. [store], the
 * renderer's, keeps a bounded set decoded in memory and loads the rest from here on
 * demand. Files are only added, never rewritten: a hash names one content.
 */
class TextureFiles(private val dir: File, maxBytes: Long = TextureStore.DEFAULT_MAX_BYTES) {
    val store = TextureStore(maxBytes, load = ::read, exists = { file(it).exists() })

    private val _written = MutableStateFlow(0)

    /** Counts textures written to disk, so views that showed a texture missing can redraw. */
    val written: StateFlow<Int> = _written

    /**
     * Canonicalises a texture PNG, writes its file, and returns its hash. Throws
     * IllegalArgumentException when [png] is not a texture the store accepts.
     */
    fun add(png: ByteArray): String {
        val c = TextureStore.canonical(png)
        write(c.hash, c.png)
        return c.hash
    }

    /** Writes a canonical texture file already checked against [hash], if it is not on disk yet. */
    fun write(hash: String, canonical: ByteArray) {
        val target = file(hash)
        if (target.exists()) return
        dir.mkdirs()
        val tmp = File(dir, ".$hash.tmp")
        FileOutputStream(tmp).use {
            it.write(canonical)
            it.fd.sync()
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        store.forget(hash)
        _written.value++
    }

    /**
     * The hashes of [hashes] that cannot be rendered: no file, or a file that no longer
     * matches its hash. Decodes each one through the store, so it is slow; call it off the
     * main thread. The store stays bounded whatever the count.
     */
    fun load(hashes: Set<String>): Set<String> = hashes.filterTo(LinkedHashSet()) { store[it] == null }

    /** Imported textures, newest first. */
    fun recent(): List<String> = dir.listFiles { f -> HASH_FILE.matches(f.name) }.orEmpty()
        .sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.name })
        .map { it.name.removeSuffix(".png") }

    fun file(hash: String) = File(dir, "$hash.png")

    private fun read(hash: String): ByteArray? = try {
        file(hash).takeIf { it.exists() }?.readBytes()
    } catch (e: IOException) {
        null
    }

    private companion object {
        val HASH_FILE = Regex("[0-9a-f]{64}\\.png")
    }
}
