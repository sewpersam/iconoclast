package dev.iconoclast.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.iconoclast.render.RgbaImage
import java.nio.ByteBuffer

/** The renderer's image as a bitmap. ARGB_8888 stores premultiplied RGBA bytes, the renderer's layout. */
fun RgbaImage.toImageBitmap(): ImageBitmap =
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        .apply { copyPixelsFromBuffer(ByteBuffer.wrap(pixels)) }
        .asImageBitmap()
