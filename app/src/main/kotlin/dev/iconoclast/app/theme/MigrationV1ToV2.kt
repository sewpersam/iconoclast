package dev.iconoclast.app.theme

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Schema 1 to 2: the layer stack. Version 1 painted the background from
 * `theme.background.paint` and the glyph from `theme.glyph`, body first, then lines.
 * Version 2 expresses exactly that as the base stack, `Theme.baseLayers` in :render:
 * the background paint clipped to the background, the effects slot, then a solid layer
 * on the fill body and one on the lines. A pure rewrite of the JSON object; the renders
 * are byte-identical, which PresetsTest checks against the M4 goldens.
 */
internal fun v1ToV2(v1: JsonObject, @Suppress("UNUSED_PARAMETER") now: () -> Long, @Suppress("UNUSED_PARAMETER") newId: () -> String): JsonObject {
    val theme = v1["theme"] as? JsonObject ?: throw ThemeFormatException("This theme is damaged: no theme.")
    val background = theme["background"] as? JsonObject ?: throw ThemeFormatException("This theme is damaged: no background.")
    val glyph = theme["glyph"] as? JsonObject ?: throw ThemeFormatException("This theme is damaged: no glyph paint.")
    val paint = background["paint"] ?: throw ThemeFormatException("This theme is damaged: no background paint.")
    val body = glyph["body"] ?: throw ThemeFormatException("This theme is damaged: no body colour.")
    val line = glyph["line"] ?: throw ThemeFormatException("This theme is damaged: no line colour.")

    val layers = JsonArray(listOf(
        paintLayer("background", paint, "BACKGROUND"),
        JsonObject(mapOf("type" to JsonPrimitive("effects"), "id" to JsonPrimitive("effects"))),
        paintLayer("icon-fill", solid(body), "ICON_FILL"),
        paintLayer("icon-lines", solid(line), "ICON_LINES"),
    ))
    val newTheme = JsonObject(theme.filterKeys { it != "glyph" } + mapOf(
        "background" to JsonObject(background.filterKeys { it != "paint" }),
        "layers" to layers,
    ))
    return JsonObject(v1 + mapOf("schemaVersion" to JsonPrimitive(2), "theme" to newTheme))
}

private fun solid(color: JsonElement) =
    JsonObject(mapOf("type" to JsonPrimitive("solid"), "color" to color))

/** A normal, fully opaque, unmatted layer with the default mapping. */
private fun paintLayer(id: String, source: JsonElement, clip: String) = JsonObject(mapOf(
    "type" to JsonPrimitive("paint"),
    "id" to JsonPrimitive(id),
    "source" to source,
    "clip" to JsonPrimitive(clip),
    "clipInverse" to JsonPrimitive(false),
    "blend" to JsonPrimitive("NORMAL"),
    "opacity" to JsonPrimitive(1.0),
    "mapping" to JsonObject(mapOf(
        "mode" to JsonPrimitive("FILL"),
        "scale" to JsonPrimitive(1.0),
        "rotation" to JsonPrimitive(0.0),
        "lockOffset" to JsonPrimitive(false),
    )),
    "matte" to JsonNull,
))
