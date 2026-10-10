package dev.iconoclast.render

import dev.iconoclast.render.format.Icon

/**
 * Turns an icon into a coverage mask. The 48-unit canvas maps onto the full [size] square.
 * Implementations must be safe to call from several threads at once.
 *
 * The production implementation is `raster.ScanlineRasterizer`. The M1 conversion gate
 * (`./gradlew verifyIconConversion`) runs it; see docs/verification/m2.md.
 */
fun interface IconRasterizer {
    /** Row-major coverage, one byte per pixel, 0 empty to 255 covered. */
    fun rasterize(icon: Icon, size: Int): ByteArray
}
