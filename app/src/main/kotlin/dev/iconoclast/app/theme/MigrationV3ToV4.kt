package dev.iconoclast.app.theme

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Schema 3 to 4: per-icon overrides. Version 4 adds `overrides`, a map from appfilter
 * drawable name to an [IconOverride]; a version 3 document has none, so the step adds an
 * empty map and bumps the version. A pure rewrite: the theme is untouched and renders the
 * same bytes, which ThemeMigrationTest checks against the starter goldens.
 */
internal fun v3ToV4(v3: JsonObject, @Suppress("UNUSED_PARAMETER") now: () -> Long, @Suppress("UNUSED_PARAMETER") newId: () -> String): JsonObject {
    if (v3["theme"] !is JsonObject) throw ThemeFormatException("This theme is damaged: no theme.")
    if ("overrides" in v3) throw ThemeFormatException("This theme is damaged: version 3 has no overrides.")
    return JsonObject(v3 + mapOf("schemaVersion" to JsonPrimitive(4), "overrides" to JsonObject(emptyMap())))
}
