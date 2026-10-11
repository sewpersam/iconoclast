package dev.iconoclast.app.texture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dev.iconoclast.render.texture.TextureStore
import java.io.IOException
import java.nio.ByteBuffer

/** The picked image cannot become a texture. [message] is shown to the user. */
class TextureImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Texture import, spec 2.4: an image from the photo picker or the document picker,
 * decoded with a power-of-two sample size, then scaled exactly so its longest side is at
 * most [TextureStore.MAX_SIDE], encoded by the project's encoder and stored by SHA-256.
 * No storage permission: the pickers grant read access to the one file.
 */
object TextureImport {

    /** Imports the image at [uri] and returns its texture hash. Slow; call it off the main thread. */
    fun import(context: Context, uri: Uri, files: TextureFiles): String {
        val resolver = context.contentResolver
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // With inJustDecodeBounds the decode returns null by design; the bounds are the result.
            val stream = resolver.openInputStream(uri) ?: throw TextureImportException("The image could not be opened.")
            stream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw TextureImportException("This file is not an image the phone can read.")
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, TextureStore.MAX_SIDE)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw TextureImportException("This file is not an image the phone can read.")
            val (w, h) = fitted(decoded.width, decoded.height, TextureStore.MAX_SIDE)
            val argb = if (decoded.config == Bitmap.Config.ARGB_8888) decoded else decoded.copy(Bitmap.Config.ARGB_8888, false)
            val scaled = if (argb.width == w && argb.height == h) argb else Bitmap.createScaledBitmap(argb, w, h, true)
            // ARGB_8888 stores premultiplied RGBA bytes, the layout the encoder takes.
            val pixels = ByteArray(w * h * 4)
            scaled.copyPixelsToBuffer(ByteBuffer.wrap(pixels))
            return files.add(TextureStore.encode(w, h, pixels))
        } catch (e: IOException) {
            throw TextureImportException("The image could not be read.", e)
        } catch (e: SecurityException) {
            throw TextureImportException("The image could not be read.", e)
        } catch (e: OutOfMemoryError) {
            throw TextureImportException("This image is too large to import.", e)
        }
    }

    /** The largest power of two that keeps the longest side at or above [max], so the exact downscale has data to work with. */
    fun sampleSize(width: Int, height: Int, max: Int): Int {
        val longest = maxOf(width, height)
        var n = 1
        while (longest / (n * 2) >= max) n *= 2
        return n
    }

    /** [width] x [height] scaled so the longest side is at most [max], aspect kept, each side at least 1. */
    fun fitted(width: Int, height: Int, max: Int): Pair<Int, Int> {
        val longest = maxOf(width, height)
        if (longest <= max) return width to height
        val s = max.toDouble() / longest
        return maxOf(1, Math.round(width * s).toInt()).coerceAtMost(max) to maxOf(1, Math.round(height * s).toInt()).coerceAtMost(max)
    }
}
