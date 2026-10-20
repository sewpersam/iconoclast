package dev.iconoclast.render.background

import dev.iconoclast.render.BackgroundParams
import dev.iconoclast.render.BackgroundShape
import dev.iconoclast.render.FillMode
import dev.iconoclast.render.FillParams
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.ShapeParams
import dev.iconoclast.render.Theme
import dev.iconoclast.render.format.IconBundle
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Schema 11: the icon is laid out on the background shape's square, so a smaller shape
 * draws a smaller icon and the default icon is never trimmed by the shape.
 */
class IconFollowsBackgroundTest {

    private val bundle by lazy {
        IconBundle(ByteBuffer.wrap(requireNotNull(javaClass.getResourceAsStream("/golden/samples.bin")).readBytes()))
    }

    private fun icon(name: String) = bundle.icon(GoldenSet.SAMPLES.indexOf(name).also { check(it >= 0) })

    private val names = listOf("fdroid", "camera", "google_chrome", "signal", "spotify", "weather")

    private fun at(size: Float, shape: BackgroundShape = BackgroundShape.Squircle(), fill: FillMode = FillMode.OUTLINE) =
        Theme(background = BackgroundParams(shape = shape, size = size), fill = FillParams(mode = fill))

    /**
     * The icon's lines at half a 256 canvas are, pixel for pixel, the lines of a whole 128
     * canvas, centred: the icon's box shrinks with the shape. Offsets follow too.
     */
    @Test
    fun theIconAtHalfIsTheWholeIconAtHalfTheCanvas() {
        val r = IconRenderer()
        for (n in names) for (shape in listOf(ShapeParams(), ShapeParams(iconScale = 1.2f, offsetX = 0.08f, offsetY = -0.04f))) {
            val half = r.glyphMasks(n, icon(n), at(0.5f).copy(shape = shape), 256).lines!!
            val whole = r.glyphMasks(n, icon(n), at(1f).copy(shape = shape), 128).lines!!
            var worst = 0f
            var sum = 0f
            for (y in 0 until 128) for (x in 0 until 128) {
                val w = whole.values[y * 128 + x]
                sum += w
                // Half's centred 128 square spans 64 to 192 of 256; it is the whole 128
                // canvas drawn at the same scale, so pixel (x, y) maps to (x + 64, y + 64).
                worst = maxOf(worst, abs(half.values[(y + 64) * 256 + x + 64] - w))
            }
            assertTrue(sum > 50f, "$n draws")
            assertTrue(worst < 0.02f, "$n $shape differs by $worst")
        }
    }

    /** At the default icon size the shape trims nothing, at every size and shape. */
    @Test
    fun theDefaultIconIsNeverTrimmed() {
        val r = IconRenderer()
        val shapes = listOf(BackgroundShape.Squircle(), BackgroundShape.Circle, BackgroundShape.RoundedSquare(0.2f), BackgroundShape.Teardrop)
        for (n in names) for (shape in shapes) for (s in listOf(1f, 0.85f, 0.7f, 0.5f)) for (fill in FillMode.entries) {
            val t = at(s, shape, fill)
            val input = r.stackInput(n, icon(n), n, t, 192)
            val m = r.iconMask(n, icon(n), t, 192)!!
            val clip = input.shape!!
            var lost = 0f
            for (i in 0 until clip.pixels) lost += m.values[i] * (1f - clip.values[i])
            assertTrue(lost < 0.5f, "$n $shape $s $fill loses $lost px")
        }
    }

    /** With the background off, its size changes nothing: the square is the canvas. */
    @Test
    fun backgroundOffKeepsTheCanvas() {
        val r = IconRenderer()
        val off = Theme(background = BackgroundParams(enabled = false, size = 0.5f))
        val full = Theme(background = BackgroundParams(enabled = false))
        for (n in names) assertContentEquals(r.render(n, icon(n), full, 128).pixels, IconRenderer().render(n, icon(n), off, 128).pixels, n)
    }

    /** One renderer drawing both sizes keeps them apart: the frame is in every mask key. */
    @Test
    fun theCacheKeepsSizesApart() {
        val shared = IconRenderer()
        for (n in names) for (fill in FillMode.entries) {
            val a = shared.render(n, icon(n), at(1f, fill = fill), 96).pixels
            val b = shared.render(n, icon(n), at(0.6f, fill = fill), 96).pixels
            assertContentEquals(IconRenderer().render(n, icon(n), at(0.6f, fill = fill), 96).pixels, b, "$n $fill")
            assertTrue(!a.contentEquals(b), "$n $fill")
        }
        assertEquals(1f, IconRenderer.iconFrame(Theme()))
        assertEquals(0.6f, IconRenderer.iconFrame(at(0.6f)))
    }
}
