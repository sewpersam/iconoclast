package dev.iconoclast.app.theme

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Schema 5 to 6: a paint layer's eye. Version 6 gives every paint layer a `visible`
 * flag; a version 5 layer was always shown, so the step writes `"visible": true` on each
 * paint layer and bumps the version. Opacity is untouched, so the theme renders the same
 * bytes, which ThemeMigrationTest checks against the goldens. A version 5 paint layer
 * that already has `visible` is damaged.
 */
internal fun v5ToV6(v5: JsonObject, @Suppress("UNUSED_PARAMETER") now: () -> Long, @Suppress("UNUSED_PARAMETER") newId: () -> String): JsonObject {
    val theme = v5["theme"] as? JsonObject ?: throw ThemeFormatException("This theme is damaged: no theme.")
    val layers = theme["layers"] as? JsonArray ?: throw ThemeFormatException("This theme is damaged: no layers.")
    val upgraded = layers.map { l ->
        val layer = l as? JsonObject ?: return@map l
        if ((layer["type"] as? JsonPrimitive)?.content != "paint") return@map layer
        if ("visible" in layer) throw ThemeFormatException("This theme is damaged: version 5 has no hidden layers.")
        JsonObject(layer + ("visible" to JsonPrimitive(true)))
    }
    return JsonObject(v5 + mapOf("schemaVersion" to JsonPrimitive(6), "theme" to JsonObject(theme + ("layers" to JsonArray(upgraded)))))
}
