package dev.icondesigner.render

import dev.icondesigner.render.format.Icon

/**
 * Turns an icon into a coverage mask. The 48-unit canvas maps onto the full [size] square.
 * Implementations must be safe to call from several threads at once.
 *
 * Known gap: the M1 conversion gate (`./gradlew verifyIconConversion`) ran with
 * `AwtRasterizer`, a java.awt implementation in the JVM-only `verify` source set that never
 * ships. Task 3 (M2) must re-run that gate with its production implementation of this
 * interface, swapped in at `rasterizerUnderTest()` in
 * `render/src/verify/kotlin/dev/icondesigner/render/verify/VerifyConversion.kt`, and the gate
 * must still pass.
 */
fun interface IconRasterizer {
    /** Row-major coverage, one byte per pixel, 0 empty to 255 covered. */
    fun rasterize(icon: Icon, size: Int): ByteArray
}
