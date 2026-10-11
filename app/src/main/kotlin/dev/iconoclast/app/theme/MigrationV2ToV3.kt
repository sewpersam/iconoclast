package dev.iconoclast.app.theme

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Schema 2 to 3: effects. Version 3 adds the `effect` layer type, one per effect, beside
 * the `effects` marker that version 2 already wrote. A version 2 stack holds no effect
 * layers, so the step only bumps the version: a pure rewrite, and the theme renders the
 * same bytes, which ThemeMigrationTest checks against the preset goldens.
 */
internal fun v2ToV3(v2: JsonObject, @Suppress("UNUSED_PARAMETER") now: () -> Long, @Suppress("UNUSED_PARAMETER") newId: () -> String): JsonObject {
    val theme = v2["theme"] as? JsonObject ?: throw ThemeFormatException("This theme is damaged: no theme.")
    if (theme["layers"] == null) throw ThemeFormatException("This theme is damaged: no layers.")
    return JsonObject(v2 + ("schemaVersion" to JsonPrimitive(3)))
}
