package dev.iconoclast.app.theme

import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.Theme

/*
 * The override resolver. The renderer and the pack builder never see an override: they
 * get the effective Theme and the geometry name these return, so cache keys follow the
 * effective values and a changed override misses the cache by itself.
 */

/** [drawable]'s theme with its override applied; the document's theme when it has none. */
fun ThemeDocument.effectiveTheme(drawable: String): Theme = overrides[drawable]?.applyTo(theme) ?: theme

/** [theme] with this override applied. Previews call it with the theme they show. */
fun IconOverride.applyTo(theme: Theme): Theme {
    val o = this
    var t = theme
    if (o.strokeWeight != null) t = t.copy(shape = t.shape.copy(strokeWeight = o.strokeWeight))
    if (o.fillMode != null) t = t.copy(fill = t.fill.copy(mode = o.fillMode))
    if (o.gapClosing != null) t = t.copy(fill = t.fill.copy(gapClosing = o.gapClosing))
    if (o.textureOffset != null) t = t.copy(textureOffset = o.textureOffset)
    if (o.hideStreaks) t = t.copy(layers = t.layers.filterNot { it is EffectLayer && (it.effect is Effect.Streaks || it.effect is Effect.BrushStrokes) })
    return t
}

/**
 * The drawable whose geometry [drawable]'s slot draws: its swap target, else itself. The
 * slot's seed name stays [drawable]. A swap target missing from the icon set is the
 * caller's to catch; [staleOverrides] lists it.
 */
fun ThemeDocument.effectiveGeometry(drawable: String): String = overrides[drawable]?.swapDrawable ?: drawable

/**
 * Overrides that no longer fit the pinned icon set: keyed by a drawable [known] does not
 * hold, or swapping to one. They stay in the document, so a later icon set that brings the
 * name back finds them; the coverage screen flags them. Sorted by drawable name.
 */
fun ThemeDocument.staleOverrides(known: (String) -> Boolean): List<String> =
    overrides.filter { (name, o) -> !known(name) || (o.swapDrawable != null && !known(o.swapDrawable)) }.keys.sorted()

/** This document with [drawable]'s override set to [override]; an empty or null one removes it, "reset to theme". */
fun ThemeDocument.withOverride(drawable: String, override: IconOverride?): ThemeDocument =
    copy(overrides = if (override == null || override.isEmpty) overrides - drawable else overrides + (drawable to override))
