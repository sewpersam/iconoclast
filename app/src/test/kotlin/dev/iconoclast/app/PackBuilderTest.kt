package dev.iconoclast.app

import dev.iconoclast.app.theme.IconOverride
import dev.iconoclast.app.theme.ThemeDocument
import dev.iconoclast.app.theme.effectiveGeometry
import dev.iconoclast.app.theme.effectiveTheme
import dev.iconoclast.app.theme.withOverride
import dev.iconoclast.iconsource.IconSource
import dev.iconoclast.pack.CoverageMatcher
import dev.iconoclast.render.GoldenSet
import dev.iconoclast.render.IconRenderer
import dev.iconoclast.render.OUTPUT_SIZE
import dev.iconoclast.render.ShapeParams
import dev.iconoclast.render.Theme
import dev.iconoclast.render.format.BundleFormat
import dev.iconoclast.render.format.ComponentTableFormat
import dev.iconoclast.render.format.IconBundle
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Spec 3.5 determinism, tested where it can break: slot assembly, preview, generated assets. */
class PackBuilderTest {
    private val template = File(System.getProperty("template.apk")).readBytes()

    /** The golden sample icons as an IconSource, names sorted as the converter sorts them. */
    private val source: IconSource = run {
        val bundle = IconBundle(ByteBuffer.wrap(File(System.getProperty("golden.samples")).readBytes()))
        val sorted = GoldenSet.SAMPLES.withIndex().sortedBy { it.value }
        val names = sorted.map { it.value }
        val index = names.withIndex().associate { (i, n) -> n to i }
        IconSource.fromBytes(
            BundleFormat.encode(sorted.map { bundle.icon(it.index) }),
            names.joinToString("\n", postfix = "\n").toByteArray(),
            ComponentTableFormat.encode(
                mapOf(
                    "org.fdroid.fdroid/org.fdroid.fdroid.views.main.MainActivity" to index.getValue("fdroid"),
                    "org.mozilla.firefox/org.mozilla.fenix.HomeActivity" to index.getValue("firefox"),
                    "org.mozilla.firefox/org.mozilla.fenix.IntentReceiverActivity" to index.getValue("firefox"),
                    "com.android.camera2/com.android.camera.CameraLauncher" to index.getValue("camera"),
                    "org.thoughtcrime.securesms/org.thoughtcrime.securesms.RoutingActivity" to index.getValue("signal"),
                ),
            ),
        )
    }

    private val components = listOf(
        "org.thoughtcrime.securesms/org.thoughtcrime.securesms.RoutingActivity",
        "org.mozilla.firefox/org.mozilla.fenix.HomeActivity",
        "com.android.camera2/com.android.camera.CameraLauncher",
        "org.mozilla.firefox/org.mozilla.fenix.IntentReceiverActivity",
        "org.fdroid.fdroid/org.fdroid.fdroid.views.main.MainActivity",
        "com.unknown/com.unknown.Main",
    )

    private val thick = Theme(shape = ShapeParams(strokeWeight = 2f))
    private val plan = CoverageMatcher.match(components, PackBuilder.slotCount(template), source::resolve)

    private val dir: File = java.nio.file.Files.createTempDirectory("packbuilder").toFile().apply { deleteOnExit() }
    private var builds = 0

    /** The unsigned pack's bytes, built to a file as Apply builds it. */
    private fun build(renderer: IconRenderer = IconRenderer(), look: (String) -> SlotLook = PackBuilder.uniform(thick)): ByteArray {
        val out = File(dir, "unsigned-${builds++}.apk")
        val work = File(dir, "icons")
        runBlocking { PackBuilder(source, renderer).build(template, plan, look, work, out) { _, _ -> } }
        assertTrue(!work.exists(), "the icon files are removed after the rewrite")
        return out.readBytes()
    }

    private fun entries(apk: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(apk)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                out[e.name] = z.readBytes()
            }
        }
        return out
    }

    @Test
    fun sameThemeAndPlanGiveTheSameUnsignedBytes() {
        assertEquals(4, plan.drawables.size)
        assertContentEquals(build(), build())
    }

    @Test
    fun eachSlotHoldsTheRenderOfItsDrawable() {
        val renderer = IconRenderer()
        val files = entries(build())
        for ((slot, drawable) in plan.drawables.withIndex()) {
            val expected = renderer.renderPng(source.names.name(drawable), source.icon(drawable), thick, OUTPUT_SIZE)
            assertContentEquals(expected, files.getValue(PackBuilder.slotPath(slot)), "slot $slot")
        }
        // The preview: "arcticons" is not a sample, so it falls back to the first slot's drawable.
        assertContentEquals(files.getValue(PackBuilder.slotPath(0)), files.getValue("res/drawable-nodpi-v4/pack_icon.png"))
        // Unused slots keep the template placeholder.
        assertEquals(entries(template).getValue(PackBuilder.slotPath(plan.drawables.size)).size, files.getValue(PackBuilder.slotPath(plan.drawables.size)).size)

        val appfilter = files.getValue("assets/appfilter.xml").decodeToString()
        for ((component, slot) in plan.items) {
            assertTrue("ComponentInfo{$component}\" drawable=\"${PackBuilder.slotName(slot)}\"" in appfilter, component)
        }
        val firefoxSlot = PackBuilder.slotName(plan.items.first { it.first.startsWith("org.mozilla.firefox/") }.second)
        assertEquals(2, Regex("drawable=\"$firefoxSlot\"").findAll(appfilter).count())
        assertTrue("com.unknown" !in appfilter)

        val drawableXml = files.getValue("assets/drawable.xml").decodeToString()
        assertEquals(plan.drawables.indices.map(PackBuilder::slotName), Regex("drawable=\"(slot_\\d+)\"").findAll(drawableXml).map { it.groupValues[1] }.toList())
    }

    /**
     * Readiness item 4: each slot draws its own effective theme and geometry, seeded by its
     * own name. fdroid is thicker, camera draws signal's line art, signal itself is plain,
     * and a swap to a name the icon set lacks draws the slot's own geometry.
     */
    @Test
    fun eachSlotDrawsItsOwnOverrides() {
        val jade = GoldenSet.STARTERS.getValue("preset_jade")
        val doc = ThemeDocument(id = "t", name = "T", created = 1, modified = 1, theme = jade)
            .withOverride("fdroid", IconOverride(strokeWeight = 3f, hideStreaks = true))
            .withOverride("camera", IconOverride(swapDrawable = "signal"))
            .withOverride("firefox", IconOverride(swapDrawable = "not_in_the_pin"))
        val look = { name: String -> SlotLook(doc.effectiveGeometry(name), doc.effectiveTheme(name)) }
        val files = entries(build(look = look))
        val fresh = IconRenderer()
        fun png(geometry: String, seed: String, theme: Theme) =
            fresh.renderPng(geometry, source.icon(geometry)!!, seed, theme, OUTPUT_SIZE)
        val expected = mapOf(
            "fdroid" to png("fdroid", "fdroid", doc.effectiveTheme("fdroid")),
            "camera" to png("signal", "camera", jade),
            "firefox" to png("firefox", "firefox", jade),
            "signal" to png("signal", "signal", jade),
        )
        for ((slot, drawable) in plan.drawables.withIndex()) {
            val name = source.names.name(drawable)
            assertContentEquals(expected.getValue(name), files.getValue(PackBuilder.slotPath(slot)), name)
        }
        assertTrue(!expected.getValue("camera").contentEquals(expected.getValue("signal")), "the swapped slot keeps its own seed")
        assertTrue(!expected.getValue("fdroid").contentEquals(png("fdroid", "fdroid", jade)), "the override shows")
    }
}
