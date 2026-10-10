package dev.iconoclast.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.iconoclast.app.theme.ThemeDocument
import dev.iconoclast.app.theme.ThemeFormatException
import dev.iconoclast.render.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

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

    /** Theme files that could not be read on load. They stay on disk. */
    val unreadable: StateFlow<List<String>> = repo.unreadable

    init {
        // The container loads at app start; this repeats a failed load and reports it.
        act("Themes could not be loaded") { repo.load() }
    }

    suspend fun previews(theme: Theme): List<PreviewIcon> = container.previews.icons(theme)

    private var creating = false

    /** Creates a theme with the renderer defaults and passes its id on, for the editor. Ignores repeat taps. */
    fun create(onCreated: (String) -> Unit) {
        if (creating) return
        creating = true
        act("New theme not saved") {
            try {
                onCreated(repo.create("New theme", Theme()).id)
            } finally {
                creating = false
            }
        }
    }

    fun duplicate(id: String) = act("Not duplicated") { repo.duplicate(id) }

    fun rename(id: String, name: String) = act("Not renamed") { repo.rename(id, name) }

    fun delete(id: String) = act("Not deleted") { repo.delete(id) }

    fun setActive(id: String) = act("Not set active") { repo.setActive(id) }

    fun export(id: String) = act("Not exported") {
        val doc = repo.get(id) ?: return@act
        launches.trySend(withContext(Dispatchers.IO) { ThemeTransfer.shareIntent(getApplication(), doc) })
    }

    fun import(uri: Uri) = act("Not imported") {
        _message.value = try {
            val text = withContext(Dispatchers.IO) { ThemeTransfer.read(getApplication(), uri) }
            "Imported ${repo.import(text).name}"
        } catch (e: ThemeFormatException) {
            "Not imported. ${e.message}"
        }
    }

    /** Runs [block]; a disk failure becomes a snackbar line instead of a crash. */
    private fun act(failure: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: IOException) {
                Log.w(TAG, failure, e)
                _message.value = "$failure. Storage error: ${e.message ?: e.javaClass.simpleName}"
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
