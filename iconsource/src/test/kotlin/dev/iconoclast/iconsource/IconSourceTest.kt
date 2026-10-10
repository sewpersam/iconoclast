package dev.iconoclast.iconsource

import dev.iconoclast.render.format.BundleFormat
import dev.iconoclast.render.format.ComponentTableFormat
import dev.iconoclast.render.format.Icon
import dev.iconoclast.render.format.Primitive
import dev.iconoclast.render.format.Verb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IconSourceTest {

    private val names = listOf("calculator", "camera", "camera_2", "google_camera", "zoom")

    private fun icon(x: Float) = Icon(listOf(Primitive(1, 1f, 4f, byteArrayOf(Verb.MOVE, Verb.LINE), floatArrayOf(x, 0f, x, 1f))))

    private val source = IconSource.fromBytes(
        BundleFormat.encode(names.indices.map { icon(it.toFloat()) }),
        names.joinToString("\n", postfix = "\n").toByteArray(),
        ComponentTableFormat.encode(
            mapOf("com.cam/com.cam.Main" to 1, "com.calc/com.calc.Calculator" to 0, ":CAMERA" to 3),
        ),
    )

    @Test
    fun looksUpByName() {
        assertEquals(2, source.indexOf("camera_2"))
        assertEquals(-1, source.indexOf("camera_3"))
        assertEquals(4f, source.icon("zoom")!!.primitives.single().coords[0])
        assertNull(source.icon("nope"))
    }

    @Test
    fun searchesBySubstringPrefixFirst() {
        assertEquals(listOf(1, 2, 3), source.search("camera"))
        assertEquals(listOf(1, 2), source.search("Camera", limit = 2))
        assertEquals(listOf(3), source.search("google cam"))
        assertEquals(emptyList(), source.search("  "))
    }

    @Test
    fun resolvesComponentStrings() {
        assertEquals(1, source.resolve("ComponentInfo{com.cam/com.cam.Main}"))
        assertEquals(1, source.resolve("com.cam/com.cam.Main"))
        assertEquals(1, source.resolve("com.cam/.Main"))
        assertEquals(0, source.resolve("com.calc/com.calc.Calculator"))
        assertEquals(3, source.resolve(":CAMERA"))
        assertEquals(-1, source.resolve("com.cam/com.cam.Other"))
        assertEquals(-1, source.resolve("garbage"))
        assertEquals(-1, source.resolve("com.cam/"))
    }
}
