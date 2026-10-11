package dev.iconoclast.app.theme

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.util.UUID

/** The text is not a theme this version can read. [message] is shown to the user. */
class ThemeFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Parses a theme file of any known schema version, upgrading it step by step to
 * [ThemeDocument.CURRENT_SCHEMA]. Every load and every import goes through here.
 * [now] and [newId] fill fields that older versions lacked.
 */
fun migrate(
    json: String,
    now: () -> Long = System::currentTimeMillis,
    newId: () -> String = { UUID.randomUUID().toString() },
): ThemeDocument {
    var doc = try {
        ThemeDocument.JSON.parseToJsonElement(json) as? JsonObject
    } catch (e: SerializationException) {
        null
    } ?: throw ThemeFormatException("This file is not a theme.")

    val version = when (val v = doc["schemaVersion"]) {
        null -> 0
        else -> (v as? JsonPrimitive)?.intOrNull ?: throw ThemeFormatException("This file is not a theme.")
    }
    if (version > ThemeDocument.CURRENT_SCHEMA) {
        throw ThemeFormatException("This theme was made by a newer version of the app. Update the app to open it.")
    }
    if (version < 0) throw ThemeFormatException("This file is not a theme.")
    for (from in version until ThemeDocument.CURRENT_SCHEMA) doc = STEPS[from](doc, now, newId)

    return try {
        ThemeDocument.JSON.decodeFromJsonElement(ThemeDocument.serializer(), doc)
    } catch (e: SerializationException) {
        throw ThemeFormatException("This theme is damaged: ${e.message}", e)
    } catch (e: IllegalArgumentException) {
        throw ThemeFormatException("This theme is damaged: ${e.message}", e)
    }
}

private typealias Step = (JsonObject, () -> Long, () -> String) -> JsonObject

/** STEPS[n] upgrades version n to n + 1. */
private val STEPS: List<Step> = listOf(::v0ToV1, ::v1ToV2, ::v2ToV3)

/**
 * Version 0 is synthetic, written before any release to exercise this mechanism: no
 * schemaVersion, id or timestamps, "title" for the name, and "stroke" for the stroke weight.
 */
private fun v0ToV1(v0: JsonObject, now: () -> Long, newId: () -> String): JsonObject {
    val t = now()
    val theme = v0["theme"] as? JsonObject ?: throw ThemeFormatException("This file is not a theme.")
    val shape = (theme["shape"] as? JsonObject)?.renamed("stroke", "strokeWeight")
    val out = v0.renamed("title", "name").toMutableMap()
    out["schemaVersion"] = JsonPrimitive(1)
    out["id"] = JsonPrimitive(newId())
    out["created"] = JsonPrimitive(t)
    out["modified"] = JsonPrimitive(t)
    out["theme"] = if (shape == null) theme else JsonObject(theme + ("shape" to shape))
    return JsonObject(out)
}

private fun JsonObject.renamed(from: String, to: String): JsonObject {
    if (from !in this) return this
    return JsonObject(entries.associate { (k, v) -> (if (k == from) to else k) to v })
}
