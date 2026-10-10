package dev.icondesigner.render.format

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BundleFormatTest {

    private fun prim(flags: Int, width: Float, miter: Float, verbs: List<Byte>, coords: List<Float>) =
        Primitive(flags, width, miter, verbs.toByteArray(), coords.toFloatArray())

    @Test
    fun roundTripsIconsAndQuantizesCoordinates() {
        val stroke = Primitive.flags(stroke = true, fill = false, evenOdd = false, Primitive.CAP_ROUND, Primitive.JOIN_ROUND)
        val fill = Primitive.flags(stroke = false, fill = true, evenOdd = true, Primitive.CAP_BUTT, Primitive.JOIN_MITER)
        val icons = listOf(
            Icon(listOf(prim(stroke, 1f, 4f, listOf(Verb.MOVE, Verb.LINE), listOf(0.5f, 47.5f, 24.001f, -3.3f)))),
            Icon(emptyList()),
            Icon(
                listOf(
                    prim(fill, 0f, 10f, listOf(Verb.MOVE, Verb.CUBIC, Verb.CLOSE), listOf(1f, 1f, 2f, 2f, 3f, 3f, 4f, 4f)),
                    prim(stroke, 1.5f, 90f, listOf(Verb.MOVE, Verb.LINE), listOf(0f, 0f, 1f, 1f)),
                ),
            ),
        )
        val bundle = IconBundle(ByteBuffer.wrap(BundleFormat.encode(icons)))
        assertEquals(3, bundle.size)
        assertEquals(0, bundle.icon(1).primitives.size)

        val a = bundle.icon(0).primitives.single()
        assertEquals(stroke, a.flags)
        assertEquals(Primitive.CAP_ROUND, a.cap)
        assertEquals(Primitive.JOIN_ROUND, a.join)
        assertEquals(1f, a.strokeWidth)
        assertEquals(4f, a.miterLimit)
        assertContentEquals(byteArrayOf(Verb.MOVE, Verb.LINE), a.verbs)
        // Coordinates snap to 1/256 of a unit.
        assertContentEquals(floatArrayOf(0.5f, 47.5f, 24f, -3.3007812f), a.coords)

        val (b, c) = bundle.icon(2).primitives
        assertEquals(true, b.isFilled && b.isEvenOdd && !b.isStroked)
        assertEquals(10f, b.miterLimit)
        assertEquals(8, b.coords.size)
        assertEquals(1.5f, c.strokeWidth)
        assertEquals(90f, c.miterLimit)
    }

    @Test
    fun rejectsOutOfRangeCoordinatesAndBadInput() {
        val p = prim(1, 1f, 4f, listOf(Verb.MOVE), listOf(200f, 0f))
        assertFailsWith<IllegalArgumentException> { BundleFormat.encode(listOf(Icon(listOf(p)))) }
        assertFailsWith<IllegalArgumentException> { IconBundle(ByteBuffer.wrap(ByteArray(16))) }
        val bundle = IconBundle(ByteBuffer.wrap(BundleFormat.encode(listOf(Icon(emptyList())))))
        assertFailsWith<IndexOutOfBoundsException> { bundle.icon(1) }
    }

    @Test
    fun componentTableLooksUpByExactKey() {
        val table = ComponentTable(
            ByteBuffer.wrap(
                ComponentTableFormat.encode(
                    mapOf("com.b/com.b.Main" to 2, "com.a/com.a.Main" to 1, ":BROWSER" to 7, "pe.x/pe.ож" to 65535),
                ),
            ),
        )
        assertEquals(4, table.size)
        assertEquals(1, table.lookup("com.a/com.a.Main"))
        assertEquals(2, table.lookup("com.b/com.b.Main"))
        assertEquals(7, table.lookup(":BROWSER"))
        assertEquals(65535, table.lookup("pe.x/pe.ож"))
        assertEquals(-1, table.lookup("com.a/com.a.Mai"))
        assertEquals(-1, table.lookup("com.c/x"))
        assertEquals(-1, table.lookup(""))
    }
}
