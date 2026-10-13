package dev.iconoclast.app.theme

import dev.iconoclast.render.Theme
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A saved theme, spec 2.4: a recipe, not images. The JSON is documented in
 * docs/theme-format.md. [created] and [modified] are epoch milliseconds.
 */
@Serializable
data class ThemeDocument(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val id: String,
    val name: String,
    val created: Long,
    val modified: Long,
    @Serializable(ThemeSerializer::class) val theme: Theme,
    /**
     * Per-icon overrides by appfilter drawable name, schema 4. Never in [theme]:
     * [effectiveTheme] and [effectiveGeometry] resolve them per icon.
     */
    val overrides: Map<String, IconOverride> = emptyMap(),
) {
    init {
        require(overrides.keys.none { it.isBlank() }) { "an override needs a drawable name" }
    }

    /** Pretty-printed JSON at the current schema version, overrides sorted by name. */
    fun toJson(): String = JSON.encodeToString(serializer(), copy(schemaVersion = CURRENT_SCHEMA, overrides = overrides.toSortedMap()))

    companion object {
        const val CURRENT_SCHEMA = 7

        /** Strict: unknown keys fail, every field is written. */
        val JSON = Json {
            encodeDefaults = true
            prettyPrint = true
        }
    }
}
