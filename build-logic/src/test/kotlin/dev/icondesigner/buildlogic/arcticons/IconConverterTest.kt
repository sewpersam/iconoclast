package dev.icondesigner.buildlogic.arcticons

import dev.icondesigner.render.format.Icon
import dev.icondesigner.render.format.Primitive
import dev.icondesigner.render.format.Verb
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IconConverterTest {

    private fun svg(body: String, css: String = "", root: String = "viewBox=\"0 0 48 48\"") =
        """<?xml version="1.0" encoding="UTF-8"?><svg xmlns="http://www.w3.org/2000/svg" $root>""" +
            (if (css.isEmpty()) "" else "<defs><style>$css</style></defs>") + body + "</svg>"

    private fun convert(text: String, stats: ConversionStats = ConversionStats()): Icon = convertSvg(text, stats)

    private fun Primitive.points(): List<Pair<Float, Float>> = coords.toList().chunked(2) { it[0] to it[1] }

    private fun assertNear(expected: Float, actual: Float, eps: Float = 1e-3f) =
        assertTrue(kotlin.math.abs(expected - actual) <= eps, "expected $expected, got $actual")

    private val base = ".s{fill:none;stroke:#fff;stroke-linecap:round;stroke-linejoin:round;}"

    @Test
    fun classResolvesStrokeWithRoundCapsAndJoins() {
        val p = convert(svg("""<line class="s" x1="1" y1="2" x2="3" y2="4"/>""", base)).primitives.single()
        assertTrue(p.isStroked)
        assertFalse(p.isFilled)
        assertEquals(Primitive.CAP_ROUND, p.cap)
        assertEquals(Primitive.JOIN_ROUND, p.join)
        assertEquals(1f, p.strokeWidth)
        assertEquals(4f, p.miterLimit)
        assertEquals(listOf(Verb.MOVE, Verb.LINE), p.verbs.toList())
        assertEquals(listOf(1f to 2f, 3f to 4f), p.points())
    }

    @Test
    fun higherSpecificityWinsOverLaterRule() {
        val css = "line.s{stroke-width:3px} .s{fill:none;stroke:#fff;stroke-width:2px}"
        val p = convert(svg("""<line class="s" x1="0" y1="0" x2="1" y2="1"/>""", css)).primitives.single()
        assertEquals(3f, p.strokeWidth)
    }

    @Test
    fun idSelectorBeatsClassAndLaterEqualRuleWins() {
        val css = "#x{stroke-width:5} .a{stroke:#fff;fill:none;stroke-width:2} .b{stroke-width:4}"
        val icon = convert(
            svg(
                """<line id="x" class="a b" x1="0" y1="0" x2="1" y2="1"/>""" +
                    """<line class="a b" x1="0" y1="0" x2="1" y2="1"/>""",
                css,
            ),
        )
        assertEquals(listOf(5f, 4f), icon.primitives.map { it.strokeWidth })
    }

    @Test
    fun spacedCssAndGroupedSelectorsParse() {
        val css = ".a, .b { fill: none; stroke: #fff; stroke-width: 2px }"
        val icon = convert(svg("""<line class="b" x1="0" y1="0" x2="1" y2="1"/>""", css))
        assertEquals(2f, icon.primitives.single().strokeWidth)
    }

    @Test
    fun inlineStyleOverridesCss() {
        val p = convert(
            svg("""<line class="s" style="stroke-width:2.5;stroke-linecap:butt" x1="0" y1="0" x2="1" y2="1"/>""", base),
        ).primitives.single()
        assertEquals(2.5f, p.strokeWidth)
        assertEquals(Primitive.CAP_BUTT, p.cap)
    }

    @Test
    fun presentationAttributeAppliesButLosesToCss() {
        val icon = convert(
            svg(
                """<line fill="none" stroke="#fff" stroke-width="2" x1="0" y1="0" x2="1" y2="1"/>""" +
                    """<line class="s" stroke-width="2" stroke-linecap="butt" x1="0" y1="0" x2="1" y2="1"/>""",
                base,
            ),
        )
        val (attrOnly, overridden) = icon.primitives
        assertTrue(attrOnly.isStroked)
        assertEquals(2f, attrOnly.strokeWidth)
        assertEquals(Primitive.CAP_BUTT, attrOnly.cap)
        // CSS sets the cap to round, beating the attribute; width comes from the attribute.
        assertEquals(Primitive.CAP_ROUND, overridden.cap)
        assertEquals(2f, overridden.strokeWidth)
    }

    @Test
    fun groupStyleAndRootAttributesInherit() {
        val text = svg(
            """<g stroke-width="3"><path d="M1 1L2 2"/></g>""",
            root = """viewBox="0 0 48 48" fill="none" stroke="#fff" stroke-linecap="round"""",
        )
        val p = convert(text).primitives.single()
        assertTrue(p.isStroked)
        assertFalse(p.isFilled)
        assertEquals(3f, p.strokeWidth)
        assertEquals(Primitive.CAP_ROUND, p.cap)
    }

    @Test
    fun everyElementTypeConverts() {
        val body = """
            <path class="s" d="M1 1L5 5"/>
            <line class="s" x1="1" y1="1" x2="5" y2="5"/>
            <rect class="s" x="1" y="1" width="4" height="4"/>
            <circle class="s" cx="10" cy="10" r="2"/>
            <ellipse class="s" cx="10" cy="10" rx="3" ry="2"/>
            <polyline class="s" points="1,1 2,2 3,1"/>
            <polygon class="s" points="1 1 2 2 3 1"/>
        """
        val stats = ConversionStats()
        val verbs = convert(svg(body, base), stats).primitives.map { it.verbs.toList() }
        val M = Verb.MOVE; val L = Verb.LINE; val C = Verb.CUBIC; val Z = Verb.CLOSE
        assertEquals(
            listOf(
                listOf(M, L),
                listOf(M, L),
                listOf(M, L, L, L, Z),
                listOf(M, C, C, C, C, Z),
                listOf(M, C, C, C, C, Z),
                listOf(M, L, L),
                listOf(M, L, L, Z),
            ),
            verbs,
        )
        for (tag in SHAPE_TAGS) assertEquals(1, stats.counts["element.$tag"], tag)
    }

    @Test
    fun circleAndEllipseHitTheirExtremes() {
        val icon = convert(svg("""<circle class="s" cx="10" cy="20" r="3"/><ellipse class="s" cx="0" cy="0" rx="4" ry="2"/>""", base))
        val circle = icon.primitives[0].points()
        assertEquals(listOf(13f to 20f, 10f to 23f, 7f to 20f, 10f to 17f, 13f to 20f), listOf(circle[0], circle[3], circle[6], circle[9], circle[12]))
        val ellipse = icon.primitives[1].points()
        assertEquals(4f to 0f, ellipse[0])
        assertEquals(0f to 2f, ellipse[3])
    }

    @Test
    fun roundedRectUsesRxForMissingRyAndClamps() {
        val p = convert(svg("""<rect class="s" x="0" y="0" width="10" height="4" rx="3"/>""", base)).primitives.single()
        val M = Verb.MOVE; val L = Verb.LINE; val C = Verb.CUBIC
        assertEquals(listOf(M, L, C, L, C, L, C, L, C, Verb.CLOSE), p.verbs.toList())
        val pts = p.points()
        // rx 3, ry defaults to rx then clamps to height/2 = 2.
        assertEquals(3f to 0f, pts[0])
        assertEquals(7f to 0f, pts[1])
        assertEquals(10f to 2f, pts[4])
    }

    @Test
    fun rectRadiusClampsToHalfWidth() {
        val p = convert(svg("""<rect class="s" x="0" y="0" width="4" height="10" rx="5" ry="5"/>""", base)).primitives.single()
        assertEquals(2f to 0f, p.points()[0])
    }

    @Test
    fun arcBecomesCubicsOnTheCircle() {
        // Half circle of radius 5 from (0,10) to (10,10) through (5,5) or (5,15) depending on sweep.
        val p = convert(svg("""<path class="s" d="M0 10A5 5 0 0 1 10 10"/>""", base)).primitives.single()
        assertEquals(listOf(Verb.MOVE, Verb.CUBIC, Verb.CUBIC), p.verbs.toList())
        val pts = p.points()
        assertEquals(10f to 10f, pts.last())
        assertNear(5f, pts[3].first)
        assertNear(5f, pts[3].second)
        // Midpoint of the first quarter lies on the circle.
        val (x0, y0) = pts[0]; val (x1, y1) = pts[1]; val (x2, y2) = pts[2]; val (x3, y3) = pts[3]
        val mx = (x0 + 3 * x1 + 3 * x2 + x3) / 8
        val my = (y0 + 3 * y1 + 3 * y2 + y3) / 8
        assertNear(5f, hypot(mx - 5f, my - 10f), 2e-3f)
    }

    @Test
    fun relativeArcWithCompactFlagsAndRadiusScaling() {
        // Flags written as "01" with no separator, radius too small so it scales up.
        val p = convert(svg("""<path class="s" d="m0,10a1,1,0,01,10,0"/>""", base)).primitives.single()
        val pts = p.points()
        assertEquals(10f to 10f, pts.last())
        assertNear(5f, pts[3].first)
        assertNear(5f, pts[3].second)
    }

    @Test
    fun pathCommandsAreAbsoluteAndImplicit() {
        val d = "m1,1 2,0 h2 v2 H1 V5 z l1-1 q1,0 1,1 t1,1 s1,1 2,2 c0-1 1-1 1 0 .5.5.5.5.5.5"
        val p = convert(svg("""<path class="s" d="$d"/>""", base)).primitives.single()
        val M = Verb.MOVE; val L = Verb.LINE; val C = Verb.CUBIC; val Z = Verb.CLOSE
        assertEquals(listOf(M, L, L, L, L, L, Z, M, L, C, C, C, C, C), p.verbs.toList())
        val pts = p.points()
        assertEquals(listOf(1f to 1f, 3f to 1f, 5f to 1f, 5f to 3f, 1f to 3f, 1f to 5f, 1f to 1f, 2f to 0f), pts.take(8))
        // q1,0 1,1 from (2,0): end (3,1).
        assertEquals(3f to 1f, pts[10])
    }

    @Test
    fun transformsAreBaked() {
        val body = """
            <line class="s" x1="0" y1="0" x2="1" y2="0" transform="translate(10 20)"/>
            <line class="s" x1="0" y1="0" x2="1" y2="0" transform="matrix(2 0 0 2 1 1)"/>
            <line class="s" x1="1" y1="0" x2="2" y2="0" transform="rotate(90)"/>
            <rect class="s" x="0" y="0" width="1" height="1" transform="translate(-2.7 3.3) rotate(-6.9)"/>
            <g transform="translate(5)"><line class="s" x1="0" y1="0" x2="1" y2="0" transform="scale(2)"/></g>
        """
        val stats = ConversionStats()
        val ps = convert(svg(body, base), stats).primitives
        assertEquals(listOf(10f to 20f, 11f to 20f), ps[0].points())
        assertEquals(listOf(1f to 1f, 3f to 1f), ps[1].points())
        assertEquals(2f, ps[1].strokeWidth)
        assertNear(0f, ps[2].points()[0].first)
        assertNear(1f, ps[2].points()[0].second)
        assertNear(-2.7f, ps[3].points()[0].first)
        assertEquals(listOf(5f to 0f, 7f to 0f), ps[4].points())
        assertEquals(2f, ps[4].strokeWidth)
        assertEquals(6, stats.counts["transform.baked"])
    }

    @Test
    fun filledDotIsFlaggedFillOnly() {
        val css = base + ".d{fill:#fff;stroke-width:0px;}"
        val p = convert(svg("""<circle class="d" cx="5" cy="5" r=".8"/>""", css)).primitives.single()
        assertTrue(p.isFilled)
        assertFalse(p.isStroked)
    }

    @Test
    fun filledAndStrokedKeepsBothFlags() {
        val css = ".d{fill:#fff;stroke:#fff;stroke-width:1px;}"
        val p = convert(svg("""<circle class="d" cx="5" cy="5" r="1"/>""", css)).primitives.single()
        assertTrue(p.isFilled && p.isStroked)
    }

    @Test
    fun unstyledShapeGetsSvgDefaultBlackFill() {
        val stats = ConversionStats()
        val p = convert(svg("""<rect x="0" y="0" width="2" height="2"/>"""), stats).primitives.single()
        assertTrue(p.isFilled)
        assertFalse(p.isStroked)
        assertEquals(1, stats.counts["style.defaultBlackFill"])
    }

    @Test
    fun evenOddFillRuleIsRecorded() {
        val css = ".e{fill:#fff;fill-rule:evenodd;stroke-width:0px}"
        val p = convert(svg("""<path class="e" d="M0 0H10V10H0ZM2 2H8V8H2Z"/>""", css)).primitives.single()
        assertTrue(p.isEvenOdd)
        assertTrue(p.isFilled)
    }

    @Test
    fun evenOddIsKeptOnStrokedOnlyPrimitives() {
        val css = ".m{fill-rule:evenodd;}.m,.n{fill:none;stroke:#fff}"
        val (m, n) = convert(svg("""<path class="m" d="M0 0H9V9Z"/><path class="n" d="M0 0H9V9Z"/>""", css)).primitives
        assertTrue(m.isEvenOdd && !m.isFilled)
        assertFalse(n.isEvenOdd)
    }

    @Test
    fun miterLimitIsRecorded() {
        val css = ".m{fill:none;stroke:#fff;stroke-miterlimit:10}"
        assertEquals(10f, convert(svg("""<path class="m" d="M0 0L5 5L10 0"/>""", css)).primitives.single().miterLimit)
    }

    @Test
    fun metadataAndInkscapeCruftAreStripped() {
        val text = """<svg xmlns="http://www.w3.org/2000/svg" xmlns:sodipodi="http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd"
            xmlns:c2pa="http://c2pa.org/manifest" viewBox="0 0 48 48">
            <metadata><c2pa:manifest>AAAA</c2pa:manifest><path d="M0 0L9 9" stroke="#fff"/></metadata>
            <sodipodi:namedview pagecolor="#fff"/><title>x</title><desc>y</desc>
            <defs><style>$base</style><path id="unused" d="M0 0L1 1" class="s"/></defs>
            <line class="s" x1="0" y1="0" x2="1" y2="1"/></svg>"""
        val stats = ConversionStats()
        assertEquals(1, convert(text, stats).primitives.size)
        assertTrue(stats.counts.keys.none { it.startsWith("element.unsupported") })
    }

    @Test
    fun missingViewBoxUsesWidthAndHeight() {
        val stats = ConversionStats()
        val text = svg("""<line class="s" x1="0" y1="0" x2="96" y2="96"/>""", base, root = """width="96" height="96"""")
        val p = convert(text, stats).primitives.single()
        assertEquals(listOf(0f to 0f, 48f to 48f), p.points())
        assertEquals(0.5f, p.strokeWidth)
        assertEquals(1, stats.counts["svg.noViewBox"])
    }

    @Test
    fun missingViewBoxAndSizeDefaultsToCanvas() {
        val p = convert(svg("""<line class="s" x1="0" y1="0" x2="48" y2="48"/>""", base, root = "")).primitives.single()
        assertEquals(listOf(0f to 0f, 48f to 48f), p.points())
    }

    @Test
    fun invisibleAndUnpaintedShapesAreDropped() {
        val body = """
            <line class="s" style="display:none" x1="0" y1="0" x2="1" y2="1"/>
            <line class="s" style="opacity:0" x1="0" y1="0" x2="1" y2="1"/>
            <line fill="none" x1="0" y1="0" x2="1" y2="1"/>
            <circle style="fill:#fff;fill-opacity:0" cx="1" cy="1" r="1"/>
            <path class="s" d="M5 5"/>
        """
        assertEquals(0, convert(svg(body, base)).primitives.size)
    }

    @Test
    fun dashesAreBakedIntoSeparateSubpaths() {
        val css = ".d{fill:none;stroke:#fff;stroke-dasharray:1 2;stroke-linecap:round}"
        val p = convert(svg("""<line class="d" x1="0" y1="0" x2="7" y2="0"/>""", css)).primitives.single()
        // Dashes [0,1], [3,4], [6,7].
        assertEquals(listOf(0f to 0f, 1f to 0f, 3f to 0f, 4f to 0f, 6f to 0f, 7f to 0f), p.points())
        assertEquals(listOf(Verb.MOVE, Verb.LINE, Verb.MOVE, Verb.LINE, Verb.MOVE, Verb.LINE), p.verbs.toList())
    }

    @Test
    fun zeroLengthDashesBecomeDots() {
        val css = ".d{fill:none;stroke:#fff;stroke-dasharray:0 2;stroke-linecap:round}"
        val p = convert(svg("""<line class="d" x1="0" y1="0" x2="4" y2="0"/>""", css)).primitives.single()
        assertEquals(listOf(0f to 0f, 0f to 0f, 2f to 0f, 2f to 0f), p.points())
    }

    @Test
    fun brokenPathKeepsTheValidPrefix() {
        val stats = ConversionStats()
        val p = convert(svg("""<path class="s" d="M0 0L1 1L2 x"/>""", base), stats).primitives.single()
        assertEquals(listOf(0f to 0f, 1f to 1f), p.points())
        assertEquals(1, stats.counts["geometry.error.path"])
    }
}
