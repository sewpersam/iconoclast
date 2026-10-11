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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A small preview of a texture file, decoded off the main thread at a reduced size. */
@Composable
fun TextureThumbnail(file: File, description: String, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMB_PX) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }
    }
    val shape = RoundedCornerShape(6.dp)
    Box(modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant).semantics { contentDescription = description }) {
        image?.let { Image(it, null, Modifier.size(size), contentScale = ContentScale.Crop) }
    }
}

private const val THUMB_PX = 96
