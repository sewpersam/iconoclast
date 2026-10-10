package dev.icondesigner.render

import dev.icondesigner.render.format.BundleFormat
import dev.icondesigner.render.format.IconBundle
import dev.icondesigner.render.png.PngEncoder
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Golden images: 24 sample icons in each fill mode, at the output size, compared pixel
 * for pixel after decoding. The goldens and the sample bundle live in
 * src/test/resources/golden. `./gradlew :render:test -PupdateGoldens` rewrites both from
 * the converted Arcticons bundle.
 */
class GoldenTest {

    private val update = System.getProperty("golden.update") == "true"

    private val bundle: IconBundle by lazy {
        if (update) writeSamples()
        IconBundle(ByteBuffer.wrap(resource("samples.bin")))
    }

    private fun resource(name: String): ByteArray =
        if (update) File(System.getProperty("golden.dir"), name).readBytes()
        else requireNotNull(javaClass.getResourceAsStream("/golden/$name")) { "missing golden/$name" }.readBytes()

    /** Copies the sample icons out of the full bundle, in [SAMPLES] order. */
    private fun writeSamples() {
        val source = File(System.getProperty("golden.source"))
        val names = File(source, "names.txt").readLines()
        val full = IconBundle(ByteBuffer.wrap(File(source, "icons.bin").readBytes()))
        val icons = SAMPLES.map { full.icon(names.indexOf(it).also { i -> check(i >= 0) { "no icon $it" } }) }
        File(System.getProperty("golden.dir"), "samples.bin").writeBytes(BundleFormat.encode(icons))
    }

    @Test
    fun goldens() {
        val renderer = IconRenderer()
        val mismatches = mutableListOf<String>()
        for ((mode, theme) in THEMES) for ((i, name) in SAMPLES.withIndex()) {
            val png = renderer.renderPng(i, bundle.icon(i), theme, SIZE)
            val path = "${mode.name.lowercase()}/$name.png"
            if (update) {
                File(System.getProperty("golden.dir"), path).apply { parentFile.mkdirs() }.writeBytes(png)
                continue
            }
            if (!decode(png).contentEquals(decode(resource(path)))) mismatches += path
        }
        if (mismatches.isNotEmpty()) fail("${mismatches.size} goldens differ: ${mismatches.take(10)}")
    }

    /**
     * The three icons spec 1.4 names. Each probe is a point on the 48 grid and what invert
     * fill must show there: the white line paint, the teal body, or the dark background.
     */
    @Test
    fun loneLinesAreHandledOnFdroidCameraAndChrome() {
        val probes = mapOf(
            // Antennas: unenclosed lines, drawn on top in the line paint.
            "fdroid" to listOf(Triple(7.3f, 7.7f, 'L'), Triple(40.7f, 7.7f, 'L'), Triple(24f, 15f, 'F')),
            // The small dot: a gap ring around a filled centre, inside the body fill.
            "camera" to listOf(Triple(38.5f, 17.02f, 'F'), Triple(38.5f, 15.02f, 'G'), Triple(34f, 17f, 'F')),
            // An inner line: a gap with fill on both sides.
            "google_chrome" to listOf(Triple(34f, 15.02f, 'G'), Triple(34f, 10f, 'F'), Triple(34f, 18f, 'F')),
        )
        val theme = THEMES.getValue(FillMode.INVERT)
        val renderer = IconRenderer()
        val scale = IconRenderer.unitScale(theme.shape, SIZE)
        val origin = (SIZE - 48 * scale) / 2
        for ((name, points) in probes) {
            val i = SAMPLES.indexOf(name)
            val img = renderer.render(i, bundle.icon(i), theme, SIZE)
            assertTrue(!renderer.glyphMasks(i, bundle.icon(i), theme, SIZE).fellBack, "$name fell back to Outline")
            for ((u, v, want) in points) {
                val x = (origin + u * scale).toInt()
                val y = (origin + v * scale).toInt()
                val o = (y * SIZE + x) * 4
                val (r, g, b) = (0..2).map { img.pixels[o + it].toInt() and 0xFF }
                val got = when {
                    r > 220 && g > 220 && b > 220 -> 'L'
                    g > 180 && r < 160 -> 'F'
                    maxOf(r, g, b) < 80 -> 'G'
                    else -> '?'
                }
                assertEquals(want, got, "$name at ($u, $v) is rgb($r, $g, $b)")
            }
        }
    }

    @Test
    fun sameRenderEncodesToTheSameBytes() {
        val i = SAMPLES.indexOf("fdroid")
        for (theme in THEMES.values) {
            val image = IconRenderer().render(i, bundle.icon(i), theme, SIZE)
            val again = IconRenderer().render(i, bundle.icon(i), theme, SIZE)
            assertContentEquals(image.pixels, again.pixels)
            assertContentEquals(PngEncoder.encode(image), PngEncoder.encode(again))
        }
    }

    @Test
    fun goldensCoverEveryMode() {
        assertEquals(FillMode.entries.toSet(), THEMES.keys)
        assertEquals(24, SAMPLES.size)
    }

    /** Straight ARGB of every pixel. */
    private fun decode(png: ByteArray): IntArray {
        val img = ImageIO.read(ByteArrayInputStream(png))
        return img.getRGB(0, 0, img.width, img.height, null, 0, img.width)
    }

    companion object {
        const val SIZE = 432

        val SAMPLES = listOf(
            "fdroid", "camera", "google_chrome", "firefox", "signal", "telegram", "whatsapp", "google_maps",
            "youtube", "spotify", "calculator", "clock", "contacts", "gallery", "messages", "phone",
            "settings", "weather", "notes", "termux", "vlc", "wikipedia", "keepassdx", "newpipe",
        )

        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val TEAL = 0xFF7FD1B9.toInt()

        val THEMES: Map<FillMode, Theme> = mapOf(
            FillMode.OUTLINE to Theme(),
            FillMode.INVERT to Theme(
                fill = FillParams(mode = FillMode.INVERT),
                background = BackgroundParams(
                    shape = BackgroundShape.Circle,
                    paint = Paint.LinearGradient(0f, 0f, 1f, 1f, listOf(ColorStop(0f, 0xFF1B3B36.toInt()), ColorStop(1f, 0xFF0E1A18.toInt()))),
                ),
                glyph = GlyphPaint(line = WHITE, body = TEAL),
            ),
            FillMode.SILHOUETTE to Theme(
                shape = ShapeParams(strokeWeight = 1.5f, join = LineJoin.MITER),
                fill = FillParams(mode = FillMode.SILHOUETTE),
                background = BackgroundParams(
                    shape = BackgroundShape.RoundedSquare(0.25f),
                    paint = Paint.RadialGradient(0.35f, 0.3f, 0.9f, listOf(ColorStop(0f, 0xFF4A3F6B.toInt()), ColorStop(1f, 0xFF16121F.toInt()))),
                    border = Border(0.02f, 0xFFE0D7FF.toInt(), 0.6f),
                ),
                glyph = GlyphPaint(line = WHITE, body = 0xFFE0D7FF.toInt()),
            ),
            FillMode.OUTLINE_SILHOUETTE to Theme(
                shape = ShapeParams(iconScale = 0.9f, cap = LineCap.SQUARE),
                fill = FillParams(mode = FillMode.OUTLINE_SILHOUETTE),
                background = BackgroundParams(
                    shape = BackgroundShape.Teardrop,
                    size = 0.92f,
                    paint = Paint.Solid(0xFF263238.toInt()),
                    border = Border(0.015f, WHITE, 0.3f),
                ),
                glyph = GlyphPaint(line = WHITE, body = 0xFF3D5A80.toInt()),
            ),
        )
    }
}
