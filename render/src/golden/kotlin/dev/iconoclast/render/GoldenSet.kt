package dev.iconoclast.render

/**
 * The golden image set: 24 sample icons and one theme per fill mode. Shared by the JVM
 * GoldenTest and the on-device byte identity test in :app's androidTest.
 */
object GoldenSet {
    val SAMPLES = listOf(
        "fdroid", "camera", "google_chrome", "firefox", "signal", "telegram", "whatsapp", "google_maps",
        "youtube", "spotify", "calculator", "clock", "contacts", "gallery", "messages", "phone",
        "settings", "weather", "notes", "termux", "vlc", "wikipedia", "keepassdx", "newpipe",
    )

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val TEAL = 0xFF7FD1B9.toInt()
    private const val JADE = 0xFF00A86B.toInt()

    val THEMES: Map<FillMode, Theme> = mapOf(
        FillMode.OUTLINE to Theme(),
        FillMode.INVERT to Theme(
            fill = FillParams(mode = FillMode.INVERT),
            background = BackgroundParams(
                shape = BackgroundShape.Circle,
            ),
            layers = Theme.baseLayers(
                Paint.LinearGradient(0f, 0f, 1f, 1f, listOf(ColorStop(0f, 0xFF1B3B36.toInt()), ColorStop(1f, 0xFF0E1A18.toInt()))),
                body = TEAL, line = WHITE,
            ),
        ),
        FillMode.SILHOUETTE to Theme(
            shape = ShapeParams(strokeWeight = 1.5f, join = LineJoin.MITER),
            fill = FillParams(mode = FillMode.SILHOUETTE),
            background = BackgroundParams(
                shape = BackgroundShape.RoundedSquare(0.25f),
                border = Border(0.02f, 0xFFE0D7FF.toInt(), 0.6f),
            ),
            layers = Theme.baseLayers(
                Paint.RadialGradient(0.35f, 0.3f, 0.9f, listOf(ColorStop(0f, 0xFF4A3F6B.toInt()), ColorStop(1f, 0xFF16121F.toInt()))),
                body = 0xFFE0D7FF.toInt(), line = WHITE,
            ),
        ),
        FillMode.OUTLINE_SILHOUETTE to Theme(
            shape = ShapeParams(iconScale = 0.9f, cap = LineCap.SQUARE),
            fill = FillParams(mode = FillMode.OUTLINE_SILHOUETTE),
            background = BackgroundParams(
                shape = BackgroundShape.Teardrop,
                size = 0.92f,
                border = Border(0.015f, WHITE, 0.3f),
            ),
            layers = Theme.baseLayers(Paint.Solid(0xFF263238.toInt()), body = 0xFF3D5A80.toInt(), line = WHITE),
        ),
    )

    /**
     * Invert fill with a closing far wider than the gap. fdroid's slot between head and
     * body is an exterior channel and must stay empty. signal's band inside its dashed
     * ring is sealed at closing 1.5 and must stay filled at 2, where the dilated strokes
     * bury it; the gaps between the dashes outside the ring stay empty.
     */
    val POCKET_THEME: Theme = THEMES.getValue(FillMode.INVERT).let { it.copy(fill = it.fill.copy(gapWidth = 0.25f, gapClosing = 2f)) }
    val POCKET_SAMPLES = listOf("signal", "fdroid")

    /**
     * The two starter presets, app/src/main/assets/presets, built in code. Their goldens were
     * made by the M4 renderer from its glyph paint; the M5 layer stack reproduces them byte
     * for byte, as it does every golden above.
     */
    val PRESETS: Map<String, Theme> = mapOf(
        "plain" to Theme(
            background = BackgroundParams(enabled = false),
            layers = Theme.baseLayers(Paint.Solid(0x00000000), body = WHITE, line = WHITE),
        ),
        "jade" to Theme(
            fill = FillParams(mode = FillMode.INVERT),
            background = BackgroundParams(shape = BackgroundShape.RoundedSquare(0.2f)),
            layers = Theme.baseLayers(Paint.Solid(0xFF000000.toInt()), body = JADE, line = JADE),
        ),
    )

    /**
     * The bundled black-and-white test texture, original work (golden/textures/NOTICE.txt),
     * and its hash. Every case renders with it in the renderer's texture store.
     */
    const val TEXTURE_FILE = "textures/cracks.png"
    const val TEXTURE = "c6d24d8999ff28eae18eca0d2869b0d900a6ea577f9727ecbea421a78ffbb395"

    val LOOK_SAMPLES = listOf("fdroid", "camera", "google_chrome", "signal", "spotify", "weather")

    private fun stops(from: Int, to: Int) = listOf(ColorStop(0f, from), ColorStop(1f, to))

    /**
     * Three layer looks over the texture and noise: every source type, mapping mode, clip
     * target class, a matte of each kind, and separable, non-separable and additive blends.
     */
    val LOOKS: Map<String, Theme> = mapOf(
        "layers_stone" to Theme(
            fill = FillParams(mode = FillMode.INVERT),
            background = BackgroundParams(shape = BackgroundShape.RoundedSquare(0.2f)),
            layers = listOf(
                PaintLayer(Layer.BACKGROUND_ID, Paint.Solid(0xFF0A0F0D.toInt()), clip = ClipTarget.BACKGROUND),
                PaintLayer("bg-cracks", Paint.Texture(TEXTURE, stops(0xFF0A0F0D.toInt(), 0xFF1E3B30.toInt())), clip = ClipTarget.BACKGROUND,
                    blend = BlendMode.SCREEN, opacity = 0.6f, mapping = Mapping(MappingMode.TILE, scale = 1.5f, rotation = 20f)),
                EffectsLayer(),
                PaintLayer(Layer.ICON_FILL_ID, Paint.Noise(NoiseKind.FBM, 5, 7, stops(0xFF0B4F3A.toInt(), 0xFF5FD3A0.toInt())), clip = ClipTarget.ICON_FILL),
                PaintLayer("fill-veins", Paint.Texture(TEXTURE, listOf(ColorStop(0f, 0xFFE8FFF4.toInt()))), clip = ClipTarget.ICON_FILL,
                    blend = BlendMode.SCREEN, opacity = 0.8f, matte = Matte(black = 0.2f, white = 0.8f, feather = 0.004f)),
                PaintLayer(Layer.ICON_LINES_ID, Paint.Solid(0xFF7FE0B5.toInt()), clip = ClipTarget.ICON_LINES),
            ),
        ),
        "layers_etched" to Theme(
            fill = FillParams(mode = FillMode.OUTLINE_SILHOUETTE),
            background = BackgroundParams(shape = BackgroundShape.Circle),
            layers = listOf(
                PaintLayer(Layer.BACKGROUND_ID, Paint.LinearGradient(0f, 0f, 1f, 1f, stops(0xFF3A4766.toInt(), 0xFF101522.toInt())), clip = ClipTarget.BACKGROUND),
                EffectsLayer(),
                PaintLayer(Layer.ICON_FILL_ID, Paint.Texture(TEXTURE, stops(0xFF26324A.toInt(), 0xFFB8C8F0.toInt())), clip = ClipTarget.ICON_FILL,
                    mapping = Mapping(MappingMode.FILL, rotation = -35f)),
                PaintLayer("etch", Paint.Texture(TEXTURE, listOf(ColorStop(0f, 0xFF000000.toInt()))), clip = ClipTarget.ICON_FILL,
                    blend = BlendMode.MULTIPLY, opacity = 0.8f, mapping = Mapping(MappingMode.FILL, scale = 2f),
                    matte = Matte(MatteMode.THRESHOLD, threshold = 0.5f, feather = 0.003f)),
                PaintLayer(Layer.ICON_LINES_ID, Paint.Solid(0xFF9FB7FF.toInt()), clip = ClipTarget.ICON_LINES),
                PaintLayer("lines-tint", Paint.Noise(NoiseKind.RIDGED, 4, 11, stops(0xFFFF8A5C.toInt(), 0xFF5CC8FF.toInt())), clip = ClipTarget.ICON_LINES,
                    blend = BlendMode.COLOR, opacity = 0.8f),
            ),
        ),
        "layers_grain" to Theme(
            fill = FillParams(mode = FillMode.SILHOUETTE),
            background = BackgroundParams(shape = BackgroundShape.Square, border = Border(0.02f, 0xFFF2C14E.toInt(), 0.5f)),
            layers = listOf(
                PaintLayer(Layer.BACKGROUND_ID, Paint.Solid(0xFF2B2140.toInt()), clip = ClipTarget.BACKGROUND),
                PaintLayer("bg-grain", Paint.Noise(NoiseKind.GRAIN, seed = 3), clip = ClipTarget.BACKGROUND, blend = BlendMode.OVERLAY, opacity = 0.35f,
                    mapping = Mapping(scale = 4f)),
                PaintLayer("outside-glow", Paint.Texture(TEXTURE, listOf(ColorStop(0f, 0xFFF2C14E.toInt()))), clip = ClipTarget.ICON,
                    clipInverse = true, blend = BlendMode.ADD, opacity = 0.4f, mapping = Mapping(MappingMode.TILE, scale = 0.5f), matte = Matte()),
                EffectsLayer(),
                PaintLayer(Layer.ICON_FILL_ID, Paint.Solid(0xFFF2C14E.toInt()), clip = ClipTarget.ICON_FILL),
                PaintLayer("fill-tex", Paint.Texture(TEXTURE), clip = ClipTarget.ICON_FILL, blend = BlendMode.DIFFERENCE, opacity = 0.6f,
                    mapping = Mapping(MappingMode.FIT, rotation = 45f, lockOffset = true)),
                PaintLayer("hue-shift", Paint.Noise(NoiseKind.FBM, 2, 5, stops(0xFFFF0000.toInt(), 0xFF0000FF.toInt())), clip = ClipTarget.ICON,
                    blend = BlendMode.HUE, opacity = 0.5f),
                PaintLayer(Layer.ICON_LINES_ID, Paint.Solid(WHITE), clip = ClipTarget.ICON_LINES),
            ),
        ),
    )

    val BLEND_SAMPLES = listOf("camera", "weather")

    /**
     * The nine blend modes the looks leave out, each over a varied backdrop: dodge, burn
     * and soft light on the background; hard light, darken and saturation on the fill;
     * lighten over the whole icon; exclusion over the canvas; luminosity on the lines.
     */
    val BLENDS: Map<String, Theme> = mapOf(
        "layers_blends" to Theme(
            fill = FillParams(mode = FillMode.INVERT),
            background = BackgroundParams(shape = BackgroundShape.RoundedSquare(0.25f)),
            layers = listOf(
                PaintLayer(Layer.BACKGROUND_ID, Paint.LinearGradient(0f, 0f, 1f, 1f, stops(0xFF5B6FA8.toInt(), 0xFF2E3550.toInt())),
                    clip = ClipTarget.BACKGROUND),
                PaintLayer("bg-dodge", Paint.Texture(TEXTURE, stops(0xFF000000.toInt(), 0xFFB07A3A.toInt())), clip = ClipTarget.BACKGROUND,
                    blend = BlendMode.COLOR_DODGE, opacity = 0.8f, mapping = Mapping(MappingMode.TILE, scale = 0.75f)),
                PaintLayer("bg-burn", Paint.RadialGradient(0.5f, 0.5f, 0.7f, stops(0xFFFFFFFF.toInt(), 0xFF6A2C70.toInt())),
                    clip = ClipTarget.BACKGROUND, blend = BlendMode.COLOR_BURN, opacity = 0.7f),
                PaintLayer("bg-soft", Paint.Noise(NoiseKind.FBM, 4, 21, stops(0xFF000000.toInt(), 0xFFFFFFFF.toInt())),
                    clip = ClipTarget.BACKGROUND, blend = BlendMode.SOFT_LIGHT),
                EffectsLayer(),
                PaintLayer(Layer.ICON_FILL_ID, Paint.Solid(0xFFE07A5F.toInt()), clip = ClipTarget.ICON_FILL),
                PaintLayer("fill-hard", Paint.LinearGradient(0f, 1f, 1f, 0f, stops(0xFF1A1A40.toInt(), 0xFFF4F1BB.toInt())),
                    clip = ClipTarget.ICON_FILL, blend = BlendMode.HARD_LIGHT, opacity = 0.9f),
                PaintLayer("fill-darken", Paint.Texture(TEXTURE, stops(0xFF3D405B.toInt(), 0xFFFFFFFF.toInt())), clip = ClipTarget.ICON_FILL,
                    blend = BlendMode.DARKEN, mapping = Mapping(MappingMode.FILL, scale = 2f, rotation = 60f)),
                PaintLayer("fill-sat", Paint.Solid(0xFF00FFAA.toInt()), clip = ClipTarget.ICON_FILL, blend = BlendMode.SATURATION, opacity = 0.6f),
                PaintLayer("icon-lighten", Paint.Noise(NoiseKind.RIDGED, 3, 9, stops(0xFF202020.toInt(), 0xFF9AD1D4.toInt())),
                    clip = ClipTarget.ICON, blend = BlendMode.LIGHTEN, opacity = 0.7f),
                PaintLayer(Layer.ICON_LINES_ID, Paint.Solid(0xFFF2CC8F.toInt()), clip = ClipTarget.ICON_LINES),
                PaintLayer("lines-lum", Paint.LinearGradient(0f, 0f, 0f, 1f, stops(0xFFFFFFFF.toInt(), 0xFF303030.toInt())),
                    clip = ClipTarget.ICON_LINES, blend = BlendMode.LUMINOSITY, opacity = 0.8f),
                PaintLayer("canvas-excl", Paint.Noise(NoiseKind.FBM, 2, 33, stops(0xFF000000.toInt(), 0xFF80C0FF.toInt())),
                    clip = ClipTarget.CANVAS, blend = BlendMode.EXCLUSION, opacity = 0.35f),
            ),
        ),
    )

    /** Four icons for the effects: antennas and dots, a filled body, a ring, fine teeth. */
    val EFFECT_SAMPLES = listOf("fdroid", "camera", "spotify", "settings")

    /** The ground every single-effect golden sits on: invert fill, a dark tile, teal and white. */
    private val EFFECT_BASE = Theme(
        fill = FillParams(mode = FillMode.INVERT),
        background = BackgroundParams(shape = BackgroundShape.RoundedSquare(0.2f)),
        layers = Theme.baseLayers(Paint.Solid(0xFF26323C.toInt()), body = TEAL, line = WHITE),
    )

    /** Below the icon, at the effects marker. */
    private fun below(effect: EffectLayer) = EFFECT_BASE.withEffect(effect)

    /** On top of the whole stack, where effects on the icon show. */
    private fun above(effect: EffectLayer) = EFFECT_BASE.copy(layers = EFFECT_BASE.layers + effect)

    /** One golden set per effect, M6. Built-in brush for the streaks; bevel in both styles. */
    val EFFECTS: Map<String, Theme> = mapOf(
        "effect_bevel" to above(EffectLayer("bevel", Effect.Bevel(size = 0.012f, depth = 1.5f, softness = 0.3f))),
        "effect_emboss" to above(EffectLayer("bevel", Effect.Bevel(BevelStyle.EMBOSS, lightAngle = 45f, size = 0.02f, depth = 1f,
            highlight = 0x99FFFFFF.toInt(), shadow = 0x99000000.toInt()))),
        "effect_drop_shadow" to below(EffectLayer("shadow", Effect.DropShadow(angle = 120f, distance = 0.015f, blur = 0.03f,
            color = 0xFF000000.toInt()), blend = BlendMode.MULTIPLY, opacity = 0.8f)),
        "effect_inner_shadow" to above(EffectLayer("inner-shadow", Effect.InnerShadow(angle = 120f, distance = 0.008f, blur = 0.012f,
            color = 0xFF0A1A20.toInt()), opacity = 0.7f)),
        "effect_outer_glow" to below(EffectLayer("glow", Effect.OuterGlow(size = 0.05f, spread = 0.2f, color = 0xFF7FFFD4.toInt()),
            blend = BlendMode.SCREEN, opacity = 0.8f)),
        "effect_inner_glow" to above(EffectLayer("inner-glow", Effect.InnerGlow(size = 0.02f, spread = 0.3f, color = 0xFFFFF4C0.toInt()),
            opacity = 0.8f)),
    )

    /** Every golden: directory, theme and sample name. */
    val CASES: List<Triple<String, Theme, String>>
        get() = THEMES.flatMap { (mode, theme) -> SAMPLES.map { Triple(mode.name.lowercase(), theme, it) } } +
            POCKET_SAMPLES.map { Triple("pockets", POCKET_THEME, it) } +
            PRESETS.flatMap { (dir, theme) -> SAMPLES.map { Triple(dir, theme, it) } } +
            LOOKS.flatMap { (dir, theme) -> LOOK_SAMPLES.map { Triple(dir, theme, it) } } +
            BLENDS.flatMap { (dir, theme) -> BLEND_SAMPLES.map { Triple(dir, theme, it) } } +
            EFFECTS.flatMap { (dir, theme) -> EFFECT_SAMPLES.map { Triple(dir, theme, it) } }
}
