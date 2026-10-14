package dev.iconoclast.render

/**
 * The Glass look, schema 8: a frosted, see-through tile in the manner of macOS glass, built
 * from ordinary layers so every part stays editable. Applied to a theme as one change:
 *
 * - the background layer at [BACKGROUND_OPACITY], so the wallpaper shows through the tile,
 *   and a thin bright border as the glass rim;
 * - a [Effect.Blur] just above the background, behind the icon only, which frosts any image
 *   or pattern the background has. A pack icon is a still image: it cannot blur the
 *   wallpaper, only this theme's own layers;
 * - the icon fill and lines at [ICON_OPACITY];
 * - on top, a soft raised edge with a bright lit side, a white inner glow along the icon's
 *   edges, and light grain.
 *
 * [newId] names the added effect layers.
 */
object Glass {
    /**
     * Schema 9: Glass as a fill's starting point, the editor's Glass button on any fill. The
     * layer at [FILL_OPACITY], so 55% see-through, frosted, grained, with a light rim. The
     * look below, [apply], is no longer offered; themes that used it keep its layers.
     */
    val FILL_FINISH = Finish(frost = 0.04f, grain = 0.35f, rim = 0.6f)
    const val FILL_OPACITY = 0.45f

    const val BACKGROUND_OPACITY = 0.3f
    const val ICON_OPACITY = 0.55f

    val BLUR = Effect.Blur(radius = 0.04f, clip = ClipTarget.ICON)
    val EDGE = Effect.Bevel(BevelStyle.INNER, lightAngle = 110f, depth = 1.2f, size = 0.014f, softness = 0.7f,
        highlight = 0xE6FFFFFF.toInt(), highlightBlend = BlendMode.SCREEN, shadow = 0x40000000, shadowBlend = BlendMode.MULTIPLY)
    val RIM = Effect.InnerGlow(size = 0.012f, spread = 0.25f, color = 0xFFFFFFFF.toInt())
    val GRAIN = Effect.Grain(amount = 0.35f, size = 1f)
    val BORDER = Border(0.012f, 0xFFFFFFFF.toInt(), 0.5f)

    fun apply(theme: Theme, newId: () -> String): Theme {
        val faded = theme.layers.map { l ->
            when {
                l !is PaintLayer -> l
                l.id == Layer.BACKGROUND_ID -> l.copy(opacity = BACKGROUND_OPACITY)
                l.id == Layer.ICON_FILL_ID || l.id == Layer.ICON_LINES_ID -> l.copy(opacity = ICON_OPACITY)
                else -> l
            }
        }
        val bg = theme.background
        val withBlur = theme.copy(
            layers = faded,
            background = if (bg.enabled) bg.copy(border = BORDER) else bg,
        ).withEffect(EffectLayer(newId(), BLUR))
        return withBlur.copy(
            layers = withBlur.layers +
                EffectLayer(newId(), EDGE) +
                EffectLayer(newId(), RIM, opacity = 0.6f) +
                EffectLayer(newId(), GRAIN, blend = BlendMode.OVERLAY, opacity = 0.35f),
        )
    }
}
