package dev.icondesigner.render.verify

import dev.icondesigner.render.format.IconBundle
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import kotlin.system.exitProcess

/** Gate from spec 3.2: IoU at least this for... */
private const val MIN_IOU = 0.98
/** ...at least this share of icons. */
private const val MIN_PASS_RATE = 0.999

/** Coverage at or above this alpha counts as inside the shape. */
private const val ALPHA_THRESHOLD = 128

/**
 * Rasterizes every bundled icon and compares it to an inkscape render of its SVG.
 * Args: bundle, names, svgDir, refDir, diffDir, report, size, workers.
 * Exits non-zero when the gate fails.
 */
fun main(args: Array<String>) {
    val (bundleFile, namesFile, svgDir, refDir, diffDir, reportFile) = args.take(6).map(::File)
    val size = args[6].toInt()
    val workers = args[7].toInt()

    val names = namesFile.readLines().filter { it.isNotEmpty() }
    val bundle = IconBundle(ByteBuffer.wrap(bundleFile.readBytes()))
    check(bundle.size == names.size) { "bundle has ${bundle.size} icons, names has ${names.size}" }

    val refs = InkscapeReferences(svgDir, refDir, size * SUPERSAMPLE)
    val t0 = System.nanoTime()
    val rendered = refs.ensure(names, workers)
    val refSeconds = (System.nanoTime() - t0) / 1e9
    println("inkscape references: rendered $rendered, reused ${names.size - rendered}, %.1f s".format(refSeconds))

    diffDir.deleteRecursively()
    diffDir.mkdirs()
    val rasterizer = AwtRasterizer()
    val t1 = System.nanoTime()
    val pool = Executors.newFixedThreadPool(workers)
    val results = try {
        pool.invokeAll(
            names.indices.map { i ->
                Callable {
                    val ours = rasterizer.rasterize(bundle.icon(i), size)
                    val big = size * SUPERSAMPLE
                    val ref = downsample(alpha(ImageIO.read(refs.file(names[i])), big), big, SUPERSAMPLE)
                    val soft = softIou(ours, ref)
                    if (soft < MIN_IOU) {
                        writeDiff(ours, ref, size, File(diffDir, "${names[i]}.png"))
                        writeAlpha(ours, size, File(diffDir, "${names[i]}.ours.png"))
                    }
                    soft to binaryIou(ours, ref)
                }
            },
        ).map { it.get() }
    } finally {
        pool.shutdown()
    }
    val compareSeconds = (System.nanoTime() - t1) / 1e9
    val ious = results.map { it.first }
    val binary = results.map { it.second }

    val failures = names.indices.filter { ious[it] < MIN_IOU }.sortedBy { ious[it] }
    val passRate = 1.0 - failures.size.toDouble() / names.size
    val pass = passRate >= MIN_PASS_RATE
    val sorted = ious.sorted()
    val report = buildString {
        appendLine("icons ${names.size}")
        appendLine("size $size")
        appendLine("metric softIou = sum(min(a,b)) / sum(max(a,b)) over 8-bit alpha")
        appendLine("inkscapeRendered $rendered")
        appendLine("inkscapeSeconds %.1f".format(refSeconds))
        appendLine("compareSeconds %.1f".format(compareSeconds))
        appendLine("iouMin %.4f".format(sorted.first()))
        appendLine("iouP001 %.4f".format(sorted[(sorted.size * 0.001).toInt()]))
        appendLine("iouP01 %.4f".format(sorted[(sorted.size * 0.01).toInt()]))
        appendLine("iouMedian %.4f".format(sorted[sorted.size / 2]))
        appendLine("iouMean %.4f".format(ious.average()))
        appendLine("below098 ${failures.size}")
        appendLine("binaryIouBelow098 ${binary.count { it < MIN_IOU }} (threshold $ALPHA_THRESHOLD, not gated)")
        appendLine("binaryIouMedian %.4f".format(binary.sorted()[binary.size / 2]))
        appendLine("passRate %.5f".format(passRate))
        appendLine("gate ${if (pass) "PASS" else "FAIL"} (need IoU >= $MIN_IOU for >= ${MIN_PASS_RATE * 100}% of icons)")
        failures.forEach { appendLine("fail ${names[it]} %.4f".format(ious[it])) }
    }
    reportFile.parentFile.mkdirs()
    reportFile.writeText(report)
    print(report.lineSequence().take(16 + minOf(failures.size, 40)).joinToString("\n", postfix = "\n"))
    println("report: $reportFile")
    if (!pass) exitProcess(1)
}

private operator fun <T> List<T>.component6(): T = this[5]

private fun alpha(image: BufferedImage, size: Int): ByteArray {
    check(image.width == size && image.height == size) { "reference is ${image.width}x${image.height}" }
    val argb = image.getRGB(0, 0, size, size, null, 0, size)
    return ByteArray(argb.size) { (argb[it] ushr 24).toByte() }
}

/**
 * Soft intersection over union of two coverage masks: sum of per-pixel minimum over sum of
 * per-pixel maximum. This is the gated metric. Two empty masks score 1.
 */
fun softIou(a: ByteArray, b: ByteArray): Double {
    var inter = 0L
    var union = 0L
    for (i in a.indices) {
        val x = a[i].toInt() and 0xFF
        val y = b[i].toInt() and 0xFF
        inter += minOf(x, y)
        union += maxOf(x, y)
    }
    return if (union == 0L) 1.0 else inter.toDouble() / union
}

/** IoU of the two masks thresholded at [ALPHA_THRESHOLD]. Reported, not gated. Two empty masks score 1. */
fun binaryIou(a: ByteArray, b: ByteArray): Double {
    var inter = 0
    var union = 0
    for (i in a.indices) {
        val x = (a[i].toInt() and 0xFF) >= ALPHA_THRESHOLD
        val y = (b[i].toInt() and 0xFF) >= ALPHA_THRESHOLD
        if (x && y) inter++
        if (x || y) union++
    }
    return if (union == 0) 1.0 else inter.toDouble() / union
}

/** Red: reference only. Green: converted only. White: both. */
private fun writeDiff(ours: ByteArray, ref: ByteArray, size: Int, file: File) {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
    for (i in ours.indices) {
        val o = (ours[i].toInt() and 0xFF) >= ALPHA_THRESHOLD
        val r = (ref[i].toInt() and 0xFF) >= ALPHA_THRESHOLD
        val rgb = when {
            o && r -> 0xFFFFFF
            r -> 0xFF0000
            o -> 0x00FF00
            else -> 0
        }
        image.setRGB(i % size, i / size, rgb)
    }
    ImageIO.write(image, "png", file)
}

/** Our coverage as a grey image, for side-by-side inspection with the reference. */
private fun writeAlpha(alpha: ByteArray, size: Int, file: File) {
    val image = BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY)
    image.raster.setDataElements(0, 0, size, size, alpha)
    ImageIO.write(image, "png", file)
}
