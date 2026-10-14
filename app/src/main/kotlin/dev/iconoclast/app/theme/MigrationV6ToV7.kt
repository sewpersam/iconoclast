package dev.iconoclast.app.theme

import dev.iconoclast.render.Layer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Schema 6 to 7: the icon is one row. The editor shows the icon fill and icon lines
 * layers as a single Icon row, so nothing may sit between them. Layers a version 6 theme
 * stacked between the two move, in their order, to directly above the upper of the two.
 * Everything else, and the order of fill and lines themselves, stays. A theme missing
 * either icon layer has nothing between them and is unchanged apart from the version.
 *
 * A layer clipped to the icon fill renders the same above the lines wherever the fill and
 * the lines do not overlap, which in Cut-out they never do: the gap separates them.
 */
internal fun v6ToV7(v6: JsonObject, @Suppress("UNUSED_PARAMETER") now: () -> Long, @Suppress("UNUSED_PARAMETER") newId: () -> String): JsonObject {
    val theme = v6["theme"] as? JsonObject ?: throw ThemeFormatException("This theme is damaged: no theme.")
    val layers = theme["layers"] as? JsonArray ?: throw ThemeFormatException("This theme is damaged: no layers.")
    val upgraded = JsonArray(iconTogether(layers))
    return JsonObject(v6 + mapOf("schemaVersion" to JsonPrimitive(7), "theme" to JsonObject(theme + ("layers" to upgraded))))
}

/** [layers] with whatever sits between the two icon layers moved to just above them. */
internal fun iconTogether(layers: List<JsonElement>): List<JsonElement> {
    fun idOf(e: JsonElement) = ((e as? JsonObject)?.get("id") as? JsonPrimitive)?.content
    val a = layers.indexOfFirst { idOf(it) == Layer.ICON_FILL_ID || idOf(it) == Layer.ICON_LINES_ID }
    val b = layers.indexOfLast { idOf(it) == Layer.ICON_FILL_ID || idOf(it) == Layer.ICON_LINES_ID }
    if (a < 0 || b - a < 2 || idOf(layers[a]) == idOf(layers[b])) return layers
    val between = layers.subList(a + 1, b)
    return layers.subList(0, a + 1) + layers[b] + between + layers.subList(b + 1, layers.size)
}
