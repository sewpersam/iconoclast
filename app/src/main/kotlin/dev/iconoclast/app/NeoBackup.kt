package dev.iconoclast.app

import android.database.sqlite.SQLiteDatabase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Neo Launcher 0.9.3 backups (`.zbk`, a zip). Neo checks an app's custom icon, set with
 * long-press, Edit, before the icon pack, and 0.9.3 has no way to remove one. A custom
 * icon from a pack that is no longer installed shows the app's own icon for good. Neo's
 * home screen backup holds those custom icons in `NeoLauncher.db`, table `IconOverride`,
 * and Neo's restore writes each file back by name, so a copy of the backup without the
 * rows removes them.
 */
object NeoBackup {
    /** One custom icon: [target] is `package/class#user`, drawn from [drawable] in [pack]. */
    data class Override(val target: String, val pack: String, val drawable: String)

    const val DB = "NeoLauncher.db"
    const val WAL = "$DB-wal"
    const val SHM = "$DB-shm"

    /** The entries of [zip], by name, in order. */
    fun entries(zip: ByteArray): List<Pair<String, ByteArray>> {
        val out = ArrayList<Pair<String, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (!e.isDirectory) out += e.name to z.readBytes()
            }
        }
        return out
    }

    /**
     * [zip] with each entry named in [replace] swapped for its bytes, every other entry
     * kept as it was and in its place. Entries are stored, so nothing depends on a deflater.
     */
    fun rewrite(zip: ByteArray, replace: Map<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { z ->
            for ((name, data) in entries(zip)) {
                val body = replace[name] ?: data
                val e = ZipEntry(name).apply {
                    method = ZipEntry.STORED
                    size = body.size.toLong()
                    compressedSize = body.size.toLong()
                    crc = java.util.zip.CRC32().also { it.update(body) }.value
                }
                z.putNextEntry(e)
                z.write(body)
                z.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    /** Lines for the report: how many custom icons, then one per icon with whether its pack is installed. */
    fun format(overrides: List<Override>, installed: (String) -> Boolean): List<String> =
        listOf("neo backup: ${overrides.size} custom ${if (overrides.size == 1) "icon" else "icons"}") +
            overrides.sortedBy { it.target }.map { o ->
                "custom ${o.target} -> ${o.pack.ifEmpty { "system" }} ${o.drawable} " +
                    if (o.pack.isEmpty() || installed(o.pack)) "installed" else "PACK GONE"
            }

    /** The custom icons in the backup [zip], or null when it holds no Neo home screen database. */
    fun overrides(zip: ByteArray, work: File): List<Override>? = withDb(zip, work) { db ->
        db.rawQuery("select target, packPackageName, drawableName from IconOverride", null).use { c ->
            buildList { while (c.moveToNext()) add(Override(c.getString(0), c.getString(1), c.getString(2))) }
        }
    }

    /**
     * A copy of the backup [zip] without custom icons: `NeoLauncher.db` with every
     * `IconOverride` row deleted and its journal folded in, and the WAL and shared memory
     * entries empty, so a restore leaves no stale journal beside the new file. Everything
     * else is byte for byte the same. Null when the backup has no Neo home screen database.
     */
    fun withoutOverrides(zip: ByteArray, work: File): ByteArray? {
        withDb(zip, work) { db ->
            db.execSQL("delete from IconOverride")
            db.rawQuery("pragma wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            db.rawQuery("pragma journal_mode=DELETE", null).use { it.moveToFirst() }
        } ?: return null
        val cleaned = File(work, DB).readBytes()
        work.deleteRecursively()
        val names = entries(zip).mapTo(HashSet()) { it.first }
        val replace = HashMap<String, ByteArray>()
        replace[DB] = cleaned
        if (WAL in names) replace[WAL] = ByteArray(0)
        if (SHM in names) replace[SHM] = ByteArray(0)
        return rewrite(zip, replace)
    }

    /** Extracts the database and its WAL into [work], opens it, and gives [body] the database. */
    private fun <T> withDb(zip: ByteArray, work: File, body: (SQLiteDatabase) -> T): T? {
        val files = entries(zip).toMap()
        val db = files[DB] ?: return null
        work.deleteRecursively()
        check(work.mkdirs()) { "could not create $work" }
        File(work, DB).writeBytes(db)
        // The WAL holds the latest writes; SQLite folds it in on open. A stale shm is rebuilt.
        files[WAL]?.let { File(work, WAL).writeBytes(it) }
        val opened = SQLiteDatabase.openDatabase(File(work, DB).path, null, SQLiteDatabase.OPEN_READWRITE)
        return try {
            opened.rawQuery("select name from sqlite_master where type='table' and name='IconOverride'", null).use {
                if (!it.moveToFirst()) return null
            }
            body(opened)
        } finally {
            opened.close()
        }
    }
}
