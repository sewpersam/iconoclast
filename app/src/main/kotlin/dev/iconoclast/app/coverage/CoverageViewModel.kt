package dev.iconoclast.app.coverage

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.iconoclast.app.container
import dev.iconoclast.app.theme.staleOverrides
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** One row: the app's label, its component, and its drawable when Arcticons has one. */
data class CoverageRow(val label: String, val component: String, val drawable: String?)

/** The coverage screen's content, spec 2.2 screen 5. */
data class CoverageUi(
    val loading: Boolean = true,
    val newSinceApply: List<CoverageRow> = emptyList(),
    /** How many of [newSinceApply] a re-apply would theme. */
    val reapplyWouldTheme: Int = 0,
    val themed: List<CoverageRow> = emptyList(),
    val overridden: List<CoverageRow> = emptyList(),
    val notInArcticons: List<CoverageRow> = emptyList(),
    /** Overrides whose drawable or swap target this Arcticons version lacks. */
    val staleOverrides: List<String> = emptyList(),
    val neverApplied: Boolean = false,
    val error: String? = null,
)

/** Runs [CoverageScan] and labels each component, sorted by label within each section. */
class CoverageViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app.container
    private val _ui = MutableStateFlow(CoverageUi())
    val ui: StateFlow<CoverageUi> = _ui

    fun refresh() = viewModelScope.launch {
        _ui.value = try {
            load()
        } catch (e: IOException) {
            Log.w(TAG, "Coverage not read", e)
            CoverageUi(loading = false, error = "Coverage could not be read: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun load(): CoverageUi = withContext(Dispatchers.IO) {
        val report = CoverageScan.report(getApplication(), container)
        val source = container.iconSource()
        val pm = getApplication<Application>().packageManager
        val labels = HashMap<String, String>()
        fun row(e: CoverageEntry) = CoverageRow(labels.getOrPut(e.component) { label(pm, e.component) }, e.component, e.drawable)
        fun rows(s: CoverageStatus) = report.entries.filter { it.status == s }.map(::row).sortedBy { it.label.lowercase() }
        val byComponent = report.entries.associateBy { it.component }
        CoverageUi(
            loading = false,
            newSinceApply = report.installedSinceApply.mapNotNull { byComponent[it]?.let(::row) }.sortedBy { it.label.lowercase() },
            reapplyWouldTheme = report.reapplyWouldTheme.size,
            themed = rows(CoverageStatus.THEMED),
            overridden = rows(CoverageStatus.OVERRIDDEN),
            notInArcticons = rows(CoverageStatus.NOT_IN_ARCTICONS),
            staleOverrides = container.themes.active()?.staleOverrides { source.indexOf(it) >= 0 }.orEmpty(),
            neverApplied = report.lastApply == null,
        )
    }

    private fun label(pm: PackageManager, component: String): String = try {
        val (pkg, cls) = component.split('/', limit = 2)
        pm.getActivityInfo(ComponentName(pkg, cls), PackageManager.ComponentInfoFlags.of(0)).loadLabel(pm).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        component.substringBefore('/')
    }

    private companion object {
        const val TAG = "Iconoclast"
    }
}
