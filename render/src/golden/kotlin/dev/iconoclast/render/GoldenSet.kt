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
     * made by the M4 renderer; the M5 layer stack must reproduce them byte for byte.
     */
    val PRESETS: Map<String, Theme> = mapOf(
        "plain" to Theme(
            background = BackgroundParams(enabled = false, paint = Paint.Solid(0x00000000)),
            glyph = GlyphPaint(line = WHITE, body = WHITE),
        ),
        "jade" to Theme(
            fill = FillParams(mode = FillMode.INVERT),
            background = BackgroundParams(shape = BackgroundShape.RoundedSquare(0.2f), paint = Paint.Solid(0xFF000000.toInt())),
            glyph = GlyphPaint(line = JADE, body = JADE),
        ),
    )

    /** Every golden: directory, theme and sample name. */
    val CASES: List<Triple<String, Theme, String>>
        get() = THEMES.flatMap { (mode, theme) -> SAMPLES.map { Triple(mode.name.lowercase(), theme, it) } } +
            POCKET_SAMPLES.map { Triple("pockets", POCKET_THEME, it) } +
            PRESETS.flatMap { (dir, theme) -> SAMPLES.map { Triple(dir, theme, it) } }
}
