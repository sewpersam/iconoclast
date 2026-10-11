package dev.iconoclast.app.coverage

import android.content.Context
import dev.iconoclast.app.AppContainer
import dev.iconoclast.app.InstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The coverage diff as the app runs it on open, spec 2.5: installed launchable apps now,
 * resolved through the appfilter, against the last successful Apply, with the active
 * theme's overrides. Used by the home badge and the coverage screen.
 */
object CoverageScan {
    suspend fun report(context: Context, container: AppContainer): CoverageReport = withContext(Dispatchers.IO) {
        val source = container.iconSource()
        container.themes.load()
        val installed = InstalledApps.launchableComponents(context)
        val overridden = container.themes.active()?.overrides?.keys.orEmpty()
        CoverageReport.of(
            installed,
            container.coverage.read(),
            { c -> source.resolve(c).takeIf { it >= 0 }?.let(source.names::name) },
            overridden,
        )
    }
}
