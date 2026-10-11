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
 * [dir]. The renderer's [store] holds decoded textures in memory; [load] fills it from
 * here on demand. Files are only added, never rewritten: a hash names one content.
 */
class TextureFiles(private val dir: File, val store: TextureStore) {
    private val _written = MutableStateFlow(0)

    /** Counts textures written to disk, so views that showed a texture missing can redraw. */
    val written: StateFlow<Int> = _written

    /**
     * Adds a texture PNG to the store and to disk, canonicalised by the store. Returns its
     * hash. Throws IllegalArgumentException when [png] is not a texture the store accepts.
     */
    fun add(png: ByteArray): String {
        val hash = store.put(png)
        write(hash)
        return hash
    }

    /** Writes a stored texture's canonical file, if it is not on disk yet. */
    fun write(hash: String) {
        val target = file(hash)
        if (target.exists()) return
        val bytes = requireNotNull(store.bytes(hash)) { "texture $hash is not in the store" }
        dir.mkdirs()
        val tmp = File(dir, ".$hash.tmp")
        FileOutputStream(tmp).use {
            it.write(bytes)
            it.fd.sync()
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        _written.value++
    }

    /**
     * Puts every texture of [hashes] that is on disk into the store, checking each file
     * against its hash. Returns the hashes that are neither in the store nor on disk, or
     * whose file is damaged. Decoding is slow; call it off the main thread.
     */
    fun load(hashes: Set<String>): Set<String> = hashes.filterTo(LinkedHashSet()) { hash ->
        if (hash in store) return@filterTo false
        val f = file(hash)
        if (!f.exists()) return@filterTo true
        try {
            store.put(hash, f.readBytes())
            false
        } catch (e: IOException) {
            true
        } catch (e: IllegalArgumentException) {
            true
        }
    }

    /** Imported textures, newest first. */
    fun recent(): List<String> = dir.listFiles { f -> HASH_FILE.matches(f.name) }.orEmpty()
        .sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.name })
        .map { it.name.removeSuffix(".png") }

    fun file(hash: String) = File(dir, "$hash.png")

    private companion object {
        val HASH_FILE = Regex("[0-9a-f]{64}\\.png")
    }
}
