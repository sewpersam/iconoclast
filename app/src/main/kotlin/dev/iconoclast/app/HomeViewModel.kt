package dev.iconoclast.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.iconoclast.app.coverage.CoverageScan
import dev.iconoclast.app.theme.ArchiveImport
import dev.iconoclast.app.theme.ThemeArchive
import dev.iconoclast.pack.DamagedKeyException
import dev.iconoclast.pack.InvalidKeyBackupException
import dev.iconoclast.pack.KeyState
import dev.iconoclast.render.OutputResolution
import dev.iconoclast.app.theme.ThemeDocument
import dev.iconoclast.pack.RestoreResult
import dev.iconoclast.pack.backup
import dev.iconoclast.app.texture.TextureImport
import dev.iconoclast.app.texture.TextureImportException
import dev.iconoclast.app.texture.TexturePresence
import dev.iconoclast.app.theme.ThemeFormatException
import dev.iconoclast.render.Theme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException

/** The themes home, spec 2.2 screen 1: list, new, duplicate, rename, delete, export, import, set active. */
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app.container
    private val repo = container.themes

    val themes: StateFlow<List<ThemeDocument>> = repo.themes
    val activeId: StateFlow<String?> = repo.activeId

    private val _message = MutableStateFlow<String?>(null)
    /** A one-line result for the snackbar: import done or why it failed. */
    val message: StateFlow<String?> = _message

    private val launches = Channel<Intent>(Channel.UNLIMITED)
    /** Intents the activity must start: the export share sheet. */
    val intents = launches.receiveAsFlow()

    private val opens = Channel<String>(Channel.UNLIMITED)
    /**
     * Ids of new themes the editor must open. A channel, not a callback: the activity may be
     * recreated while the theme is saved, and the next collector still receives the id.
     */
    val created = opens.receiveAsFlow()

    /** Theme files that could not be read on load. They stay on disk. */
    val unreadable: StateFlow<List<String>> = repo.unreadable

    /** The output resolution, which the card previews draw at. */
    val resolution: StateFlow<OutputResolution> = container.settings.resolution

    private val _newSinceApply = MutableStateFlow(0)
    /** Apps installed since the last Apply that Arcticons covers, by package, spec 2.5: the badge on Apply. */
    val newSinceApply: StateFlow<Int> = _newSinceApply

    private val _keyPrompt = MutableStateFlow<KeyState?>(null)
    /**
     * An archive with a pack key waits for the restore question: this phone has no key
     * ([KeyState.NONE]) or a damaged one ([KeyState.DAMAGED]). Null when nothing is asked.
     */
    val keyPrompt: StateFlow<KeyState?> = _keyPrompt
    private var pendingImport: Pair<Uri, ThemeArchive>? = null

    init {
        // The container loads at app start; this repeats a failed load and reports it.
        act("Themes could not be loaded") { repo.load() }
    }

    /** Changes when a texture is added, so cards that lacked it redraw. */
    val texturesWritten: StateFlow<Int> = container.textures.written

    suspend fun previews(doc: ThemeDocument): List<PreviewIcon> = container.previews.icons(doc)

    private var creating = false

    /** Creates a theme with the renderer defaults and sends its id to [created], for the editor. Ignores repeat taps. */
    fun create() {
        if (creating) return
        creating = true
        act("New theme not saved") {
            try {
                opens.trySend(repo.create("New theme", Theme()).id)
            } finally {
                creating = false
            }
        }
    }

    fun duplicate(id: String) = act("Not duplicated") { repo.duplicate(id) }

    /**
     * An image shared to the app from another one: imported, then opened in the editor of
     * the theme in use as a new image layer, at the crop step.
     */
    fun receiveImage(uri: Uri) = act("Image not added") {
        _message.value = "Adding the shared image"
        val hash = try {
            withContext(Dispatchers.IO) { TextureImport.import(getApplication(), uri, container.textures) }
        } catch (e: TextureImportException) {
            Log.w(TAG, "Shared image not imported", e)
            _message.value = "Image not added. ${e.message}"
            return@act
        }
        repo.load()
        val target = repo.active() ?: repo.themes.value.firstOrNull()
        if (target == null) {
            _message.value = "Image not added. There is no theme to add it to."
            return@act
        }
        container.shareImage(hash)
        opens.trySend(target.id)
    }

    fun rename(id: String, name: String) = act("Not renamed") { repo.rename(id, name) }

    fun delete(id: String) = act("Not deleted") { repo.delete(id) }

    fun setActive(id: String) = act("Not set active") { repo.setActive(id) }

    /**
     * Shares the theme as a .zip with its textures. A texture missing from the phone stops
     * it. [includeKey] adds the pack key; pass it only after the warning was tapped.
     */
    fun export(id: String, includeKey: Boolean = false) = act("Not shared") {
        val doc = repo.get(id) ?: return@act
        val missing = withContext(Dispatchers.IO) { TexturePresence.ensure(container.textures, doc.theme) }
        if (missing.isNotEmpty()) {
            _message.value = "Not shared. ${TexturePresence.message(missing.size)}"
            return@act
        }
        val key = if (!includeKey) null else withContext(Dispatchers.IO) { container.packKey.backup() } ?: run {
            _message.value = "Not shared. There is no pack key yet: it is made on the first Apply."
            return@act
        }
        launches.trySend(withContext(Dispatchers.IO) { ThemeTransfer.shareIntent(getApplication(), doc, container.textures.store, key) })
    }

    /** Re-runs the coverage diff, spec 2.5: called whenever home is shown. */
    fun refreshCoverage() = viewModelScope.launch {
        _newSinceApply.value = try {
            CoverageScan.report(getApplication(), container).reapplyWouldTheme.mapTo(HashSet()) { it.substringBefore('/') }.size
        } catch (e: IOException) {
            Log.w(TAG, "Coverage not read", e)
            0
        }
    }

    /**
     * Imports a theme .zip or JSON, spec 2.4 and 6.5, in two passes. The first reads it
     * without the key entries. If it carries a key and this phone has none, [keyPrompt]
     * asks, and [answerKeyPrompt] goes on. If this phone has a key, the theme imports
     * without the archive's key, and the message says whether that key was the same.
     */
    fun import(uri: Uri) = act("Not imported") {
        _message.value = importing {
            val archive = read(uri, readKey = false)
            val local = withContext(Dispatchers.IO) { container.packKey.state() }
            if (archive.hasKey && local != KeyState.OK) {
                pendingImport = uri to archive
                _keyPrompt.value = local
                return@act
            }
            val note = if (archive.hasKey) keyNote(uri) else ""
            "Imported ${store(archive, restoreKey = false).doc.name}$note"
        }
    }

    /** The restore question's answer: [restore] re-reads the file with its key and restores it. */
    fun answerKeyPrompt(restore: Boolean) = act("Not imported") {
        val (uri, archive) = pendingImport ?: return@act
        pendingImport = null
        _keyPrompt.value = null
        _message.value = importing {
            if (restore) {
                val result = store(read(uri, readKey = true), restoreKey = true)
                "Imported ${result.doc.name}" + if (result.key == RestoreResult.RESTORED) " and restored its pack key" else ""
            } else {
                "Imported ${store(archive, restoreKey = false).doc.name} without its pack key"
            }
        }
    }

    /** What became of an archive's key when this phone already has one. Never restored. */
    private suspend fun keyNote(uri: Uri): String = try {
        val theirs = read(uri, readKey = true).key?.open()?.certificate?.encoded
        val ours = withContext(Dispatchers.IO) { container.packKey.loadOrCreate().certificate.encoded }
        if (theirs != null && theirs.contentEquals(ours)) ". Its pack key is already this phone's."
        else ". Its pack key was not imported: this phone already has a different one, which it keeps."
    } catch (e: ThemeFormatException) {
        ". Its pack key could not be read and was skipped."
    } catch (e: InvalidKeyBackupException) {
        ". Its pack key could not be read and was skipped."
    }

    /** The whole file is validated before anything is stored. */
    private suspend fun read(uri: Uri, readKey: Boolean): ThemeArchive = withContext(Dispatchers.IO) {
        ThemeTransfer.read(getApplication(), uri, readKey) { container.textures.load(setOf(it)).isEmpty() }
    }

    private suspend fun store(archive: ThemeArchive, restoreKey: Boolean) = withContext(Dispatchers.IO) {
        ArchiveImport.store(archive, restoreKey, container.packKey, container.textures, repo)
    }

    /** The import's message, or why it failed. */
    private inline fun importing(block: () -> String): String = try {
        block()
    } catch (e: ThemeFormatException) {
        "Not imported. ${e.message}"
    } catch (e: OutOfMemoryError) {
        "Not imported. This file is too large for the memory available."
    }

    /** Runs [block]; a disk failure becomes a snackbar line instead of a crash. */
    private fun act(failure: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: IOException) {
                Log.w(TAG, failure, e)
                _message.value = "$failure. Storage error: ${e.message ?: e.javaClass.simpleName}"
            } catch (e: DamagedKeyException) {
                Log.w(TAG, failure, e)
                _message.value = "$failure. The pack pack key on this phone is damaged. " +
                    "Restore it by importing a backup that includes the key, or make a new key from Apply."
            } catch (e: GeneralSecurityException) {
                // A pack key store this phone could not read, by export or key restore.
                Log.w(TAG, failure, e)
                _message.value = "$failure. The pack key on this phone could not be read: ${e.message ?: e.javaClass.simpleName}"
            } catch (e: IllegalStateException) {
                // CancellationException is an IllegalStateException: cancellation must pass.
                if (e is CancellationException) throw e
                Log.w(TAG, failure, e)
                _message.value = "$failure. ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun messageShown() {
        _message.value = null
    }

    private companion object {
        const val TAG = "Iconoclast"
    }
}
