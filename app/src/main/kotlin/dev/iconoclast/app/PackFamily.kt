package dev.iconoclast.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dev.iconoclast.pack.PackName

/**
 * The packs Apply has installed, one package name per Apply, see [PackName]. The last
 * installed generation is kept in preferences, so the counter survives restarts; the
 * packs still on the phone are found by the icon pack intent every pack answers.
 */
class PackFamily(private val context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val base = PackBuilder.PACK_BASE

    /** The generation of the last successful Apply, 0 before the first one. */
    val lastGeneration: Int get() = prefs.getInt(GENERATION, 0)

    /** The pack the last successful Apply installed, or null before the first one. */
    val current: String? get() = lastGeneration.takeIf { it > 0 }?.let { PackName.of(base, it) }

    /** Records [name] as the pack in use. Written before returning: a crash right after must not reuse it. */
    fun installed(name: String) {
        val generation = requireNotNull(PackName.generationOf(base, name)) { "$name is not a pack" }
        prefs.edit().putInt(GENERATION, maxOf(generation, lastGeneration)).commit()
    }

    /** Every pack of ours on the phone, the legacy single-name pack included. */
    fun onPhone(): Set<String> {
        val pm = context.packageManager
        return pm.queryIntentActivities(Intent(THEMES_ACTION), PackageManager.ResolveInfoFlags.of(0))
            .mapNotNullTo(sortedSetOf()) { it.activityInfo?.packageName?.takeIf { p -> PackName.isPack(base, p) } }
    }

    /** The name the next Apply installs. */
    fun nextName(): String =
        PackName.of(base, PackName.next(lastGeneration, onPhone().mapNotNull { PackName.generationOf(base, it) }))

    fun isOnPhone(name: String): Boolean = try {
        context.packageManager.getPackageInfo(name, PackageManager.PackageInfoFlags.of(0))
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private companion object {
        const val FILE = "pack"
        const val GENERATION = "generation"

        /** The icon pack action every pack declares; the manifest queries it. */
        const val THEMES_ACTION = "org.adw.launcher.THEMES"
    }
}
