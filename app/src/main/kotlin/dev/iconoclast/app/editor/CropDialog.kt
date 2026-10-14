package dev.iconoclast.app.editor

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.iconoclast.render.Crop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * The crop step: the image with a frame over it. Drag inside the frame to move it, drag a
 * corner to resize it, pinch to resize it about its middle. The frame is stored in the
 * theme as fractions of the image, never as a new image, so it can be changed later from
 * the layer's page. [png] loads the canonical texture to show.
 */
@Composable
fun CropDialog(request: CropRequest, png: (String) -> ByteArray?, onDismiss: () -> Unit, onDone: (Crop?) -> Unit) {
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(request.sha256) {
        image = withContext(Dispatchers.IO) { png(request.sha256)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    }
    var frame by remember { mutableStateOf(request.crop?.let { Rect(it.left, it.top, it.right, it.bottom) } ?: WHOLE) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            Text(Tips.CROP.label + " image", style = MaterialTheme.typography.titleMedium)
            Text("Drag the frame to move it. Drag a corner or pinch to resize.", Modifier.padding(top = 4.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val img = image
                if (img == null) CircularProgressIndicator()
                else CropArea(img, frame) { frame = it }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { frame = WHOLE }) { Text("Whole image") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onDone(toCrop(frame)) }) { Text("Done") }
            }
        }
    }
}

private val WHOLE = Rect(0f, 0f, 1f, 1f)

/** The frame as a crop, or null when it is the whole image. */
private fun toCrop(r: Rect): Crop? {
    val c = Crop(r.left.coerceIn(0f, 1f), r.top.coerceIn(0f, 1f), r.right.coerceIn(0f, 1f), r.bottom.coerceIn(0f, 1f))
    return if (c.left <= 0.001f && c.top <= 0.001f && c.right >= 0.999f && c.bottom >= 0.999f) null else c
}

@Composable
private fun CropArea(img: ImageBitmap, frame: Rect, onChange: (Rect) -> Unit) {
    val current by rememberUpdatedState(frame)
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth().aspectRatio(img.width.toFloat() / img.height, matchHeightConstraintsFirst = img.height > img.width)) {
        val corner = with(density) { 32.dp.toPx() }
        Image(img, contentDescription = "The image to crop", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Box(
            Modifier.fillMaxSize()
                .semantics { contentDescription = "Crop frame, ${percent(frame.width)} wide and ${percent(frame.height)} tall" }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val start = current
                        val px = Rect(start.left * w, start.top * h, start.right * w, start.bottom * h)
                        val corners = listOf(px.topLeft, px.topRight, px.bottomLeft, px.bottomRight)
                        val hitCorner = corners.indices.minByOrNull { (corners[it] - down.position).getDistance() }
                            ?.takeIf { (corners[it] - down.position).getDistance() <= corner }
                        val inside = px.contains(down.position)
                        var rect = start
                        while (true) {
                            val e = awaitPointerEvent()
                            val pressed = e.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                val z = e.calculateZoom()
                                if (z != 1f) rect = scaled(rect, 1f / z)
                            } else {
                                val c = e.changes.firstOrNull { it.id == down.id } ?: pressed.first()
                                val d = c.position - c.previousPosition
                                val dx = d.x / w
                                val dy = d.y / h
                                rect = when {
                                    hitCorner != null -> dragCorner(rect, hitCorner, dx, dy)
                                    inside -> moved(rect, dx, dy)
                                    else -> rect
                                }
                            }
                            e.changes.forEach { it.consume() }
                            onChange(rect)
                        }
                    }
                }
                .drawWithContent {
                    val r = Rect(frame.left * size.width, frame.top * size.height, frame.right * size.width, frame.bottom * size.height)
                    val shade = Color.Black.copy(alpha = 0.55f)
                    drawRect(shade, Offset.Zero, Size(size.width, r.top))
                    drawRect(shade, Offset(0f, r.bottom), Size(size.width, size.height - r.bottom))
                    drawRect(shade, Offset(0f, r.top), Size(r.left, r.height))
                    drawRect(shade, Offset(r.right, r.top), Size(size.width - r.right, r.height))
                    drawRect(Color.White, r.topLeft, r.size, style = Stroke(2.dp.toPx()))
                    val k = 14.dp.toPx()
                    for (p in listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight)) {
                        drawRect(Color.White, Offset(p.x - k / 2, p.y - k / 2), Size(k, k))
                    }
                },
        )
    }
}

private const val MIN = Crop.MIN * 2

private fun moved(r: Rect, dx: Float, dy: Float): Rect {
    val x = dx.coerceIn(-r.left, 1f - r.right)
    val y = dy.coerceIn(-r.top, 1f - r.bottom)
    return r.translate(x, y)
}

/** Corner [i], 0 top left, 1 top right, 2 bottom left, 3 bottom right, moved by (dx, dy). */
private fun dragCorner(r: Rect, i: Int, dx: Float, dy: Float): Rect {
    var (l, t, rr, b) = listOf(r.left, r.top, r.right, r.bottom)
    if (i == 0 || i == 2) l = (l + dx).coerceIn(0f, rr - MIN) else rr = (rr + dx).coerceIn(l + MIN, 1f)
    if (i == 0 || i == 1) t = (t + dy).coerceIn(0f, b - MIN) else b = (b + dy).coerceIn(t + MIN, 1f)
    return Rect(l, t, rr, b)
}

/** [r] scaled by [k] about its middle, kept inside the image. */
private fun scaled(r: Rect, k: Float): Rect {
    val w = (r.width * k).coerceIn(MIN, 1f)
    val h = (r.height * k).coerceIn(MIN, 1f)
    val cx = r.center.x.coerceIn(w / 2, 1f - w / 2)
    val cy = r.center.y.coerceIn(h / 2, 1f - h / 2)
    if (abs(w - r.width) < 1e-6f && abs(h - r.height) < 1e-6f) return r
    return Rect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
}
