package dev.iconoclast.app.theme

import dev.iconoclast.render.MAX_ICON_SCALE
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull

/**
 * Schema 10 to 11, the icon follows the background. The icon's size and offset are now
 * measured against the background shape's square instead of the canvas, so shrinking the
 * shape shrinks the icon with it, [dev.iconoclast.render.IconRenderer.iconFrame].
 *
 * A schema 10 file at size 1, or with the background off, changes only its version and
 * renders the same bytes. Below 1, the shape's numbers are rewritten so the icon keeps its
 * size and place on the tile: `iconScale / size` and `offset / size`, up to float rounding.
 * Both are capped at their ranges: an icon wider than the tile's whole canvas at that
 * size, or an offset past 10% of the new frame, lands on the edge of its range. Both
 * cases were already trimmed by the shape.
 */
internal fun v10ToV11(v10: JsonObject, @Suppress("UNUSED_PARAMETER") now: () -> Long, @Suppress("UNUSED_PARAMETER") newId: () -> String): JsonObject {
    val version = JsonPrimitive(11)
    val theme = v10["theme"] as? JsonObject ?: throw ThemeFormatException("This theme is damaged: no theme.")
    val bg = theme["background"] as? JsonObject ?: throw ThemeFormatException("This theme is damaged: no background.")
    val f = (bg["size"] as? JsonPrimitive)?.floatOrNull ?: throw ThemeFormatException("This theme is damaged: no background size.")
    val enabled = (bg["enabled"] as? JsonPrimitive)?.booleanOrNull ?: throw ThemeFormatException("This theme is damaged: no background switch.")
    if (f == 1f || !enabled) return JsonObject(v10 + ("schemaVersion" to version))
    val shape = theme["shape"] as? JsonObject ?: throw ThemeFormatException("This theme is damaged: no shape.")
    fun p(k: String) = (shape[k] as? JsonPrimitive)?.floatOrNull ?: throw ThemeFormatException("This theme is damaged: the shape has no $k.")
    val moved = shape + mapOf(
        "iconScale" to JsonPrimitive((p("iconScale") / f).coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE)),
        "offsetX" to JsonPrimitive((p("offsetX") / f).coerceIn(-MAX_OFFSET, MAX_OFFSET)),
        "offsetY" to JsonPrimitive((p("offsetY") / f).coerceIn(-MAX_OFFSET, MAX_OFFSET)),
    )
    return JsonObject(v10 + mapOf("schemaVersion" to version, "theme" to JsonObject(theme + ("shape" to JsonObject(moved)))))
}

private const val MIN_ICON_SCALE = 0.5f
private const val MAX_OFFSET = 0.1f
