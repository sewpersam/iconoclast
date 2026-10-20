package dev.iconoclast.app

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager

/**
 * Launchable activities as `package/class`, spec 2.5. The union of
 * `queryIntentActivities(MAIN, LAUNCHER)` for this user and `LauncherApps` for every
 * profile it exposes, which adds work profile apps when there is one. Activity aliases
 * keep their own class name, which is what launchers look up, spec 6.2. Each package's
 * launch intent component is added as well, the one Neo Launcher looks up. Iconoclast itself
 * is included: the bundled `iconoclast` glyph themes it like any other app.
 */
object InstalledApps {
    fun launchableComponents(context: Context): Set<String> {
        val out = HashSet<String>()
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val packages = HashSet<String>()
        for (r in pm.queryIntentActivities(main, PackageManager.ResolveInfoFlags.of(0))) {
            out += "${r.activityInfo.packageName}/${r.activityInfo.name}"
            packages += r.activityInfo.packageName
        }
        // Neo Launcher looks an app up by its launch intent, which prefers an INFO activity
        // over the LAUNCHER one. Covering that component too makes the pack match it.
        for (p in packages) pm.getLaunchIntentForPackage(p)?.component?.let { out += "${it.packageName}/${it.className}" }
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        for (user in launcherApps.profiles) {
            val activities = try {
                launcherApps.getActivityList(null, user)
            } catch (e: SecurityException) {
                emptyList()
            }
            for (a in activities) out += "${a.componentName.packageName}/${a.componentName.className}"
        }
        return out
    }
}
