package dev.iconoclast.app.settings

import android.content.Context
import dev.iconoclast.render.OutputResolution
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The user's settings, spec 2.2 screen 6. Today only the output icon resolution, which
 * Apply builds the pack at and every preview passes for its grain cells.
 */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _resolution = MutableStateFlow(
        prefs.getString(RESOLUTION, null)?.let { s -> OutputResolution.entries.find { it.name == s } } ?: OutputResolution.FULL,
    )
    val resolution: StateFlow<OutputResolution> = _resolution

    fun setResolution(r: OutputResolution) {
        prefs.edit().putString(RESOLUTION, r.name).apply()
        _resolution.value = r
    }

    private companion object {
        const val FILE = "settings"
        const val RESOLUTION = "outputResolution"
    }
}
