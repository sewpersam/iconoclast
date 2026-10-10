package dev.iconoclast.app

import android.app.Application
import android.util.Log
import dev.iconoclast.app.theme.Presets
import dev.iconoclast.app.theme.ThemeRepository
import dev.iconoclast.iconsource.IconSource
import dev.iconoclast.render.IconRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/** The app-scoped objects, created once in [IconoclastApp] and passed down. No DI framework. */
class AppContainer(private val app: Application) {
    /** Outlives every screen. For work that must finish even if the user leaves. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The one renderer. Its mask cache is shared by previews, editor and Apply. */
    val renderer = IconRenderer()

    private val source = scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) { IconSource.open(app.assets) }

    /** The bundled icons, read once, off the main thread, on first use: about 9 MB. */
    suspend fun iconSource(): IconSource = source.await()

    val themes = ThemeRepository(File(app.filesDir, "themes"), presets = { Presets.read(app.assets) })

    val previews = ThemePreviews(this)

    private companion object {
        const val TAG = "Iconoclast"
    }

    init {
        scope.launch { RenderWarmup.run() }
        scope.launch {
            // HomeViewModel loads again and shows the failure; here it is only logged.
            try {
                themes.load()
            } catch (e: IOException) {
                Log.w(TAG, "Themes could not be loaded", e)
            }
            themes.unreadable.value.forEach { Log.w(TAG, "Theme file skipped: $it") }
        }
    }
}
