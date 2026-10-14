package dev.iconoclast.app

import android.app.Application
import android.os.Process
import android.util.Log
import dev.iconoclast.app.coverage.CoverageStore
import dev.iconoclast.app.settings.AppSettings
import dev.iconoclast.app.texture.TextureFiles
import dev.iconoclast.app.theme.Presets
import dev.iconoclast.app.theme.ThemeRepository
import dev.iconoclast.iconsource.IconSource
import dev.iconoclast.pack.PackKeyStore
import dev.iconoclast.render.IconRenderer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** The app-scoped objects, created once in [IconoclastApp] and passed down. No DI framework. */
class AppContainer(private val app: Application) {
    /** Outlives every screen. For work that must finish even if the user leaves. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Imported textures on disk, and the renderer's bounded store that loads them on demand. */
    val textures = TextureFiles(File(app.filesDir, "textures"))

    /** The one renderer. Its mask cache is shared by previews, editor and Apply. */
    val renderer = IconRenderer(textures = textures.store)

    private val source = scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) { IconSource.open(app.assets) }

    /** The bundled icons, read once, off the main thread, on first use: about 9 MB. */
    suspend fun iconSource(): IconSource = source.await()

    val themes = ThemeRepository(File(app.filesDir, "themes"), presets = { Presets.read(app.assets) })

    val previews = ThemePreviews(this)

    /** What the last successful Apply installed, for the coverage diff on open. */
    val coverage = CoverageStore(File(app.filesDir, "coverage"))

    /**
     * Preview drawing for the editor strip and the home cards: a bounded pool, so previews
     * never take every core from the UI and its render thread.
     */
    val previewThreads: CoroutineDispatcher = pool("preview", PREVIEW_THREADS, Process.THREAD_PRIORITY_DEFAULT)

    /** The editor's big preview: its own thread, so it never waits behind the strip. */
    val heroThread: CoroutineDispatcher = pool("preview-hero", 1, Process.THREAD_PRIORITY_DEFAULT)

    /** The output resolution, read by Apply and the previews. */
    val settings = AppSettings(app)

    /** An image shared to the app, imported, waiting for the editor to add it as a layer. */
    private val sharedImage = java.util.concurrent.atomic.AtomicReference<String?>(null)

    fun shareImage(hash: String) = sharedImage.set(hash)

    /** The shared image's texture hash, once; null when none waits. */
    fun takeSharedImage(): String? = sharedImage.getAndSet(null)

    /** The pack signing key, spec 3.5: one instance, so Apply, export and import never race on its files. */
    val packKey = PackKeyStore(app.noBackupFilesDir, "${app.getString(R.string.app_name)} Pack")

    private companion object {
        const val TAG = "Iconoclast"

        /** Leaves a core to the UI, another to the big preview; two at least, four at most. */
        val PREVIEW_THREADS = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)

        fun pool(name: String, threads: Int, priority: Int): CoroutineDispatcher {
            val n = AtomicInteger()
            return Executors.newFixedThreadPool(threads) { r ->
                Thread({ Process.setThreadPriority(priority); r.run() }, "$name-${n.incrementAndGet()}").apply { isDaemon = true }
            }.asCoroutineDispatcher()
        }
    }

    init {
        scope.launch { RenderWarmup.run() }
        // A shared export, possibly carrying the signing key, never outlives the process that shared it.
        scope.launch(Dispatchers.IO) { ThemeTransfer.clearExports(app) }
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
