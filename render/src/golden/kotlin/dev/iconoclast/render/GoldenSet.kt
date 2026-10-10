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
