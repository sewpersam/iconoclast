package dev.icondesigner.app

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager

/**
 * Launchable activities as `package/class`, spec 2.5. The union of
 * `queryIntentActivities(MAIN, LAUNCHER)` for this user and `LauncherApps` for every
 * profile it exposes, which adds work profile apps when there is one. Activity aliases
 * keep their own class name, which is what launchers look up, spec 6.2.
 */
object InstalledApps {
    fun launchableComponents(context: Context): Set<String> {
        val out = HashSet<String>()
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        for (r in pm.queryIntentActivities(main, PackageManager.ResolveInfoFlags.of(0))) {
            out += "${r.activityInfo.packageName}/${r.activityInfo.name}"
        }
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        for (user in launcherApps.profiles) {
            val activities = try {
                launcherApps.getActivityList(null, user)
            } catch (e: SecurityException) {
                emptyList()
            }
            for (a in activities) out += "${a.componentName.packageName}/${a.componentName.className}"
        }
        out.removeAll { it.startsWith(context.packageName + "/") }
        return out
    }
}
