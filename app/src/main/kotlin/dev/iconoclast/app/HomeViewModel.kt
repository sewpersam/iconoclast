package dev.iconoclast.app

import android.app.Application
import android.content.Intent
import android.net.Uri
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

    suspend fun previews(theme: Theme): List<PreviewIcon> = container.previews.icons(theme)

    /** Creates a theme with the renderer defaults and passes its id on, for the editor. */
    fun create(onCreated: (String) -> Unit) = viewModelScope.launch {
        onCreated(repo.create("New theme", Theme()).id)
    }

    fun duplicate(id: String) = viewModelScope.launch { repo.duplicate(id) }

    fun rename(id: String, name: String) = viewModelScope.launch { repo.rename(id, name) }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }

    fun setActive(id: String) = viewModelScope.launch { repo.setActive(id) }

    fun export(id: String) = viewModelScope.launch {
        val doc = repo.get(id) ?: return@launch
        launches.trySend(withContext(Dispatchers.IO) { ThemeTransfer.shareIntent(getApplication(), doc) })
    }

    fun import(uri: Uri) = viewModelScope.launch {
        _message.value = try {
            val text = withContext(Dispatchers.IO) { ThemeTransfer.read(getApplication(), uri) }
            "Imported ${repo.import(text).name}"
        } catch (e: ThemeFormatException) {
            "Not imported. ${e.message}"
        }
    }

    fun messageShown() {
        _message.value = null
    }
}
