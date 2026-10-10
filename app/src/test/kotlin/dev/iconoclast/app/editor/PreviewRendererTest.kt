package dev.iconoclast.app.editor

import dev.iconoclast.render.ShapeParams
import dev.iconoclast.render.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewRendererTest {
    private fun weight(w: Float) = Theme(shape = ShapeParams(strokeWeight = w))

    @Test
    fun newestRequestWinsAndNoneQueues() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val gate = CountDownLatch(1)
        val started = CountDownLatch(1)
        val drawn = Collections.synchronizedList(mutableListOf<Float>())
        val r = PreviewRenderer(scope, Dispatchers.Default, 128, render = { d, t ->
            if (t == weight(1f)) {
                started.countDown()
                gate.await(5, TimeUnit.SECONDS)
            }
            drawn += t.shape.strokeWeight
            "$d@${t.shape.strokeWeight}"
        })
        r.request(PreviewRequest(weight(1f), listOf(0, 1)))
        assertTrue(started.await(5, TimeUnit.SECONDS))
        // The first pass is blocked. Three more requests arrive; only the last may run.
        for (w in listOf(2f, 3f, 4f)) r.request(PreviewRequest(weight(w), listOf(0, 1)))
        gate.countDown()
        val last = withTimeout(5000) { r.frame.first { it.theme == weight(4f) && it.complete } }
        assertEquals(mapOf(0 to "0@4.0", 1 to "1@4.0"), last.images)
        assertEquals(listOf(1f, 4f), drawn.toSet().toList().sorted(), "2 and 3 were dropped")
        assertEquals(2, r.passes)
        scope.cancel()
    }

    @Test
    fun visibleFirstKeepsTheOtherImagesUntilAFullPass() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val r = PreviewRenderer(scope, Dispatchers.Default, 128, render = { d, t -> "$d@${t.shape.strokeWeight}".also { calls += it } })
        r.request(PreviewRequest(weight(1f), listOf(0, 1, 2)))
        withTimeout(5000) { r.frame.first { it.complete } }
        r.request(PreviewRequest(weight(2f), listOf(0, 1, 2), first = setOf(1)))
        val partial = withTimeout(5000) { r.frame.first { it.theme == weight(2f) } }
        assertEquals(mapOf(0 to "0@1.0", 1 to "1@2.0", 2 to "2@1.0"), partial.images)
        assertTrue(!partial.complete)
        r.request(PreviewRequest(weight(2f), listOf(0, 1, 2)))
        val full = withTimeout(5000) { r.frame.first { it.theme == weight(2f) && it.complete } }
        assertEquals(mapOf(0 to "0@2.0", 1 to "1@2.0", 2 to "2@2.0"), full.images)
        // 1 at weight 2 came from the cache.
        assertEquals(1, calls.count { it == "1@2.0" })
        // Back to weight 1: every image is cached.
        val before = calls.size
        r.request(PreviewRequest(weight(1f), listOf(0, 1, 2)))
        withTimeout(5000) { r.frame.first { it.theme == weight(1f) && it.complete } }
        assertEquals(before, calls.size)
        scope.cancel()
    }

    @Test
    fun aFailedPassCanBeRequestedAgain() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val fail = java.util.concurrent.atomic.AtomicBoolean(true)
        val r = PreviewRenderer(scope, Dispatchers.Default, 128, render = { d, t ->
            if (fail.get()) error("disk gone")
            "$d@${t.shape.strokeWeight}"
        })
        val req = PreviewRequest(weight(1f), listOf(0))
        r.request(req)
        withTimeout(5000) { while (r.failures == 0) kotlinx.coroutines.delay(10) }
        fail.set(false)
        r.request(req)
        val frame = withTimeout(5000) { r.frame.first { it.complete } }
        assertEquals(mapOf(0 to "0@1.0"), frame.images)
        scope.cancel()
    }
}
