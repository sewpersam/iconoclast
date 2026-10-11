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
) {
    /** Pretty-printed JSON at the current schema version. */
    fun toJson(): String = JSON.encodeToString(serializer(), copy(schemaVersion = CURRENT_SCHEMA))

    companion object {
        const val CURRENT_SCHEMA = 3

        /** Strict: unknown keys fail, every field is written. */
        val JSON = Json {
            encodeDefaults = true
            prettyPrint = true
        }
    }
}
