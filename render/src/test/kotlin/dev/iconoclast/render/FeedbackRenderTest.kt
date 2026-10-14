package dev.iconoclast.render

import dev.iconoclast.render.format.IconBundle
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Phone feedback round 1, schema 9: what the goldens alone do not pin. */
class FeedbackRenderTest {
    private val bundle = IconBundle(ByteBuffer.wrap(javaClass.getResourceAsStream("/golden/samples.bin")!!.readBytes()))
    private val name = "camera"
    private val icon = bundle.icon(GoldenSet.SAMPLES.indexOf(name))
    private val renderer = IconRenderer().also { it.textures.put(Marble.PNG) }
    private fun render(t: Theme, size: Int = 128) = renderer.render(name, icon, t, size).pixels

    private val clouds = Paint.Noise(Pattern.Noise(detail = 3f), 4)
    private val base = Theme(fill = FillParams(mode = FillMode.INVERT), background = BackgroundParams(shape = BackgroundShape.Circle, size = 0.8f))

    private fun withAbove(id: String, layer: PaintLayer) =
        base.copy(layers = base.layers.flatMap { if (it.id == id) listOf(it, layer) else listOf(it) })

    @Test
    fun belowTheBackgroundIsTheBackgroundClip() {
        val below = withAbove(Layer.BACKGROUND_ID, PaintLayer("c", clouds, clip = ClipTarget.BELOW))
        val named = withAbove(Layer.BACKGROUND_ID, PaintLayer("c", clouds, clip = ClipTarget.BACKGROUND))
        assertContentEquals(render(named), render(below))
    }

    @Test
    fun aboveTheIconRowIsTheWholeIcon() {
        val below = withAbove(Layer.ICON_LINES_ID, PaintLayer("c", clouds, clip = ClipTarget.BELOW))
        val named = withAbove(Layer.ICON_LINES_ID, PaintLayer("c", clouds, clip = ClipTarget.ICON))
        assertContentEquals(render(named), render(below))
    }

    @Test
    fun belowAPaintLayerIsItsClipAndFollowsADrag() {
        val tint = PaintLayer("t", Paint.Solid(0xFFFF0000.toInt()), clip = ClipTarget.BELOW)
        // Above the clouds, which sit on the background: the background's shape.
        val stacked = withAbove(Layer.BACKGROUND_ID, PaintLayer("c", clouds, clip = ClipTarget.BACKGROUND)).let { t ->
            t.copy(layers = t.layers.flatMap { if (it.id == "c") listOf(it, tint) else listOf(it) })
        }
        val named = withAbove(Layer.BACKGROUND_ID, PaintLayer("c", clouds, clip = ClipTarget.BACKGROUND)).let { t ->
            t.copy(layers = t.layers.flatMap { if (it.id == "c") listOf(it, tint.copy(clip = ClipTarget.BACKGROUND)) else listOf(it) })
        }
        assertContentEquals(render(named), render(stacked))
        // Dragged to the bottom of the stack it has nothing below and paints nothing.
        val bottom = stacked.copy(layers = listOf(tint) + stacked.layers.filter { it.id != "t" })
        val without = stacked.copy(layers = stacked.layers.filter { it.id != "t" })
        assertContentEquals(render(without), render(bottom))
    }

    @Test
    fun coverStaysCoveringAtItsSmallestScale() {
        val t = base.copy(background = BackgroundParams(shape = BackgroundShape.Square), layers = base.layers.map {
            if (it.id == Layer.BACKGROUND_ID) (it as PaintLayer).copy(source = Paint.Texture(Marble.HASH),
                mapping = Mapping(scale = 0.25f, randomScale = 1f, randomRotation = 180f)) else it
        })
        for (n in listOf("camera", "fdroid", "spotify")) {
            val px = renderer.render(n, bundle.icon(GoldenSet.SAMPLES.indexOf(n)), t, 96).pixels
            for (i in 3 until px.size step 4) assertEquals(-1, px[i].toInt(), "$n: transparent pixel ${i / 4}")
        }
    }

    @Test
    fun linesCutThroughToTransparency() {
        val t = Theme(shape = ShapeParams(strokeWeight = 3f), background = BackgroundParams(shape = BackgroundShape.Square),
            layers = Theme.baseLayers(Paint.Solid(0xFF3050F0.toInt())).map {
                if (it.id == Layer.ICON_LINES_ID) (it as PaintLayer).copy(opacity = 0f, finish = Finish(cut = 1f)) else it
            })
        val px = render(t)
        val alphas = (3 until px.size step 4).map { px[it].toInt() and 0xFF }
        assertTrue(alphas.count { it == 0 } > 200, "the lines are holes")
        assertTrue(alphas.count { it == 255 } > 8000, "the background stays")
    }

    @Test
    fun legacyNoiseIsItsPattern() {
        assertEquals(Pattern.Noise(detail = 4f), Paint.Noise(NoiseKind.FBM, 5).pattern)
        assertEquals(Pattern.Noise(detail = 2f, ridges = true), Paint.Noise(NoiseKind.RIDGED, 3).pattern)
        assertEquals(Pattern.Cells(size = 0f, randomness = 0f), Paint.Noise(NoiseKind.GRAIN).pattern)
    }

    @Test
    fun adjustAtZeroChangesNothing() {
        fun bg(a: Adjust?) = base.copy(layers = base.layers.map {
            if (it.id == Layer.BACKGROUND_ID) (it as PaintLayer).copy(source = Paint.Texture(Marble.HASH, adjust = a)) else it
        })
        assertContentEquals(render(bg(null)), render(bg(Adjust())))
        assertTrue(!render(bg(null)).contentEquals(render(bg(Adjust(contrast = 0.5f)))))
    }

    @Test
    fun outwardStrokesFromTheCentreAreSchema8s() {
        val e = Effect.BrushStrokes(count = 40, length = 0.2f)
        fun t(x: Effect.BrushStrokes) = base.withEffect(EffectLayer("s", x))
        assertContentEquals(render(t(e)), render(t(e.copy(originX = 0.5f, originY = 0.5f, direction = StrokeDirection.OUTWARD))))
        assertTrue(!render(t(e)).contentEquals(render(t(e.copy(originX = 0.2f)))))
    }
}
