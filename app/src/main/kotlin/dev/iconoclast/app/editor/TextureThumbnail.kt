package dev.iconoclast.app.editor

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.texture.BuiltInBrushes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A small preview of texture [hash], decoded off the main thread at a reduced size. A
 * built-in brush has no file: its canonical PNG is generated. Any other texture is read
 * from [file], the imported texture's path. A null [description] leaves naming to the parent.
 */
@Composable
fun TextureThumbnail(hash: String, file: (String) -> File, description: String?, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(null, hash) {
        value = withContext(Dispatchers.IO) { decodeThumbnail(hash, file) }
    }
    val shape = RoundedCornerShape(6.dp)
    Box(modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)
        .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)) {
        image?.let { Image(it, null, Modifier.size(size), contentScale = ContentScale.Crop) }
    }
}

/** The brush's name for a built-in [hash], else "Imported". */
fun textureName(hash: String): String = BuiltInBrushes.ALL.firstOrNull { it.sha256 == hash }?.name ?: "Imported"

private fun decodeThumbnail(hash: String, file: (String) -> File): ImageBitmap? {
    val png = BuiltInBrushes.png(hash)
    val path = file(hash).path
    fun decode(o: BitmapFactory.Options) =
        if (png != null) BitmapFactory.decodeByteArray(png, 0, png.size, o) else BitmapFactory.decodeFile(path, o)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    decode(bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMB_PX) sample *= 2
    return decode(BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

private const val THUMB_PX = 96
