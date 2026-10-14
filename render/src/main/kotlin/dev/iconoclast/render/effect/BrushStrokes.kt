package dev.iconoclast.render.effect

import dev.iconoclast.render.BlendMode
import dev.iconoclast.render.Effect
import dev.iconoclast.render.IconSeed
import dev.iconoclast.render.Layer
import dev.iconoclast.render.PaintLayer
import dev.iconoclast.render.StrokeDirection
import dev.iconoclast.render.StrokeStyle
import dev.iconoclast.render.TextureOffset
import dev.iconoclast.render.layer.Noise
import dev.iconoclast.render.layer.StackEvaluator
import dev.iconoclast.render.layer.StackEvaluator.EffectOutput
import dev.iconoclast.render.layer.StackInput
import dev.iconoclast.render.mask.Blur
import dev.iconoclast.render.mask.label4
import dev.iconoclast.render.math.SplitMix64
import dev.iconoclast.render.math.ceil
import dev.iconoclast.render.math.floor
import dev.iconoclast.render.math.sqrt

/**
 * Brush strokes, schema 5: stroke-based painterly rendering after Hertzmann, "Painterly
 * Rendering with Curved Brush Strokes of Multiple Sizes" (SIGGRAPH 1998), turned outward.
 *
 * 1. Start points. Candidates are drawn uniformly over the canvas and kept
 *    when they lie on the icon's edge, inside it by at most half a stroke width (the
 *    signed distance field, mask/SignedDistance). Each candidate draws the same random
 *    numbers whether it is kept or not, so a 128 px preview and the 432 px pack keep the
 *    same strokes.
 * 2. The path. A direction field is the negated gradient of the icon mask blurred over a
 *    fraction of the stroke length: it points away from the icon, smoothly, and spreads
 *    like field lines around the whole shape. A stroke takes [STEPS] equal steps along
 *    it, its direction low-pass filtered as Hertzmann's curvature filter f_c, leaning by
 *    drift toward a fixed angle, turned by a per-stroke offset, a steady bend and a small
 *    wobble.
 * 3. The mark. Each step is a capsule whose half-width swells from 0.6 to full width over the first quarter and tapers to a point as `(1 - t)^0.6`;
 *    the stroke's coverage is their maximum, so a stroke never darkens itself. Bristles
 *    are value noise across the stroke: faint streaks in the loaded body, then, past a
 *    third of the length, each bristle runs dry where it falls below a threshold that
 *    rises toward the tip, so the stroke breaks into dry-brush marks. Paint
 *    runs out as `(1 - t)^(3 fade)`.
 * 4. Colour. Each stroke takes one colour, Hertzmann's rule: the linked layer's source at
 *    the stroke's start, or [Effect.BrushStrokes.color], lightened or darkened by up to 45%
 *    of the randomness. Strokes are painted over each other in order.
 * 5. Distance blur. The painted strokes are blurred and mixed in by the distance from the
 *    icon over the stroke length, so marks are crisp at the icon and soft at their reach.
 * 6. Holes. Nothing shows in the icon's enclosed counters, which keeps the glyph legible.
 *
 * Lengths are fractions of the canvas, every random choice comes from the per-icon offset
 * and the seed through SplitMix64, and the arithmetic is plain float with StrictMath for
 * trigonometry, so the strokes are the same at every size and on every platform.
 */
internal object BrushStrokes {

    fun paint(e: Effect.BrushStrokes, blend: BlendMode, layers: List<Layer>, input: StackInput): EffectOutput {
        val n = input.size
        val px = n.toFloat()
        val pixels = n * n
        val icon = EffectSource.icon(input)
        val cov = FloatArray(pixels)
        val colors = FloatArray(4 * pixels)
        val parallel = e.direction == StrokeDirection.PARALLEL
        if (!parallel && icon.none { it > 0f }) return EffectOutput(cov, null, colors, blend)

        val reach = e.length * px
        val band = ceil(reach) + 2f
        var sd = EffectSource.signedDistance(input, band)
        var flow = EffectSource.blurred(input, (FLOW_BASE + FLOW_PER_LENGTH * e.length) * px)
        // Outward from another point: the icon's outline, and so the burst, moved there.
        val mx = (e.originX - 0.5f) * px
        val my = (e.originY - 0.5f) * px
        if (!parallel && (mx != 0f || my != 0f)) {
            val lifted = FloatArray(pixels) { sd[it] + band }
            sd = EffectPaint.shifted(lifted, n, mx, my).also { for (i in it.indices) it[i] -= band }
            flow = EffectPaint.shifted(flow, n, mx, my)
        }
        val strokes = if (parallel) placeParallel(e, input.offset, n) else place(e, input.offset, sd, flow, n)

        // One colour per stroke from the source at its start.
        val source = layers.firstOrNull { it.id == e.paintLayer } as? PaintLayer
        val sourceColors = source?.let { s ->
            val need = FloatArray(pixels)
            for (st in strokes) need[st.startPixel(n)] = 1f
            StackEvaluator.sourceColors(s, input, need)
        }
        val solid = EffectPaint.solid(e.color)
        val prem = FloatArray(4 * pixels)
        val local = FloatArray(pixels)
        for (st in strokes) {
            val c = FloatArray(4)
            if (sourceColors != null) {
                val o = 4 * st.startPixel(n)
                for (k in 0..3) c[k] = sourceColors[o + k]
            } else {
                for (k in 0..3) c[k] = solid[k]
            }
            for (k in 0..2) c[k] = minOf(c[3], c[k] * st.tone)
            raster(e, st, n, local, cov, prem, c)
        }

        // All one way, the blur grows toward each stroke's tip as it is drawn.
        if (e.blur > 0f && !parallel) distanceBlur(e, sd, reach, n, cov, prem)
        // Swipes run behind the icon: its counters show them as they show the background.
        val holes = if (parallel) BooleanArray(pixels) else holes(icon, n)

        for (i in 0 until pixels) {
            val a = if (holes[i]) 0f else cov[i]
            if (a <= MIN_COVERAGE) {
                cov[i] = 0f
                continue
            }
            val o = 4 * i
            val inv = 1f / a
            for (k in 0..3) colors[o + k] = minOf(1f, prem[o + k] * inv)
        }
        return EffectOutput(cov, null, colors, blend)
    }

    /**
     * The icon's holes: background, below half coverage, 4-connected, that does not reach
     * the canvas edge. Strokes never show there, so the counters of an invert fill (an
     * eye, a terminal prompt) stay clean.
     */
    private fun holes(icon: FloatArray, n: Int): BooleanArray {
        val bg = BooleanArray(n * n) { icon[it] < 0.5f }
        val l = label4(bg, n, n)
        val open = BooleanArray(l.count + 1)
        for (k in 0 until n) {
            open[l.labels[k]] = true
            open[l.labels[(n - 1) * n + k]] = true
            open[l.labels[k * n]] = true
            open[l.labels[k * n + n - 1]] = true
        }
        return BooleanArray(n * n) { bg[it] && !open[l.labels[it]] }
    }

    /** A traced stroke: [STEPS] + 1 points in pixels, its base half-width, tone and bristle field. */
    private class Stroke(val xs: FloatArray, val ys: FloatArray, val halfWidth: Float, val tone: Float, val bristleSeed: Int, val bristleShift: Float, val coarse: Boolean) {
        fun startPixel(n: Int): Int {
            val i = floor(xs[0]).toInt().coerceIn(0, n - 1)
            val j = floor(ys[0]).toInt().coerceIn(0, n - 1)
            return j * n + i
        }
    }

    private fun place(
        e: Effect.BrushStrokes, offset: TextureOffset, sd: FloatArray, flow: FloatArray, n: Int,
    ): List<Stroke> {
        val px = n.toFloat()
        val r = e.randomness
        val seed = IconSeed.mix64((offset.x.toRawBits().toLong() shl 32) xor (offset.y.toRawBits().toLong() and 0xFFFFFFFFL)) xor e.seed.toLong()
        val rng = SplitMix64(seed)
        val band = maxOf(1f, EDGE_BAND * e.width * px)
        val (wx, wy) = EffectPaint.toward(e.angle)
        val strokes = ArrayList<Stroke>(e.count)
        var tries = 0
        while (strokes.size < e.count && tries < e.count * MAX_TRIES) {
            tries++
            val cx = rng.nextFloat() * px
            val cy = rng.nextFloat() * px
            val own = SplitMix64(rng.nextLong())
            val d = Smear.bilinear(sd, n, cx - 0.5f, cy - 0.5f)
            if (d < -0.5f || d > band) continue

            // Hertzmann's multiple sizes: a share of the strokes is a coarse pass, wider and shorter, painted first.
            val coarse = own.nextFloat() < COARSE_SHARE
            val len = e.length * px * (1f + r * (own.nextFloat() - 0.5f) * 1.2f) * (if (coarse) COARSE_LENGTH else 1f)
            val hw = 0.5f * e.width * px * (1f + r * (own.nextFloat() - 0.5f) * 1.2f) * (if (coarse) COARSE_WIDTH else 1f)
            val jitter = r * (own.nextFloat() - 0.5f) * JITTER
            val turn = e.bend * BEND * (0.6f + 0.8f * own.nextFloat()) / STEPS
            val tone = 1f + r * TONE * (2f * own.nextFloat() - 1f)
            val bristleSeed = own.nextLong().toInt()
            val bristleShift = own.nextFloat() * 64f
            val (jc, js) = cosSin(jitter)

            val xs = FloatArray(STEPS + 1)
            val ys = FloatArray(STEPS + 1)
            xs[0] = cx
            ys[0] = cy
            val h = len / STEPS
            var dx = 0f
            var dy = 0f
            outward(flow, n, cx, cy)?.let { (ox, oy) -> dx = ox; dy = oy }
            if (dx == 0f && dy == 0f) { dx = wx; dy = wy }
            for (k in 1..STEPS) {
                var tx = dx
                var ty = dy
                outward(flow, n, xs[k - 1], ys[k - 1])?.let { (ox, oy) -> tx = ox; ty = oy }
                if (e.drift > 0f) {
                    tx = tx * (1f - e.drift) + wx * e.drift
                    ty = ty * (1f - e.drift) + wy * e.drift
                }
                // The per-stroke offset turns the target, so it persists through the filter.
                val rx = tx * jc - ty * js
                val ry = tx * js + ty * jc
                var nx = dx * (1f - CURVATURE) + rx * CURVATURE
                var ny = dy * (1f - CURVATURE) + ry * CURVATURE
                val (wc, ws) = cosSin(turn + r * (own.nextFloat() - 0.5f) * WOBBLE)
                val qx = nx * wc - ny * ws
                val qy = nx * ws + ny * wc
                val m = sqrt(qx * qx + qy * qy)
                if (m > 1e-6f) { nx = qx / m; ny = qy / m } else { nx = dx; ny = dy }
                dx = nx
                dy = ny
                xs[k] = xs[k - 1] + dx * h
                ys[k] = ys[k - 1] + dy * h
            }
            // A stroke that ends near the icon is trapped in a hole or crawled along an edge: not a flick off it.
            if (-Smear.bilinear(sd, n, xs[STEPS] - 0.5f, ys[STEPS] - 0.5f) < ESCAPE * len) continue
            strokes += Stroke(xs, ys, hw, tone, bristleSeed, bristleShift, coarse)
        }
        // The coarse pass first, the fine strokes over it; stable, so each pass keeps its order.
        return strokes.filter { it.coarse } + strokes.filter { !it.coarse }
    }

    /**
     * All one way: [Effect.BrushStrokes.count] strokes along the angle, spread evenly across
     * a band through the origin, `count width 1.6` of the canvas wide up to 1.3, centred on
     * the origin along their length. Each draws its own numbers in the order [place] does,
     * then its place across and along the band. They bend, wobble and turn by the
     * randomness as outward strokes do, with the angle as the field they follow.
     */
    private fun placeParallel(e: Effect.BrushStrokes, offset: TextureOffset, n: Int): List<Stroke> {
        val px = n.toFloat()
        val r = e.randomness
        val seed = IconSeed.mix64((offset.x.toRawBits().toLong() shl 32) xor (offset.y.toRawBits().toLong() and 0xFFFFFFFFL)) xor e.seed.toLong()
        val rng = SplitMix64(seed)
        val (wx, wy) = EffectPaint.toward(e.angle)
        val ax = -wy
        val ay = wx
        val span = minOf(MAX_SPAN, e.count * e.width * SPAN_PER_WIDTH) * px
        val strokes = ArrayList<Stroke>(e.count)
        for (i in 0 until e.count) {
            val own = SplitMix64(rng.nextLong())
            val coarse = own.nextFloat() < COARSE_SHARE
            val len = e.length * px * (1f + r * (own.nextFloat() - 0.5f) * 1.2f) * (if (coarse) COARSE_LENGTH else 1f)
            val hw = 0.5f * e.width * px * (1f + r * (own.nextFloat() - 0.5f) * 1.2f) * (if (coarse) COARSE_WIDTH else 1f)
            val jitter = r * (own.nextFloat() - 0.5f) * JITTER * PARALLEL_JITTER
            val turn = e.bend * BEND * (0.6f + 0.8f * own.nextFloat()) / STEPS
            val tone = 1f + r * TONE * (2f * own.nextFloat() - 1f)
            val bristleSeed = own.nextLong().toInt()
            val bristleShift = own.nextFloat() * 64f
            val across = ((i + 0.5f) / e.count - 0.5f) * span + r * (own.nextFloat() - 0.5f) * span / e.count
            val along = r * (own.nextFloat() - 0.5f) * ALONG_JITTER * len
            val (jc, js) = cosSin(jitter)
            val xs = FloatArray(STEPS + 1)
            val ys = FloatArray(STEPS + 1)
            xs[0] = e.originX * px + ax * across - wx * (len * 0.5f - along)
            ys[0] = e.originY * px + ay * across - wy * (len * 0.5f - along)
            val h = len / STEPS
            var dx = wx * jc - wy * js
            var dy = wx * js + wy * jc
            for (k in 1..STEPS) {
                var nx = dx
                var ny = dy
                val (wc, ws) = cosSin(turn + r * (own.nextFloat() - 0.5f) * WOBBLE * PARALLEL_JITTER)
                val qx = nx * wc - ny * ws
                val qy = nx * ws + ny * wc
                val m = sqrt(qx * qx + qy * qy)
                if (m > 1e-6f) { nx = qx / m; ny = qy / m }
                dx = nx
                dy = ny
                xs[k] = xs[k - 1] + dx * h
                ys[k] = ys[k - 1] + dy * h
            }
            strokes += Stroke(xs, ys, hw, tone, bristleSeed, bristleShift, coarse)
        }
        return strokes.filter { it.coarse } + strokes.filter { !it.coarse }
    }

    /**
     * The unit direction away from the icon at pixel position (x, y), y down: the negated
     * gradient of the blurred mask by central differences. Null where the field is flat.
     */
    private fun outward(flow: FloatArray, n: Int, x: Float, y: Float): Pair<Float, Float>? {
        val ix = x - 0.5f
        val iy = y - 0.5f
        val gx = Smear.bilinear(flow, n, ix + 1f, iy) - Smear.bilinear(flow, n, ix - 1f, iy)
        val gy = Smear.bilinear(flow, n, ix, iy + 1f) - Smear.bilinear(flow, n, ix, iy - 1f)
        val m = sqrt(gx * gx + gy * gy)
        if (m < FLAT) return null
        return -gx / m to -gy / m
    }

    /**
     * Paints [st] into [local] as the maximum of its capsules, then over [cov] and [prem]
     * with premultiplied colour [c], and clears [local] behind it.
     */
    private fun raster(e: Effect.BrushStrokes, st: Stroke, n: Int, local: FloatArray, cov: FloatArray, prem: FloatArray, c: FloatArray) {
        val xs = st.xs
        val ys = st.ys
        val margin = st.halfWidth * (1f + SOFT_BAND * e.softness) + 2f
        var sx0 = n
        var sy0 = n
        var sx1 = -1
        var sy1 = -1
        for (k in 0..STEPS) {
            sx0 = minOf(sx0, floor(xs[k] - margin).toInt())
            sy0 = minOf(sy0, floor(ys[k] - margin).toInt())
            sx1 = maxOf(sx1, ceil(xs[k] + margin).toInt())
            sy1 = maxOf(sy1, ceil(ys[k] + margin).toInt())
        }
        sx0 = sx0.coerceAtLeast(0)
        sy0 = sy0.coerceAtLeast(0)
        sx1 = sx1.coerceAtMost(n - 1)
        sy1 = sy1.coerceAtMost(n - 1)
        if (sx0 > sx1 || sy0 > sy1) return

        val fadeExp = FADE * e.fade
        val dryWidth = DRY_EDGE + DRY_SOFT * e.softness
        val painted = e.style == StrokeStyle.PAINTED
        val tipBlur = if (e.direction == StrokeDirection.PARALLEL) e.blur * TIP_BLUR else 0f
        for (k in 0 until STEPS) {
            val ax = xs[k]
            val ay = ys[k]
            val ex = xs[k + 1] - ax
            val ey = ys[k + 1] - ay
            val l2 = ex * ex + ey * ey
            val ta = k.toFloat() / STEPS
            val tb = (k + 1).toFloat() / STEPS
            val ha = st.halfWidth * if (painted) paintedWidth(st, ta) else taper(ta)
            val hb = st.halfWidth * if (painted) paintedWidth(st, tb) else taper(tb)
            // Painted: the load holds, then runs out over the last two thirds; the bristles break it up.
            val pa = if (painted) 1f - e.fade * PAINTED_FADE * smoothstep(0.3f, 1f, ta) else pow(1f - ta, fadeExp)
            val pb = if (painted) 1f - e.fade * PAINTED_FADE * smoothstep(0.3f, 1f, tb) else pow(1f - tb, fadeExp)
            val m = maxOf(ha, hb) * (1f + SOFT_BAND * e.softness + tipBlur + (if (painted) RAGGED else 0f)) + 1.5f
            val i0 = maxOf(sx0, floor(minOf(ax, ax + ex) - m).toInt())
            val i1 = minOf(sx1, ceil(maxOf(ax, ax + ex) + m).toInt())
            val j0 = maxOf(sy0, floor(minOf(ay, ay + ey) - m).toInt())
            val j1 = minOf(sy1, ceil(maxOf(ay, ay + ey) + m).toInt())
            for (j in j0..j1) for (i in i0..i1) {
                val qx = i + 0.5f - ax
                val qy = j + 0.5f - ay
                val u = if (l2 > 0f) ((qx * ex + qy * ey) / l2).coerceIn(0f, 1f) else 0f
                val ddx = qx - u * ex
                val ddy = qy - u * ey
                val dist = sqrt(ddx * ddx + ddy * ddy)
                var hw = ha + (hb - ha) * u
                val t = ta + (tb - ta) * u
                var soft = 1f + SOFT_BAND * e.softness * hw
                if (tipBlur > 0f) soft += tipBlur * t * hw
                if (painted && l2 > 0f) {
                    // Ragged edges: each side of the stroke frays by its own noise along it.
                    val side = (qx * ey - qy * ex)
                    val fray = Noise.value(t * FRAY_ALONG + st.bristleShift, if (side > 0f) 0.5f else 7.5f, st.bristleSeed xor FRAY_SALT)
                    hw *= 1f - RAGGED * (1f - e.softness * 0.5f) * fray
                }
                val edge = ((hw - dist) / soft + 0.5f).coerceIn(0f, 1f)
                if (edge <= 0f) continue
                var v = edge * (pa + (pb - pa) * u)
                if (painted && e.bristles > 0f && l2 > 0f) {
                    val side = (qx * ey - qy * ex) / sqrt(l2)
                    val s = (side / maxOf(hw, 0.5f)).coerceIn(-1f, 1f)
                    // Bristles along the stroke: each its own load, long streaks, the tail breaking up first.
                    val lane = (s * 0.5f + 0.5f) * PAINTED_BRISTLES + st.bristleShift
                    val load = Noise.value(lane, 0.5f, st.bristleSeed xor LOAD_SALT)
                    val b = Noise.value(lane, t * PAINTED_ALONG, st.bristleSeed)
                    // Lanes that carry little paint leave dark streaks the whole way along.
                    v *= 1f - e.bristles * PAINTED_STREAK * (1f - smoothstep(0.2f, 0.6f, b)) * (1.2f - load)
                    val th = e.bristles * ((t - PAINTED_DRY_START) / (1f - PAINTED_DRY_START)).coerceIn(0f, 1f) * (1.3f - 0.6f * load)
                    if (th > 0f) v *= smoothstep(th - dryWidth, th + dryWidth, b * 0.6f + load * 0.4f)
                    // Paint pooled where the brush came down.
                    v = minOf(1f, v * (1f + POOL * pow(1f - t, 6f)))
                } else if (e.bristles > 0f && l2 > 0f) {
                    // Signed position across the stroke, -1 to 1.
                    val side = (qx * ey - qy * ex) / sqrt(l2)
                    val s = (side / maxOf(hw, 0.5f)).coerceIn(-1f, 1f)
                    val b = Noise.value((s * 0.5f + 0.5f) * BRISTLE_COUNT + st.bristleShift, t * BRISTLE_ALONG, st.bristleSeed)
                    // The loaded body carries a faint streak; past DRY_START the bristles run dry one by one.
                    v *= 1f - e.bristles * STREAK * (1f - b)
                    val th = e.bristles * ((t - DRY_START) / (1f - DRY_START)).coerceIn(0f, 1f)
                    if (th > 0f) v *= smoothstep(th - dryWidth, th + dryWidth, b)
                }
                val idx = j * n + i
                if (v > local[idx]) local[idx] = v
            }
        }
        for (j in sy0..sy1) for (i in sx0..sx1) {
            val idx = j * n + i
            val a = local[idx]
            if (a <= 0f) continue
            local[idx] = 0f
            // Painted: wet paint over wet paint darkens where strokes cross.
            val dark = if (painted) 1f - OVERLAP * cov[idx] else 1f
            cov[idx] = cov[idx] + a - cov[idx] * a
            val o = 4 * idx
            val keep = 1f - a
            for (ch in 0..2) prem[o + ch] = prem[o + ch] * keep + c[ch] * dark * a
            prem[o + 3] = prem[o + 3] * keep + c[3] * a
        }
    }

    /** Mixes in a blurred copy of the strokes by distance from the icon: none on it, all at [reach]. */
    private fun distanceBlur(e: Effect.BrushStrokes, sd: FloatArray, reach: Float, n: Int, cov: FloatArray, prem: FloatArray) {
        val pixels = n * n
        val sigma = e.blur * BLUR_PER_LENGTH * reach
        if (sigma < 0.5f) return
        val bc = Blur.gaussian(cov, n, n, sigma)
        val channel = FloatArray(pixels)
        val blurred = Array(4) { ch ->
            for (i in 0 until pixels) channel[i] = prem[4 * i + ch]
            Blur.gaussian(channel, n, n, sigma)
        }
        for (i in 0 until pixels) {
            val out = -sd[i]
            if (out <= 0f) continue
            val w = smoothstep(0f, reach, out) * e.blur
            cov[i] = cov[i] + (bc[i].coerceIn(0f, 1f) - cov[i]) * w
            val o = 4 * i
            for (ch in 0..3) prem[o + ch] = prem[o + ch] + (blurred[ch][i].coerceIn(0f, 1f) - prem[o + ch]) * w
        }
    }

    /**
     * Painted: pressed down from a third of the width to full by a tenth of the way, a
     * slow swell and pinch along the stroke from its own noise, then lifted off.
     */
    private fun paintedWidth(st: Stroke, t: Float): Float {
        val press = PRESS + (1f - PRESS) * smoothstep(0f, 0.08f, t)
        val swell = 1f + SWELL * (Noise.value(t * SWELL_ALONG + st.bristleShift, 3.5f, st.bristleSeed xor FRAY_SALT) * 2f - 1f)
        val lift = 1f - LIFT * smoothstep(0.65f, 1f, t)
        return press * swell * lift
    }

    /** Pressed on at the icon, full at a quarter of the way, lifted off to a point. */
    private fun taper(t: Float): Float = (BASE + (1f - BASE) * smoothstep(0f, 0.25f, t)) * pow(1f - t, 0.6f)

    private fun pow(x: Float, p: Float): Float =
        if (p == 0f) 1f else if (x <= 0f) 0f else StrictMath.pow(x.toDouble(), p.toDouble()).toFloat()

    private fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun cosSin(a: Float): Pair<Float, Float> =
        StrictMath.cos(a.toDouble()).toFloat() to StrictMath.sin(a.toDouble()).toFloat()

    /** The coarse pass: its share of the strokes, and their width and length against the rest. */
    private const val COARSE_SHARE = 0.3f
    private const val COARSE_WIDTH = 1.9f
    private const val COARSE_LENGTH = 0.8f

    /** A stroke must end at least this share of its length away from the icon. */
    private const val ESCAPE = 0.35f

    /** The taper's width at the base, where the brush is set down. */
    private const val BASE = 0.6f

    /** Steps per stroke, at every size. */
    private const val STEPS = 24

    /** Candidates tried per stroke before giving up: thin icons have little edge. */
    private const val MAX_TRIES = 400

    /** Start points lie inside the edge by at most this fraction of the stroke width. */
    private const val EDGE_BAND = 0.5f

    /** The direction field's blur, sigma as a fraction of the canvas: a base plus a share of the length. */
    private const val FLOW_BASE = 0.02f
    private const val FLOW_PER_LENGTH = 0.15f

    /** Below this gradient the field is flat and the stroke keeps its heading. */
    private const val FLAT = 1e-5f

    /** Hertzmann's curvature filter f_c: how much of the field each step follows. */
    private const val CURVATURE = 0.35f

    /** At randomness 1: the widest per-stroke turn, the per-step wobble, in radians, and the tone swing either way. */
    private const val JITTER = 1.3f
    private const val WOBBLE = 0.35f
    private const val TONE = 0.45f

    /** Radians a stroke turns over its length at bend 1. */
    private const val BEND = 2.4f

    /** Paint runs out as (1 - t)^(FADE fade). */
    private const val FADE = 3f

    /** The soft edge's width: 1 px plus this times the half-width at softness 1. */
    private const val SOFT_BAND = 1.5f

    /** Bristles across a stroke, and how fast each changes along it. */
    private const val BRISTLE_COUNT = 6f
    private const val BRISTLE_ALONG = 1.5f

    /** Where along the stroke the bristles start to run dry, and how deep the streaks in the loaded body are. */
    private const val DRY_START = 0.35f
    private const val STREAK = 0.35f

    /** Half-width of a bristle's edge in noise units, plus more with softness. */
    private const val DRY_EDGE = 0.05f
    private const val DRY_SOFT = 0.25f

    /** Distance blur at blur 1: sigma as a fraction of the stroke length in pixels. */
    private const val BLUR_PER_LENGTH = 0.15f

    /** All one way: the band's width per stroke width, and its widest, fractions of the canvas. */
    private const val SPAN_PER_WIDTH = 1.6f
    private const val MAX_SPAN = 1.3f
    /** All one way: how much of the outward turn and wobble a parallel stroke takes, and its slide along the band. */
    private const val PARALLEL_JITTER = 0.25f
    private const val ALONG_JITTER = 0.4f
    /** All one way: the soft edge at the tip at blur 1, in half-widths. */
    private const val TIP_BLUR = 2.5f

    /** Painted: the width at touch-down, the swell either way and its rate, fraying, overlap darkening, pooled paint. */
    private const val PRESS = 0.35f
    private const val SWELL = 0.14f
    private const val SWELL_ALONG = 2.5f
    private const val RAGGED = 0.22f
    private const val FRAY_ALONG = 22f
    private const val OVERLAP = 0.22f
    private const val POOL = 0.35f
    private const val LIFT = 0.85f
    private const val PAINTED_FADE = 0.7f
    /** Painted bristles: lanes across, rate along, streak depth, and where they start to run dry. */
    private const val PAINTED_BRISTLES = 14f
    private const val PAINTED_ALONG = 0.7f
    private const val PAINTED_STREAK = 0.55f
    private const val PAINTED_DRY_START = 0.2f
    private const val FRAY_SALT = 0x3C6EF372
    private const val LOAD_SALT = 0x0A54FF53

    /** Coverage below this is dropped: under half an 8-bit step. */
    private const val MIN_COVERAGE = 1f / 512
}
