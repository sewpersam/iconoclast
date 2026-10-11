package dev.iconoclast.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dev.iconoclast.app.theme.ThemeArchive
import dev.iconoclast.app.theme.ThemeDocument
import dev.iconoclast.app.theme.ThemeFormatException
import dev.iconoclast.app.theme.ThemeZipExport
import dev.iconoclast.app.theme.ThemeZipImport
import dev.iconoclast.render.texture.TextureStore
import java.io.File
import java.io.IOException

/**
 * Theme export through the share sheet and import through the document picker,
 * spec 2.4. No storage permission: FileProvider out, ACTION_OPEN_DOCUMENT in. The
 * export is a `.zip` with the theme and its textures; import takes that or a bare JSON.
 */
object ThemeTransfer {
    /** Picker filter. Many file managers label .json and .zip as octet-stream or plain text. */
    val IMPORT_TYPES = arrayOf(ThemeZipExport.MIME, "application/x-zip-compressed", "application/json", "text/plain", "application/octet-stream")

    /** Every texture of [doc] must be in [textures]. */
    fun shareIntent(context: Context, doc: ThemeDocument, textures: TextureStore): Intent {
        val dir = File(context.cacheDir, "export").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, fileName(doc.name)).apply { writeBytes(ThemeZipExport.write(doc, textures)) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(ThemeZipExport.MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, doc.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Export ${doc.name}")
    }

    /**
     * The picked file, checked for import, spec 2.4. Streamed from the content URI under
     * [ThemeZipImport]'s per-entry and total bounds, never loaded whole. Throws
     * [ThemeFormatException] for the user when it cannot be read or is not a theme.
     */
    fun read(context: Context, uri: Uri, onPhone: (String) -> Boolean): ThemeArchive = try {
        ThemeZipImport.read({
            context.contentResolver.openInputStream(uri) ?: throw ThemeFormatException("The file could not be opened.")
        }, onPhone)
    } catch (e: IOException) {
        throw ThemeFormatException("The file could not be read.", e)
    } catch (e: SecurityException) {
        throw ThemeFormatException("The file could not be read.", e)
    }

    /** A safe file name from the theme name, for the receiving app to show. */
    fun fileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(' ', '-').ifEmpty { "theme" }.take(60) + ".zip"
}
