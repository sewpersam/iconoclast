package dev.icondesigner.render

import dev.icondesigner.render.format.Icon

/** Turns an icon into a coverage mask. The 48-unit canvas maps onto the full [size] square. */
fun interface IconRasterizer {
    /** Row-major coverage, one byte per pixel, 0 empty to 255 covered. */
    fun rasterize(icon: Icon, size: Int): ByteArray
}
