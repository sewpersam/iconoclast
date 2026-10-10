package dev.iconoclast.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dev.iconoclast.app.theme.ThemeDocument
import dev.iconoclast.app.theme.ThemeFormatException
import java.io.File
import java.io.IOException

/**
 * Theme export through the share sheet and import through the document picker,
 * spec 2.4. No storage permission: FileProvider out, ACTION_OPEN_DOCUMENT in.
 * Textures and the .zip arrive in M5; for now the file is the theme JSON.
 */
object ThemeTransfer {
    /** Picker filter. Many file managers label .json as octet-stream or plain text. */
    val IMPORT_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

    /** A theme file is a few kilobytes. Anything this large is not one. */
    private const val MAX_BYTES = 256 * 1024

    fun shareIntent(context: Context, doc: ThemeDocument): Intent {
        val dir = File(context.cacheDir, "export").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, fileName(doc.name)).apply { writeText(doc.toJson()) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, doc.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Export ${doc.name}")
    }

    /** The picked file's text. Throws [ThemeFormatException] for the user when it cannot be read. */
    fun read(context: Context, uri: Uri): String = try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readNBytes(MAX_BYTES + 1) }
            ?: throw ThemeFormatException("The file could not be opened.")
        if (bytes.size > MAX_BYTES) throw ThemeFormatException("This file is too large to be a theme.")
        bytes.decodeToString()
    } catch (e: IOException) {
        throw ThemeFormatException("The file could not be read.", e)
    } catch (e: SecurityException) {
        throw ThemeFormatException("The file could not be read.", e)
    }

    /** A safe file name from the theme name, for the receiving app to show. */
    fun fileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(' ', '-').ifEmpty { "theme" }.take(60) + ".json"
}
