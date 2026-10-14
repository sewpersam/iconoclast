package dev.iconoclast.app.theme

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean

/**
 * Schema 7 to 8, the look pass: every paint layer's mapping trades `lockOffset` for the
 * per-icon placement set. `lockOffset` true becomes `randomShift` 0, the centred patch;
 * false becomes 1, the icon's own patch. No random turn or zoom, seed 0, no crop. That
 * places every texture and noise exactly as before, so a migrated theme renders the same
 * bytes. Angular gradients and the blur effect are new and need no rewrite.
 */
internal fun v7ToV8(v7: JsonObject, @Suppress("UNUSED_PARAMETER") now: () -> Long, @Suppress("UNUSED_PARAMETER") newId: () -> String): JsonObject {
    val theme = v7["theme"] as? JsonObject ?: throw ThemeFormatException("This theme is damaged: no theme.")
    val layers = theme["layers"] as? JsonArray ?: throw ThemeFormatException("This theme is damaged: no layers.")
    val upgraded = JsonArray(layers.map { l ->
        val layer = l as? JsonObject ?: throw ThemeFormatException("This theme is damaged: a layer is not an object.")
        val mapping = layer["mapping"] as? JsonObject ?: return@map layer
        val lock = (mapping["lockOffset"] as? JsonPrimitive)?.let { runCatching { it.boolean }.getOrNull() }
            ?: throw ThemeFormatException("This theme is damaged: a mapping has no lockOffset.")
        val m = JsonObject(mapping - "lockOffset" + mapOf(
            "randomShift" to JsonPrimitive(if (lock) 0.0 else 1.0),
            "randomRotation" to JsonPrimitive(0.0),
            "randomScale" to JsonPrimitive(0.0),
            "seed" to JsonPrimitive(0),
            "crop" to JsonNull,
        ))
        JsonObject(layer + ("mapping" to m))
    })
    return JsonObject(v7 + mapOf("schemaVersion" to JsonPrimitive(8), "theme" to JsonObject(theme + ("layers" to upgraded))))
}
