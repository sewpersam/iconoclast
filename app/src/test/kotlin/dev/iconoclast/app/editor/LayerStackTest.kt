package dev.iconoclast.app.editor

import dev.iconoclast.app.theme.ThemeDocument
import dev.iconoclast.app.theme.migrate
import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.ClipTarget
import dev.iconoclast.render.Effect
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.Layer
import dev.iconoclast.render.Mapping
import dev.iconoclast.render.MappingMode
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Theme
import dev.iconoclast.render.texture.BuiltInBrushes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LayerStackTest {
    private val base = Theme()
    private fun ids(t: Theme) = t.layers.map { it.id }

    @Test
    fun insertMoveRemove() {
        val l = LayerStack.newLayer(Paint.Solid(-1))
        val t = LayerStack.insertAbove(base, l, Layer.EFFECTS_ID)
        assertEquals(listOf("background", "effects", l.id, "icon-fill", "icon-lines"), ids(t))
        assertEquals(listOf("background", "effects", l.id, "icon-fill", "icon-lines", "x"),
            ids(LayerStack.insertAbove(t, l.copy(id = "x"), null)))
        assertEquals(listOf("background", "effects", "icon-fill", l.id, "icon-lines"), ids(LayerStack.move(t, l.id, up = true)))
        assertEquals(listOf("background", l.id, "effects", "icon-fill", "icon-lines"), ids(LayerStack.move(t, l.id, up = false)))
        assertSame(base, LayerStack.move(base, "icon-lines", up = true))
        assertSame(base, LayerStack.move(base, "background", up = false))
        assertEquals(ids(base), ids(LayerStack.remove(t, l.id)))
        assertSame(base, LayerStack.remove(base, Layer.EFFECTS_ID))
        // The background and the icon only hide: the list offers no delete for them.
        for (id in listOf(Layer.BACKGROUND_ID, Layer.ICON_FILL_ID, Layer.ICON_LINES_ID, LayerStack.ICON_ROW)) {
            assertSame(base, LayerStack.remove(base, id))
            assertEquals(false, LayerStack.canRemove(base, id))
        }
        assertEquals(true, LayerStack.canRemove(t, l.id))
        assertEquals(2f, LayerStack.newLayer(Paint.Texture(GoldenSet.TEXTURE)).mapping.scale, "room for a patch per icon")
        assertEquals(1f, LayerStack.newLayer(Paint.Solid(-1)).mapping.scale)
    }

    /** Spec 1.2: the icon's fill and lines are one row; above it means above both. */
    @Test
    fun theIconIsOneRow() {
        assertEquals(listOf(LayerStack.ICON_ROW, "background"), LayerStack.rows(base).map { it.id })
        assertEquals(listOf("icon-fill", "icon-lines"), LayerStack.rows(base).first().layers.map { it.id })
        val l = LayerStack.newLayer(Paint.Solid(-1))
        assertEquals(listOf("background", "effects", "icon-fill", "icon-lines", l.id), ids(LayerStack.insertAbove(base, l, LayerStack.ICON_ROW)))
        assertEquals(listOf("background", "effects", "icon-fill", "icon-lines", l.id), ids(LayerStack.insertAbove(base, l, "icon-fill")))
        // Hiding the row hides both its layers.
        val hidden = LayerStack.withVisible(base, LayerStack.ICON_ROW, visible = false)
        assertEquals(listOf(false, false), LayerStack.rowLayers(hidden, LayerStack.ICON_ROW).map { (it as PaintLayer).visible })
        assertEquals("Hidden", rowSummary(hidden, LayerStack.rows(hidden).first()))
        assertEquals("Lines only", rowSummary(base, LayerStack.rows(base).first()))
        assertEquals("Icon", LayerStack.rowLabel(base, LayerStack.rows(base).first()))
    }

    /** Nothing can be dropped between the icon's fill and lines: a drag past the Icon row jumps it whole. */
    @Test
    fun noDropBetweenIconFillAndLines() {
        val l = LayerStack.newLayer(Paint.Solid(-1))
        val top = LayerStack.insertAbove(base, l, null)
        // Down from the top onto the Icon row: lands below both.
        assertEquals(listOf("background", "effects", l.id, "icon-fill", "icon-lines"), ids(LayerStack.moveOnto(top, l.id, LayerStack.ICON_ROW)))
        val below = LayerStack.moveOnto(top, l.id, LayerStack.ICON_ROW)
        // Up again: lands above both.
        assertEquals(ids(top), ids(LayerStack.moveOnto(below, l.id, LayerStack.ICON_ROW)))
        // Either icon layer alone is not a row and cannot be a target or be moved.
        assertSame(top, LayerStack.moveOnto(top, l.id, "icon-fill"))
        assertSame(top, LayerStack.moveOnto(top, "icon-lines", l.id))
        // The Icon row moves as one.
        assertEquals(listOf("icon-fill", "icon-lines", "background", "effects", l.id), ids(LayerStack.moveOnto(top, LayerStack.ICON_ROW, "background")))
    }

    @Test
    fun labelsNumberLayersOfOneKind() {
        val a = PaintLayer("a", Paint.Texture(GoldenSet.TEXTURE))
        val b = PaintLayer("b", Paint.Texture(GoldenSet.TEXTURE))
        val t = base.copy(layers = base.layers + a + b)
        assertEquals("Image 1", LayerStack.label(t, a))
        assertEquals("Image 2", LayerStack.label(t, b))
        assertEquals("Veins", LayerStack.label(t, PaintLayer("v", Paint.Noise(dev.iconoclast.render.NoiseKind.RIDGED))))
        assertEquals("Icon fill", LayerStack.label(t, t.layers[2]))
    }

    @Test
    fun everyBlendModeHasItsPhotoshopName() {
        assertEquals(BlendMode.entries.toList(), BLEND_NAMES.keys.toList())
        assertEquals("Add", BLEND_NAMES[BlendMode.ADD])
        assertEquals("Normal", BLEND_NAMES[BlendMode.NORMAL])
        assertEquals(ClipTarget.entries.toSet(), CLIP_NAMES.keys)
        assertEquals("Effects below", clipName(ClipTarget.EFFECTS))
        assertEquals(BlendMode.entries.toSet(), BLEND_LINES.keys)
    }

    @Test
    fun switchingToATextureStartsAtTheNewTextureMapping() {
        val a = "a".repeat(64)
        val b = "b".repeat(64)
        val solid = LayerStack.newLayer(Paint.Solid(0))
        assertEquals(LayerStack.NEW_TEXTURE_MAPPING, LayerStack.withTexture(solid, a).mapping)
        val tex = LayerStack.withTexture(solid, a).copy(mapping = Mapping(MappingMode.TILE, scale = 3f))
        val swapped = LayerStack.withTexture(tex, b)
        assertEquals(Paint.Texture(b) to tex.mapping, swapped.source to swapped.mapping)
        // The page path: a remembered image switched back to keeps the image placement start.
        assertEquals(LayerStack.NEW_TEXTURE_MAPPING, withSource(solid, Paint.Texture(a)).mapping)
        assertEquals(tex.mapping, withSource(tex, Paint.Texture(b)).mapping)
    }

    /**
     * The editor's path for a built-in brush on the M6 Jade background, Layers tab: the
     * background layer takes the brush at the new-texture mapping, and its gradient map
     * then gives exactly the golden look. The Background tab's texture layer takes it too.
     */
    @Test
    fun aBuiltInBrushPaintsTheBackground() {
        val brush = BuiltInBrushes.BRISTLE.sha256
        val jade = GoldenSet.JADE_M6
        val bg = LayerStack.layer(jade, Layer.BACKGROUND_ID) as PaintLayer
        val brushed = LayerStack.withTexture(bg, brush)
        assertEquals(Paint.Texture(brush) to LayerStack.NEW_TEXTURE_MAPPING, brushed.source to brushed.mapping)
        val golden = LayerStack.layer(GoldenSet.BRUSH_BACKGROUND, Layer.BACKGROUND_ID) as PaintLayer
        val mapped = brushed.copy(source = Paint.Texture(brush, (golden.source as Paint.Texture).stops))
        assertEquals(GoldenSet.BRUSH_BACKGROUND, LayerStack.replace(jade, mapped))
    }

    @Test
    fun clippingToTheWholeCanvasDropsTheInverse() {
        val l = LayerStack.newLayer(Paint.Solid(0)).copy(clipInverse = true)
        assertEquals(ClipTarget.CANVAS to false, LayerStack.withClip(l, ClipTarget.CANVAS).let { it.clip to it.clipInverse })
        assertEquals(ClipTarget.BACKGROUND to true, LayerStack.withClip(l, ClipTarget.BACKGROUND).let { it.clip to it.clipInverse })
    }

    /** Task 9's placement: effects outside the icon go below it, effects on it on top. */
    @Test
    fun newEffectsGoWhereTheyShow() {
        val streaks = LayerStack.newEffect(base, Effect.Streaks())
        val glow = LayerStack.newEffect(base, Effect.OuterGlow())
        val bevel = LayerStack.newEffect(base, Effect.Bevel())
        val grain = LayerStack.newEffect(base, Effect.Grain())
        var t = LayerStack.insertEffect(base, streaks)
        t = LayerStack.insertEffect(t, glow)
        t = LayerStack.insertEffect(t, bevel)
        t = LayerStack.insertEffect(t, grain)
        assertEquals(listOf("background", "effects", streaks.id, glow.id, "icon-fill", "icon-lines", bevel.id, grain.id), ids(t))
        assertEquals(false, LayerStack.aboveIcon(t, streaks))
        assertEquals(true, LayerStack.aboveIcon(t, bevel))
        assertEquals(BlendMode.OVERLAY, grain.blend)
        assertEquals("Streaks", LayerStack.label(t, streaks))
        assertEquals("Raised edge", LayerStack.label(t, bevel))
        val two = LayerStack.insertEffect(t, LayerStack.newEffect(t, Effect.Grain()))
        assertEquals("Grain 1", LayerStack.label(two, grain))
        assertEquals(ids(t) - grain.id, ids(LayerStack.remove(t, grain.id)))
    }

    @Test
    fun newStreaksTakeTheFirstTextureOrNoiseLayer() {
        assertEquals(null, (LayerStack.newEffect(base, Effect.Streaks()).effect as Effect.Streaks).paintLayer)
        val noise = LayerStack.newLayer(Paint.Noise())
        val t = LayerStack.insertAbove(base, noise, null)
        assertEquals(listOf(noise), LayerStack.streakSources(t))
        assertEquals(noise.id, (LayerStack.newEffect(t, Effect.Streaks()).effect as Effect.Streaks).paintLayer)
    }

    /** Brush strokes go below the icon, under their own name, and paint from the first texture or noise layer. */
    @Test
    fun newBrushStrokesGoBelowTheIconAndTakeTheFirstSource() {
        val bare = LayerStack.newEffect(base, Effect.BrushStrokes())
        assertEquals(null, (bare.effect as Effect.BrushStrokes).paintLayer)
        val t0 = LayerStack.insertEffect(base, bare)
        assertEquals(listOf("background", "effects", bare.id, "icon-fill", "icon-lines"), ids(t0))
        assertEquals("Brush strokes", LayerStack.label(t0, bare))
        val noise = LayerStack.newLayer(Paint.Noise())
        val t = LayerStack.insertAbove(base, noise, null)
        assertEquals(noise.id, (LayerStack.newEffect(t, Effect.BrushStrokes()).effect as Effect.BrushStrokes).paintLayer)
    }

    @Test
    fun dragMovesOntoTheNeighbourAndSkipsTheHiddenMarker() {
        // Rows, top first, leave the effects marker out.
        assertEquals(listOf(LayerStack.ICON_ROW, "background"), LayerStack.rows(base).map { it.id })
        // Down from the icon onto the background: past the marker, which stays put.
        assertEquals(listOf("icon-fill", "icon-lines", "background", "effects"), ids(LayerStack.moveOnto(base, LayerStack.ICON_ROW, "background")))
        // Up: lands just above the target.
        assertEquals(listOf("effects", "icon-fill", "icon-lines", "background"), ids(LayerStack.moveOnto(base, "background", LayerStack.ICON_ROW)))
        assertSame(base, LayerStack.moveOnto(base, LayerStack.ICON_ROW, LayerStack.ICON_ROW))
        assertSame(base, LayerStack.moveOnto(base, "gone", LayerStack.ICON_ROW))
    }

    @Test
    fun theEyeHidesAndShowsWithoutTouchingOpacity() {
        val l = LayerStack.newLayer(Paint.Solid(-1)).copy(opacity = 0.4f)
        val t = LayerStack.insertAbove(base, l, null)
        val hidden = LayerStack.withVisible(t, l.id, visible = false)
        val h = LayerStack.layer(hidden, l.id) as PaintLayer
        assertEquals(false, h.visible)
        assertEquals(0.4f, h.opacity)
        assertEquals(false, LayerStack.isVisible(h))
        assertEquals("Hidden", rowSummary(hidden, LayerStack.Row(h.id, listOf(h))))
        // A save and a reload, an app restart, keep both the flag and the opacity.
        val doc = ThemeDocument(id = "a", name = "A", created = 1, modified = 2, theme = hidden)
        val reloaded = migrate(doc.toJson()).theme
        assertEquals(hidden, reloaded)
        val shown = LayerStack.withVisible(reloaded, l.id, visible = true)
        assertEquals(t, shown)
        assertEquals(0.4f, (LayerStack.layer(shown, l.id) as PaintLayer).opacity)
        assertSame(t, LayerStack.withVisible(t, l.id, visible = true))
        // Zero opacity is a value, not hidden.
        assertEquals(true, LayerStack.isVisible(l.copy(opacity = 0f)))
        val shadow = LayerStack.newEffect(base, Effect.DropShadow())
        val e = LayerStack.insertEffect(base, shadow)
        val off = LayerStack.withVisible(e, shadow.id, visible = false)
        assertEquals(false, (LayerStack.layer(off, shadow.id) as dev.iconoclast.render.EffectLayer).enabled)
        assertEquals(e, LayerStack.withVisible(off, shadow.id, visible = true))
        assertSame(base, LayerStack.withVisible(base, Layer.EFFECTS_ID, visible = false))
    }

    @Test
    fun summariesArePlainWords() {
        fun summary(t: Theme, id: String) = rowSummary(t, LayerStack.rows(t).first { it.id == id })
        val tex = PaintLayer("t", Paint.Texture(GoldenSet.TEXTURE), clip = ClipTarget.ICON, clipInverse = true,
            blend = BlendMode.SCREEN, opacity = 0.5f, matte = dev.iconoclast.render.Matte())
        val fillTint = PaintLayer("f", Paint.Solid(-1), clip = ClipTarget.ICON_FILL)
        val t = base.copy(layers = base.layers + tex + fillTint + PaintLayer("c", Paint.Solid(-1)))
        assertEquals("Outside whole icon · cut out by brightness · Screen · 50%", summary(t, "t"))
        assertEquals("Inside icon fill", summary(t, "f"))
        assertEquals("Whole tile", summary(t, "c"))
        assertEquals("Soft square", summary(t, "background"))
        val shadow = LayerStack.newEffect(base, Effect.DropShadow())
        val s = LayerStack.insertEffect(base, shadow)
        assertEquals("Behind the icon", summary(s, shadow.id))
        val over = base.copy(layers = base.layers + shadow.copy(opacity = 0.35f))
        assertEquals("Over the icon · 35%", summary(over, shadow.id))
        // Spec 4.3: an effect that only draws on the icon, below it, is hidden and says so.
        val bevel = LayerStack.newEffect(base, Effect.Bevel())
        val under = base.copy(layers = listOf(base.layers[0], base.layers[1], bevel) + base.layers.drop(2))
        assertEquals("Hidden behind the icon", summary(under, bevel.id))
        assertEquals("Off", summary(base.copy(background = base.background.copy(enabled = false)), "background"))
    }
}
