package dev.iconoclast.app

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserManager
import dev.iconoclast.iconsource.IconSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A plain-text report to paste into a chat when icons do not change: this build, the packs
 * on the phone, the home app, and one line per launchable app with its installer, profile,
 * each launcher component, whether the installed pack's appfilter names that exact
 * component, and the Arcticons drawable it resolves to now.
 */
object Diagnostics {
    /**
     * One launcher activity: [cls] as the system reports it, [inPack] the pack's drawable for
     * it, [arcticons] the drawable Iconoclast resolves now. [packLoad] and [arcLoad] are those
     * drawables loaded the way Neo loads them, from the installed pack and from Arcticons.
     */
    data class Component(
        val cls: String, val inPack: String?, val arcticons: String?,
        val packLoad: String? = null, val arcLoad: String? = null,
    )

    /**
     * One app in one profile. [installer] is null when no store installed it, such as a
     * system app or adb. [updated] is its last install or update, as `MM-dd HH:mm`: a
     * launcher redraws an app's icon when the app updates.
     */
    data class App(val pkg: String, val user: String, val installer: String?, val updated: String?, val components: List<Component>)

    /** The report: [header] lines, then one line per app, sorted by package then profile. */
    fun format(header: List<String>, apps: List<App>): String = buildString {
        for (h in header) append(h).append('\n')
        val missing = apps.sumOf { a -> a.components.count { it.inPack == null && it.arcticons != null } }
        append("apps ").append(apps.size).append(", components missing from pack but in Arcticons: ").append(missing).append('\n')
        for (a in apps.sortedWith(compareBy({ it.pkg }, { it.user }))) {
            append(a.pkg).append(' ').append(a.user).append(" via=").append(a.installer ?: "-")
            append(" upd=").append(a.updated ?: "-").append(" |")
            for ((i, c) in a.components.sortedBy { it.cls }.withIndex()) {
                if (i > 0) append(" ;")
                append(' ').append(short(a.pkg, c.cls)).append(" pack=").append(c.inPack ?: "MISSING")
                c.packLoad?.let { append(' ').append(it) }
                append(" arc=").append(c.arcticons ?: "none")
                c.arcLoad?.let { append(' ').append(it) }
            }
            append('\n')
        }
    }

    /** [cls] with [pkg] cut to a leading dot, the way manifests write it. */
    fun short(pkg: String, cls: String): String = if (cls.startsWith("$pkg.")) cls.substring(pkg.length) else cls

    private val ITEM = Regex("""<item\s+component="ComponentInfo\{([^}]*)\}"\s+drawable="([^"]*)"""")

    /** The `package/class` to drawable entries of an appfilter as [dev.iconoclast.pack.AppfilterWriter] writes it. */
    fun parseAppfilter(xml: String): Map<String, String> =
        ITEM.findAll(xml).associate { unescape(it.groupValues[1]) to unescape(it.groupValues[2]) }

    private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&")

    /**
     * Loads [name] from [pkg] the way Neo 0.9.3 does: `getIdentifier(name, "drawable", pkg)`
     * on the package's own resources, then `getDrawableForDensity` at the screen density.
     * Gives `id=7f.. WxH px=N h=hash`, N the pixels with any alpha, h a hash of the pixels, or why it failed.
     */
    fun load(res: android.content.res.Resources?, pkg: String, name: String, dpi: Int): String {
        if (res == null) return "noapp"
        @Suppress("DiscouragedApi")
        val id = res.getIdentifier(name, "drawable", pkg)
        if (id == 0) return "id=0"
        val d = try {
            res.getDrawableForDensity(id, dpi, null)
        } catch (e: Exception) {
            return "id=%x fail=%s".format(Locale.ROOT, id, e.javaClass.simpleName)
        } ?: return "id=%x null".format(Locale.ROOT, id)
        val w = d.intrinsicWidth.takeIf { it > 0 } ?: 192
        val h = d.intrinsicHeight.takeIf { it > 0 } ?: 192
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, w, h)
        d.draw(android.graphics.Canvas(bmp))
        val all = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
        bmp.recycle()
        return "id=%x %dx%d px=%d h=%08x".format(Locale.ROOT, id, w, h, all.count { it ushr 24 != 0 }, all.contentHashCode())
    }

    private fun resourcesOf(pm: PackageManager, pkg: String?) = pkg?.let {
        try { pm.getResourcesForApplication(it) } catch (e: PackageManager.NameNotFoundException) { null }
    }

    /** Collects the report on this phone. Call off the main thread: it reads the pack and every app's install source. */
    fun collect(context: Context, source: IconSource, packs: PackFamily, lastApply: Long?): String {
        val pm = context.packageManager
        val me = pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
        val day = SimpleDateFormat("MM-dd HH:mm", Locale.ROOT)
        val onPhone = packs.onPhone()
        val pack = packs.current?.takeIf { it in onPhone } ?: onPhone.lastOrNull()
        val filter = pack?.let { p ->
            try {
                pm.getResourcesForApplication(p).assets.open("appfilter.xml").use { parseAppfilter(it.readBytes().decodeToString()) }
            } catch (e: Exception) {
                null
            }
        }
        val home = pm.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
        )?.activityInfo?.packageName
        val homeVersion = home?.let { versionOf(pm, it) }
        val arcVersion = versionOf(pm, ARCTICONS)
        val packRes = resourcesOf(pm, pack)
        val arcRes = resourcesOf(pm, ARCTICONS)
        val dpi = context.resources.displayMetrics.densityDpi
        val loads = HashMap<String, String>()
        fun packLoad(d: String) = loads.getOrPut("p:$d") { load(packRes, pack!!, d, dpi) }
        fun arcLoad(d: String) = loads.getOrPut("a:$d") { load(arcRes, ARCTICONS, d, dpi) }
        val header = listOf(
            "Iconoclast ${me.versionName} (${me.longVersionCode}) installed ${stamp.format(Date(me.lastUpdateTime))}",
            "Android ${Build.VERSION.RELEASE} sdk ${Build.VERSION.SDK_INT} ${Build.DISPLAY}",
            "home ${home ?: "none"} ${homeVersion ?: ""}".trimEnd(),
            "packs ${onPhone.joinToString(" ").ifEmpty { "none" }}, current ${packs.current ?: "none"}, " +
                "read ${pack ?: "none"}${pack?.let { installedAt(pm, it) }?.let { " installed " + stamp.format(Date(it)) } ?: ""}: " +
                "${filter?.size?.let { "$it entries" } ?: "unreadable"}",
            "last apply ${lastApply?.let { stamp.format(Date(it)) } ?: "never"}",
            "arcticons ${arcVersion ?: "not installed"}, density $dpi, loads as Neo: getIdentifier(drawable) then getDrawableForDensity",
        )

        // Every launcher activity per profile, as launchers see them, plus this user's
        // MAIN/LAUNCHER query in case LauncherApps hides one.
        val seen = LinkedHashMap<Pair<String, String>, MutableSet<String>>()
        val users = context.getSystemService(UserManager::class.java)
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        fun label(u: android.os.UserHandle) = if (u == Process.myUserHandle()) "main" else "p${users.getSerialNumberForUser(u)}"
        for (u in launcherApps.profiles) {
            val list = try { launcherApps.getActivityList(null, u) } catch (e: SecurityException) { emptyList() }
            for (a in list) seen.getOrPut(a.componentName.packageName to label(u)) { sortedSetOf() } += a.componentName.className
        }
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        for (r in pm.queryIntentActivities(main, PackageManager.ResolveInfoFlags.of(0))) {
            seen.getOrPut(r.activityInfo.packageName to "main") { sortedSetOf() } += r.activityInfo.name
        }
        val apps = seen.filterKeys { it.first != context.packageName }.map { (key, classes) ->
            val (pkg, user) = key
            App(pkg, user, installerOf(pm, pkg), installedAt(pm, pkg)?.let { day.format(Date(it)) }, classes.map { cls ->
                val c = "$pkg/$cls"
                val d = source.resolveApp(c)
                val inPack = filter?.get(c)
                val arc = if (d >= 0) source.names.name(d) else null
                Component(
                    cls, inPack, arc,
                    packLoad = inPack?.let { packLoad(it) },
                    arcLoad = if (arcRes != null) arc?.let { arcLoad(it) } else null,
                )
            })
        }
        return format(header, apps)
    }

    private const val ARCTICONS = "com.donnnno.arcticons"

    private fun versionOf(pm: PackageManager, pkg: String): String? = try {
        pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun installedAt(pm: PackageManager, pkg: String): Long? = try {
        pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).lastUpdateTime
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun installerOf(pm: PackageManager, pkg: String): String? = try {
        val s = pm.getInstallSourceInfo(pkg)
        s.installingPackageName ?: s.initiatingPackageName
    } catch (e: PackageManager.NameNotFoundException) {
        "?"
    }
}
