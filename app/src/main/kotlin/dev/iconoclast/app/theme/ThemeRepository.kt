package dev.iconoclast.app.theme

import dev.iconoclast.render.Theme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * The saved themes: one JSON file per theme in [dir], plus the active theme's id.
 * See docs/theme-format.md, Storage. Every write is atomic. The first load seeds
 * [presets], each a theme file's text, once per install.
 *
 * Thread-safe. Every suspend operation loads the directory first if nothing has yet;
 * the plain getters read the flows, which are empty until [load] has run.
 */
class ThemeRepository(
    private val dir: File,
    private val presets: () -> List<String> = { emptyList() },
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private var loaded = false

    private val _themes = MutableStateFlow<List<ThemeDocument>>(emptyList())
    /** Every theme, oldest first. */
    val themes: StateFlow<List<ThemeDocument>> = _themes

    private val _activeId = MutableStateFlow<String?>(null)
    /** The theme Apply uses. Null only when no theme exists. */
    val activeId: StateFlow<String?> = _activeId

    private val activeFile = File(dir, "active")
    private val seededFile = File(dir, "seeded")

    suspend fun load() = locked { }

    fun get(id: String): ThemeDocument? = _themes.value.find { it.id == id }

    fun active(): ThemeDocument? = _activeId.value?.let(::get)

    suspend fun create(name: String, theme: Theme): ThemeDocument = locked {
        val t = now()
        ThemeDocument(id = newId(), name = name.clean(), created = t, modified = t, theme = theme).also(::write)
    }

    /** Stores [doc] under its id, replacing any theme with that id, and stamps [ThemeDocument.modified]. */
    suspend fun save(doc: ThemeDocument): ThemeDocument = locked {
        doc.copy(schemaVersion = ThemeDocument.CURRENT_SCHEMA, name = doc.name.clean(), modified = now()).also(::write)
    }

    suspend fun rename(id: String, name: String): ThemeDocument? = locked {
        get(id)?.copy(name = name.clean(), modified = now())?.also(::write)
    }

    /** A copy named "<name> copy", with a new id. */
    suspend fun duplicate(id: String): ThemeDocument? = locked {
        val from = get(id) ?: return@locked null
        val t = now()
        from.copy(id = newId(), name = "${from.name} copy", created = t, modified = t).also(::write)
    }

    /** Deletes the theme. If it was active, the oldest remaining theme becomes active. */
    suspend fun delete(id: String) = locked {
        File(dir, "$id.json").delete()
        _themes.value = _themes.value.filterNot { it.id == id }
        if (_activeId.value == id) writeActive(_themes.value.firstOrNull()?.id)
    }

    suspend fun setActive(id: String) = locked {
        if (get(id) != null) writeActive(id)
    }

    /**
     * Adds a theme from exported JSON of any known schema version, under a new id.
     * Throws [ThemeFormatException] with a message for the user when it cannot be read.
     */
    suspend fun import(json: String): ThemeDocument = locked {
        val t = now()
        migrate(json, now, newId).copy(id = newId(), created = t, modified = t).also(::write)
    }

    /** The theme's file content, for sharing. */
    fun exportJson(id: String): String? = get(id)?.toJson()

    private suspend fun <T> locked(block: () -> T): T = mutex.withLock {
        withContext(io) {
            if (!loaded) {
                readAll()
                loaded = true
            }
            block()
        }
    }

    private fun readAll() {
        dir.mkdirs()
        val docs = dir.listFiles { f -> f.name.endsWith(".json") && !f.name.startsWith(".") }.orEmpty().mapNotNull { f ->
            // A damaged file is left on disk and skipped, so one bad theme cannot hide the rest.
            try {
                migrate(f.readText(), now, newId).copy(id = f.name.removeSuffix(".json"))
            } catch (e: ThemeFormatException) {
                null
            }
        }
        _themes.value = docs.sortedWith(ORDER)
        _activeId.value = activeFile.takeIf { it.exists() }?.readText()?.trim()?.takeIf { id -> docs.any { it.id == id } }
        if (!seededFile.exists()) seed()
        if (_activeId.value == null) writeActive(_themes.value.firstOrNull()?.id)
    }

    /** Presets keep their fixed ids, so seeding twice cannot duplicate them. */
    private fun seed() {
        val t = now()
        presets().forEachIndexed { i, text ->
            val preset = migrate(text, now, newId)
            if (get(preset.id) == null) write(preset.copy(created = t + i, modified = t + i))
        }
        writeAtomic(seededFile, "1\n")
    }

    private fun write(doc: ThemeDocument) {
        writeAtomic(File(dir, "${doc.id}.json"), doc.toJson())
        _themes.value = (_themes.value.filterNot { it.id == doc.id } + doc).sortedWith(ORDER)
        if (_activeId.value == null) writeActive(doc.id)
    }

    private fun writeActive(id: String?) {
        if (id == null) activeFile.delete() else writeAtomic(activeFile, id)
        _activeId.value = id
    }

    /** Temp file in the same directory, synced, then renamed over [target]. */
    private fun writeAtomic(target: File, text: String) {
        dir.mkdirs()
        val tmp = File(dir, ".${target.name}.tmp")
        FileOutputStream(tmp).use {
            it.write(text.toByteArray())
            it.fd.sync()
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun String.clean() = trim().ifEmpty { "Untitled" }

    private companion object {
        val ORDER = compareBy<ThemeDocument>({ it.created }, { it.id })
    }
}
