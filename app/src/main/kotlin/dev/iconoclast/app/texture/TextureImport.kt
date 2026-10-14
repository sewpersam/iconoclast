package dev.iconoclast.app.texture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import dev.iconoclast.render.texture.TextureStore
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer

/** The picked image cannot become a texture. [message] is shown to the user. */
class TextureImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Texture import, spec 2.4: an image from the photo picker, the document picker, a shared
 * image, or a web link,
 * decoded by ImageDecoder straight to its final size, longest side at most
 * [TextureStore.MAX_SIDE], upright per its EXIF orientation, in sRGB, in a software bitmap;
 * then encoded by the project's encoder and stored by SHA-256. No storage permission: the
 * pickers grant read access to the one file.
 */
object TextureImport {

    /** Imports the image at [uri] and returns its texture hash. Slow; call it off the main thread. */
    fun import(context: Context, uri: Uri, files: TextureFiles): String = try {
        decode(ImageDecoder.createSource(context.contentResolver, uri), files)
    } catch (e: SecurityException) {
        throw TextureImportException("The image could not be read.", e)
    }

    /**
     * Downloads the image at the web address [link] and imports it, returning its texture
     * hash. The only use of the network in the app. At most [MAX_LINK_BYTES], an image
     * content type, [LINK_TIMEOUT_MS] in all. Slow; call it off the main thread.
     */
    fun importLink(context: Context, link: String, files: TextureFiles): String {
        val tmp = File.createTempFile("link", ".img", context.cacheDir)
        try {
            download(parseLink(link), tmp)
            return decode(ImageDecoder.createSource(tmp), files)
        } finally {
            tmp.delete()
        }
    }

    /** [text] as an http or https URL, or a [TextureImportException] saying why not. */
    fun parseLink(text: String): URL {
        val t = text.trim()
        val url = try {
            URI(t).toURL()
        } catch (e: Exception) {
            throw TextureImportException("That is not a web address.", e)
        }
        if (url.protocol !in setOf("http", "https") || url.host.isNullOrEmpty()) throw TextureImportException("That is not a web address.")
        // Android lets apps fetch only https. Almost every site serves the same file over it.
        return if (url.protocol == "http") URI("https" + url.toString().removePrefix("http")).toURL() else url
    }

    private fun download(start: URL, to: File) {
        val deadline = System.currentTimeMillis() + LINK_TIMEOUT_MS
        var url = start
        var redirects = 0
        while (true) {
            val c = try {
                url.openConnection() as HttpURLConnection
            } catch (e: IOException) {
                throw TextureImportException("The link could not be opened.", e)
            }
            try {
                c.connectTimeout = CONNECT_TIMEOUT_MS
                c.readTimeout = READ_TIMEOUT_MS
                // Redirects are followed below, so one from http to https works too.
                c.instanceFollowRedirects = false
                c.setRequestProperty("Accept", "image/*")
                c.setRequestProperty("User-Agent", "Iconoclast")
                val code = try {
                    c.responseCode
                } catch (e: SocketTimeoutException) {
                    throw TextureImportException("The site took too long to answer.", e)
                } catch (e: IOException) {
                    throw TextureImportException("The image could not be downloaded. Check the connection and the link.", e)
                }
                if (code in 300..399) {
                    val next = c.getHeaderField("Location") ?: throw TextureImportException("The link leads nowhere (HTTP $code).")
                    if (++redirects > MAX_REDIRECTS) throw TextureImportException("The link redirects too many times.")
                    url = parseLink(URL(url, next).toString())
                    continue
                }
                if (code != HttpURLConnection.HTTP_OK) throw TextureImportException("The site answered HTTP $code instead of the image.")
                val type = c.contentType.orEmpty().lowercase()
                if (!type.startsWith("image/") && !type.startsWith("application/octet-stream")) {
                    throw TextureImportException("That link is a web page, not an image. Long-press the image and copy the image's own link.")
                }
                if (c.contentLengthLong > MAX_LINK_BYTES) throw TextureImportException("This image is over ${MAX_LINK_BYTES / 1_000_000} MB.")
                try {
                    c.inputStream.use { input ->
                        to.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var total = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                total += n
                                if (total > MAX_LINK_BYTES) throw TextureImportException("This image is over ${MAX_LINK_BYTES / 1_000_000} MB.")
                                if (System.currentTimeMillis() > deadline) throw TextureImportException("The download took too long.")
                                out.write(buf, 0, n)
                            }
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    throw TextureImportException("The download took too long.", e)
                } catch (e: IOException) {
                    throw TextureImportException("The download stopped. Check the connection and try again.", e)
                }
                return
            } finally {
                c.disconnect()
            }
        }
    }

    /** Decodes [source] to its final size and stores it. */
    private fun decode(source: ImageDecoder.Source, files: TextureFiles): String {
        try {
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

    const val MAX_LINK_BYTES = 20_000_000L
    const val LINK_TIMEOUT_MS = 30_000L
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val MAX_REDIRECTS = 5
}
