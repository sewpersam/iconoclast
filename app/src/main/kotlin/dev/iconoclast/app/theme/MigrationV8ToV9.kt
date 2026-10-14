package dev.iconoclast.app.theme

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int

/**
 * Schema 8 to 9, phone feedback round 1. Every change renders the same bytes:
 *
 * - A `noise` paint becomes a `pattern` paint: fBm and ridged are Noise at size 1/8, detail
 *   octaves - 1, roughness 0.5, ridged with `ridges`; grain is pixel-size Cells.
 *   `Pattern.legacy` in :render draws them operation for operation as before.
 * - A texture paint gains `adjust` null; every paint layer gains a zero `finish`.
 * - Brush strokes gain `direction` OUTWARD, origin 0.5, 0.5 and `style` CLASSIC: schema 8's strokes.
 * - Clip to the layer below: a paint layer other than the base stack's own, not inverted,
 *   whose clip is exactly what BELOW resolves to where it sits becomes BELOW, so it follows
 *   a drag from now on. BELOW resolves to ICON above the icon row, and to the clip of a
 *   paint layer without a matte below it. Everything else keeps its explicit target, which
 *   renders as before: CANVAS is the switch's off, the rest are shown as an older setting.
 */
internal fun v8ToV9(v8: JsonObject, @Suppress("UNUSED_PARAMETER") now: () -> Long, @Suppress("UNUSED_PARAMETER") newId: () -> String): JsonObject {
    val theme = v8["theme"] as? JsonObject ?: throw ThemeFormatException("This theme is damaged: no theme.")
    val layers = theme["layers"] as? JsonArray ?: throw ThemeFormatException("This theme is damaged: no layers.")
    val out = ArrayList<JsonObject>(layers.size)
    for (l in layers) {
        val layer = l as? JsonObject ?: throw ThemeFormatException("This theme is damaged: a layer is not an object.")
        out += when (layer.str("type")) {
            "paint" -> {
                var p = layer + mapOf("source" to source(layer["source"]), "finish" to FINISH)
                if (layer.str("id") !in BASE && layer.bool("clipInverse") == false && layer.str("clip") != "CANVAS" &&
                    layer.str("clip") == resolvedBelow(out)) p = p + ("clip" to JsonPrimitive("BELOW"))
                JsonObject(p)
            }
            "effect" -> {
                val e = layer["effect"] as? JsonObject
                if (e?.str("type") == "brushStrokes") JsonObject(layer + ("effect" to JsonObject(e + STROKES))) else layer
            }
            else -> layer
        }
    }
    return JsonObject(v8 + mapOf("schemaVersion" to JsonPrimitive(9), "theme" to JsonObject(theme + ("layers" to JsonArray(out)))))
}

/** The clip BELOW gives a layer placed on top of [done], as a schema 8 target name, or null when it has none. */
private fun resolvedBelow(done: List<JsonObject>): String? {
    val below = done.lastOrNull { it.str("type") != "effects" } ?: return null
    if (below.str("id") in ICON) return "ICON"
    if (below.str("type") != "paint" || below["matte"] !is JsonNull || below.bool("clipInverse") != false) return null
    val clip = below.str("clip")
    return if (clip == "BELOW") resolvedBelow(done.subList(0, done.indexOf(below))) else clip
}

private fun source(s: JsonElement?): JsonElement {
    val src = s as? JsonObject ?: throw ThemeFormatException("This theme is damaged: a layer has no paint.")
    return when (src.str("type")) {
        "texture" -> JsonObject(src + ("adjust" to JsonNull))
        "noise" -> {
            val kind = src.str("kind")
            val octaves = (src["octaves"] as? JsonPrimitive)?.let { runCatching { it.int }.getOrNull() }
                ?: throw ThemeFormatException("This theme is damaged: a noise has no octaves.")
            if (octaves !in 1..8) throw ThemeFormatException("This theme is damaged: noise octaves $octaves.")
            val pattern = when (kind) {
                "FBM", "RIDGED" -> JsonObject(mapOf(
                    "type" to JsonPrimitive("noise"), "size" to JsonPrimitive(0.125), "detail" to JsonPrimitive((octaves - 1).toDouble()),
                    "roughness" to JsonPrimitive(0.5), "distortion" to JsonPrimitive(0.0), "stretch" to JsonPrimitive(1.0),
                    "stretchAngle" to JsonPrimitive(0.0), "ridges" to JsonPrimitive(kind == "RIDGED"),
                ))
                "GRAIN" -> JsonObject(mapOf(
                    "type" to JsonPrimitive("cells"), "size" to JsonPrimitive(0.0), "randomness" to JsonPrimitive(0.0),
                    "edgeWidth" to JsonPrimitive(0.1), "feature" to JsonPrimitive("CELLS"),
                ))
                else -> throw ThemeFormatException("This theme is damaged: noise kind $kind.")
            }
            JsonObject(mapOf("type" to JsonPrimitive("pattern"), "pattern" to pattern, "seed" to (src["seed"] ?: JsonPrimitive(0)),
                "stops" to (src["stops"] ?: throw ThemeFormatException("This theme is damaged: a noise has no stops."))))
        }
        else -> src
    }
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.let { runCatching { it.boolean }.getOrNull() }

private val BASE = setOf("background", "icon-fill", "icon-lines")
private val ICON = setOf("icon-fill", "icon-lines")
private val FINISH = JsonObject(mapOf("frost" to JsonPrimitive(0.0), "grain" to JsonPrimitive(0.0), "rim" to JsonPrimitive(0.0), "cut" to JsonPrimitive(0.0)))
private val STROKES = mapOf(
    "direction" to JsonPrimitive("OUTWARD"), "originX" to JsonPrimitive(0.5), "originY" to JsonPrimitive(0.5), "style" to JsonPrimitive("CLASSIC"),
)

