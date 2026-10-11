package dev.iconoclast.app.texture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import dev.iconoclast.render.texture.TextureStore
import java.io.IOException
import java.nio.ByteBuffer

/** The picked image cannot become a texture. [message] is shown to the user. */
class TextureImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Texture import, spec 2.4: an image from the photo picker or the document picker,
 * decoded by ImageDecoder straight to its final size, longest side at most
 * [TextureStore.MAX_SIDE], upright per its EXIF orientation, in sRGB, in a software bitmap;
 * then encoded by the project's encoder and stored by SHA-256. No storage permission: the
 * pickers grant read access to the one file.
 */
object TextureImport {

    /** Imports the image at [uri] and returns its texture hash. Slow; call it off the main thread. */
    fun import(context: Context, uri: Uri, files: TextureFiles): String {
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val (w, h) = fitted(info.size.width, info.size.height, TextureStore.MAX_SIDE)
                decoder.setTargetSize(w, h)
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            // A wide-gamut or HDR source can still decode to another config; the encoder takes 8-bit RGBA.
            val bitmap = if (decoded.config == Bitmap.Config.ARGB_8888) decoded else decoded.copy(Bitmap.Config.ARGB_8888, false)
            val w = bitmap.width
            val h = bitmap.height
            // ARGB_8888 stores premultiplied RGBA bytes, the layout the encoder takes.
            val pixels = ByteArray(w * h * 4)
            bitmap.copyPixelsToBuffer(ByteBuffer.wrap(pixels))
            return files.add(TextureStore.encode(w, h, pixels))
        } catch (e: ImageDecoder.DecodeException) {
            throw TextureImportException("This file is not an image the phone can read.", e)
        } catch (e: IOException) {
            throw TextureImportException("The image could not be read.", e)
        } catch (e: SecurityException) {
            throw TextureImportException("The image could not be read.", e)
        } catch (e: OutOfMemoryError) {
            throw TextureImportException("This image is too large to import.", e)
        }
    }

    /** [width] x [height] scaled so the longest side is at most [max], aspect kept, each side at least 1. */
    fun fitted(width: Int, height: Int, max: Int): Pair<Int, Int> {
        val longest = maxOf(width, height)
        if (longest <= max) return width to height
        val s = max.toDouble() / longest
        return maxOf(1, Math.round(width * s).toInt()).coerceAtMost(max) to maxOf(1, Math.round(height * s).toInt()).coerceAtMost(max)
    }
}
