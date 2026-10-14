package dev.iconoclast.app.theme

import dev.iconoclast.render.BackgroundShape
import dev.iconoclast.render.BevelStyle
import dev.iconoclast.render.Breakup
import dev.iconoclast.render.Decay
import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.ClipTarget
import dev.iconoclast.render.ColorStop
import dev.iconoclast.render.Crop
import dev.iconoclast.render.Mapping
import dev.iconoclast.render.MappingMode
import dev.iconoclast.render.Matte
import dev.iconoclast.render.MatteMode
import dev.iconoclast.render.NoiseKind
import dev.iconoclast.render.Paint
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.Border
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.ShapeParams
import dev.iconoclast.render.Theme
import dev.iconoclast.render.format.IconBundle
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeSerializationTest {
    private val bundle = IconBundle(ByteBuffer.wrap(File(requireNotNull(System.getProperty("golden.samples"))).readBytes()))

    private fun doc(theme: Theme) = ThemeDocument(id = "t", name = "Test", created = 1, modified = 2, theme = theme)

    private val themes: List<Theme> = GoldenSet.THEMES.values + listOf(
        Theme(),
        Theme(background = Theme().background.copy(shape = BackgroundShape.Square, border = Border(0.05f, 0x80FF0000.toInt()))),
        Theme(background = Theme().background.copy(shape = BackgroundShape.RoundedSquare(0.5f), enabled = false)),
        // Effects is no longer offered as a clip target; themes that use it still load.
        Theme(layers = Theme().layers + PaintLayer("fx", Paint.Solid(0x80FFFFFF.toInt()), clip = ClipTarget.EFFECTS)),
        EVERY_EFFECT,
    )

    /** Schema 3: every effect type with non-default values, one switched off. */
    @Test
    fun effectLayersRoundTrip() {
        val withTexture = EVERY_EFFECT.withEffect(EffectLayer("brush", Effect.Streaks(breakup = Breakup(texture = "ab".repeat(32)))))
        val json = doc(withTexture).toJson()
        for (key in listOf("\"type\": \"effect\"", "\"type\": \"streaks\"", "\"type\": \"bevel\"", "\"type\": \"dropShadow\"",
            "\"type\": \"innerShadow\"", "\"type\": \"outerGlow\"", "\"type\": \"innerGlow\"", "\"type\": \"grain\"",
            "\"paintLayer\": \"icon-fill\"", "\"breakup\": null", "\"texture\": \"abab", "\"enabled\": false")) {
            assertTrue(key in json, key)
        }
        assertEquals(withTexture, migrate(json).theme)
        val bad = json.replace("\"stretch\": 8.0", "\"stretch\": 40.0")
        assertTrue(bad != json)
        assertFailsWith<ThemeFormatException> { migrate(bad) }
    }

    @Test
    fun roundTripIsIdentical() {
        for (theme in themes) {
            val original = doc(theme)
            val parsed = migrate(original.toJson())
            assertEquals(original, parsed)
            assertEquals(original.toJson(), parsed.toJson())
        }
    }

    @Test
    fun roundTripRendersTheSameBytes() {
        for (theme in themes) {
            val parsed = migrate(doc(theme).toJson()).theme
            val icon = bundle.icon(3)
            assertContentEquals(IconRenderer().renderPng("test", icon, theme, 128), IconRenderer().renderPng("test", icon, parsed, 128))
        }
    }

    /** Every layer field, source type and option survives a round trip. */
    @Test
    fun layerFeaturesRoundTrip() {
        val stops = listOf(ColorStop(0f, 0xFF0B3D2E.toInt()), ColorStop(1f, 0xFF7FD1B9.toInt()))
        val layers = Theme.baseLayers().toMutableList()
        layers.add(1, PaintLayer(
            "tex", Paint.Texture("ab".repeat(32), stops), clip = ClipTarget.ICON, clipInverse = true,
            blend = BlendMode.SOFT_LIGHT, opacity = 0.4f, mapping = Mapping(MappingMode.TILE, 2f, -30f, randomShift = 0.3f, randomRotation = 45f, randomScale = 0.5f, seed = -7,
                crop = Crop(0.1f, 0.2f, 0.9f, 0.6f)),
            matte = Matte(MatteMode.THRESHOLD, threshold = 0.6f, invert = true, feather = 0.01f),
        ))
        layers.add(PaintLayer("noise", Paint.Noise(NoiseKind.RIDGED, 6, 42, stops), blend = BlendMode.ADD, opacity = 0.25f))
        layers.add(PaintLayer("sweep", Paint.AngularGradient(0.3f, 0.6f, 45f, stops + ColorStop(1f, 0x80FF0000.toInt()))))
        layers.add(EffectLayer("blur", Effect.Blur(0.05f, ClipTarget.ICON_FILL, clipInverse = true), opacity = 0.7f))
        layers.add(PaintLayer("tex2", Paint.Texture("cd".repeat(32)), clip = ClipTarget.EFFECTS, matte = Matte(black = 0.1f, white = 0.9f)))
        val original = doc(Theme(layers = layers))
        val parsed = migrate(original.toJson())
        assertEquals(original, parsed)
        assertEquals(original.toJson(), parsed.toJson())
        assertEquals(setOf("ab".repeat(32), "cd".repeat(32)), parsed.theme.textureHashes())
        for (bad in listOf(
            original.toJson().replace("\"SOFT_LIGHT\"", "\"VIVID_LIGHT\""),
            original.toJson().replace("\"id\": \"tex2\"", "\"id\": \"tex\""),
            original.toJson().replace("\"type\": \"effects\"", "\"type\": \"effects\", \"extra\": 1"),
            original.toJson().replace("\"octaves\": 6", "\"octaves\": 9"),
            original.toJson().replace("ab".repeat(32), "AB".repeat(32)),
        )) {
            assertFailsWith<ThemeFormatException>(bad) { migrate(bad) }
        }
    }

    @Test
    fun negativeZeroWritesAndReadsAsZero() {
        val negative = doc(Theme(shape = ShapeParams(offsetX = -0f, offsetY = -0f)))
        val positive = doc(Theme(shape = ShapeParams(offsetX = 0f, offsetY = 0f)))
        assertEquals(positive.toJson(), negative.toJson())
        assertFalse("-0" in negative.toJson())
        val parsed = migrate(positive.toJson().replace("\"offsetX\": 0.0", "\"offsetX\": -0.0"))
        assertEquals(positive, parsed)
        assertEquals(positive.theme.hashCode(), parsed.theme.hashCode())
    }

    @Test
    fun everyFieldIsWritten() {
        val json = doc(Theme()).toJson()
        for (key in listOf("schemaVersion", "strokeWeight", "dots", "keepLoneLines", "\"border\": null", "\"type\": \"squircle\"", "\"color\": \"#FF202124\""))
            assertTrue(key in json, "missing $key in $json")
    }

    @Test
    fun badValuesAreRejected() {
        val json = doc(Theme()).toJson()
        for (bad in listOf(
            json.replace("\"strokeWeight\": 1.0", "\"strokeWeight\": 9.0"),
            json.replace("#FF202124", "red"),
            json.replace("\"squircle\"", "\"hexagon\""),
            json.replace("\"keepLoneLines\": true", "\"keepLoneLines\": true, \"extra\": 1"),
            json.replace("\"strokeWeight\": 1.0,", ""),
        )) {
            assertFailsWith<ThemeFormatException>(bad) { migrate(bad) }
        }
    }

    private companion object {
        val EVERY_EFFECT: Theme = listOf(
            EffectLayer("streaks", Effect.Streaks(angle = 45f, length = 0.4f, decay = Decay.LINEAR,
                breakup = Breakup(scale = 2f, stretch = 8f, density = 0.7f, contrast = 0.2f, seed = 3), paintLayer = "icon-fill"),
                blend = BlendMode.SCREEN, opacity = 0.3f),
            EffectLayer("plain-streaks", Effect.Streaks(breakup = null, color = 0xFF112233.toInt()), enabled = false),
            EffectLayer("strokes", Effect.BrushStrokes(count = 123, length = 0.33f, width = 0.05f, softness = 0.7f, blur = 0.9f, fade = 0.1f,
                randomness = 0.25f, bend = -0.75f, bristles = 0.4f, drift = 0.6f, angle = 135f, paintLayer = "icon-fill", color = 0xFF445566.toInt(),
                seed = -42), blend = BlendMode.MULTIPLY, opacity = 0.6f),
            EffectLayer("shadow", Effect.DropShadow(angle = 300f, distance = 0.02f, blur = 0.03f, color = 0x80000000.toInt()), opacity = 0.5f),
            EffectLayer("glow", Effect.OuterGlow(size = 0.05f, spread = 0.3f, color = 0xFF00FF88.toInt()), blend = BlendMode.ADD),
        ).fold(Theme()) { t, l -> t.withEffect(l) }.let { t ->
            t.copy(layers = t.layers + listOf(
                EffectLayer("bevel", Effect.Bevel(BevelStyle.EMBOSS, lightAngle = 45f, depth = 2f, size = 0.02f, softness = 0.5f,
                    highlight = 0xFFFFEEDD.toInt(), highlightBlend = BlendMode.ADD, shadow = 0xFF001122.toInt(), shadowBlend = BlendMode.DARKEN)),
                EffectLayer("inner-shadow", Effect.InnerShadow(angle = 10f, distance = 0.005f, blur = 0f)),
                EffectLayer("inner-glow", Effect.InnerGlow(size = 0.001f, spread = 1f)),
                EffectLayer("grain", Effect.Grain(amount = 0.25f, size = 2f, seed = -7), blend = BlendMode.OVERLAY, opacity = 0.8f),
            ))
        }
    }
}
