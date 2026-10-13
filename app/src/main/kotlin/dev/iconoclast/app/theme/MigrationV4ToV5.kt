package dev.iconoclast.app.theme

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Schema 4 to 5: brush strokes. Version 5 adds the `brushStrokes` effect type; a version 4
 * stack holds none, so the step only bumps the version: a pure rewrite, and the theme
 * renders the same bytes, which ThemeMigrationTest checks against the starter goldens. A
 * version 4 file that already has brush strokes is damaged. The bump is what lets an
 * older app refuse a theme with brush strokes as newer, not as damaged.
 */
internal fun v4ToV5(v4: JsonObject, @Suppress("UNUSED_PARAMETER") now: () -> Long, @Suppress("UNUSED_PARAMETER") newId: () -> String): JsonObject {
    val theme = v4["theme"] as? JsonObject ?: throw ThemeFormatException("This theme is damaged: no theme.")
    val layers = theme["layers"] as? JsonArray ?: throw ThemeFormatException("This theme is damaged: no layers.")
    val strokes = layers.any { l ->
        val effect = (l as? JsonObject)?.get("effect") as? JsonObject
        (effect?.get("type") as? JsonPrimitive)?.content == "brushStrokes"
    }
    if (strokes) throw ThemeFormatException("This theme is damaged: version 4 has no brush strokes.")
    return JsonObject(v4 + ("schemaVersion" to JsonPrimitive(5)))
}
