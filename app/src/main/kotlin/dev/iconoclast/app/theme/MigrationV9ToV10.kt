package dev.iconoclast.app.theme

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull

/**
 * Schema 9 to 10, the background Size control. The shape may now shrink to half the tile,
 * and the background layer's own gradient or image is laid out on the shape's square
 * instead of the canvas, so it shrinks with the shape.
 *
 * A schema 9 file at size 1, or with a solid or pattern background, changes only its
 * version and renders the same bytes. Below 1, the background layer's paint is rewritten
 * so it looks as it did on the canvas:
 * - a gradient's points move into the shape's frame, `(p - 0.5) / size + 0.5`, and a
 *   radius becomes `radius / size`: the same colour at every pixel, up to float rounding;
 * - an image's scale becomes `scale / size`, Cover's first raised to 1, capped at 4: the
 *   same zoom. A centred patch maps exactly; a randomly placed one draws its patch again
 *   within the room the smaller frame leaves.
 */
internal fun v9ToV10(v9: JsonObject, @Suppress("UNUSED_PARAMETER") now: () -> Long, @Suppress("UNUSED_PARAMETER") newId: () -> String): JsonObject {
    val version = JsonPrimitive(10)
    val theme = v9["theme"] as? JsonObject ?: throw ThemeFormatException("This theme is damaged: no theme.")
    val bg = theme["background"] as? JsonObject ?: throw ThemeFormatException("This theme is damaged: no background.")
    val f = (bg["size"] as? JsonPrimitive)?.floatOrNull ?: throw ThemeFormatException("This theme is damaged: no background size.")
    if (f == 1f) return JsonObject(v9 + ("schemaVersion" to version))
    val layers = theme["layers"] as? JsonArray ?: throw ThemeFormatException("This theme is damaged: no layers.")
    val out = layers.map { l ->
        val layer = l as? JsonObject ?: throw ThemeFormatException("This theme is damaged: a layer is not an object.")
        if (layer.str("type") != "paint" || layer.str("id") != "background") layer else intoFrame(layer, f)
    }
    return JsonObject(v9 + mapOf("schemaVersion" to version, "theme" to JsonObject(theme + ("layers" to JsonArray(out)))))
}

private fun intoFrame(layer: JsonObject, f: Float): JsonObject {
    val src = layer["source"] as? JsonObject ?: throw ThemeFormatException("This theme is damaged: a layer has no paint.")
    fun p(k: String) = (src[k] as? JsonPrimitive)?.floatOrNull ?: throw ThemeFormatException("This theme is damaged: a gradient has no $k.")
    fun point(k: String) = k to JsonPrimitive((p(k) - 0.5f) / f + 0.5f)
    return when (src.str("type")) {
        "linear" -> JsonObject(layer + ("source" to JsonObject(src + mapOf(point("x0"), point("y0"), point("x1"), point("y1")))))
        "radial" -> JsonObject(layer + ("source" to JsonObject(src + mapOf(point("cx"), point("cy"), "radius" to JsonPrimitive(p("radius") / f)))))
        "angular" -> JsonObject(layer + ("source" to JsonObject(src + mapOf(point("cx"), point("cy")))))
        "texture" -> {
            val m = layer["mapping"] as? JsonObject ?: throw ThemeFormatException("This theme is damaged: a layer has no mapping.")
            val scale = (m["scale"] as? JsonPrimitive)?.floatOrNull ?: throw ThemeFormatException("This theme is damaged: a mapping has no scale.")
            val base = if (m.str("mode") == "FILL") maxOf(scale, 1f) else scale
            JsonObject(layer + ("mapping" to JsonObject(m + ("scale" to JsonPrimitive(minOf(MAX_MAPPING_SCALE, base / f))))))
        }
        else -> layer
    }
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private const val MAX_MAPPING_SCALE = 4f
