package dev.iconoclast.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Neo Launcher 0.9.3 custom icons. Neo draws an app's custom icon (long-press, Customize,
 * the icon) before any pack, whichever pack is picked, and 0.9.3 cannot remove one. It
 * can only point it at another icon. Its own icon screen does that and is open to other
 * apps: `ComposeActivity` is exported and takes a navigation deep link per app. Nothing
 * outside Neo can tell which apps have one, so every app the pack has an icon for is
 * pointed at the pack: this opens that screen per app and [NeoIconService] taps the pack's
 * icon in its top row. Every Apply installs the pack under a new name with new drawable
 * names, so every app is pointed again at each new pack.
 */
class NeoCustomIcons(private val context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    init {
        // Before the shotgun the user ticked apps; every app is pointed now.
        if (prefs.contains(OLD_APPS)) prefs.edit().remove(OLD_APPS).apply()
    }

    /**
     * One app to point at the pack: tap [index] of [total] icons in the top row of the screen
     * titled [label]. Neo keys a custom icon by component and user.
     */
    data class Target(val component: ComponentName, val user: UserHandle, val label: String, val index: Int, val total: Int) {
        /** Neo's `ComponentKey.toString`: the user is `UserHandle.hashCode`, its id. */
        val key: String get() = "${component.flattenToString()}#${user.hashCode()}"
    }

    /** The pack the last run pointed apps at, null before any run. */
    val pack: String? get() = prefs.getString(PACK, null)

    /** Apps, as [Target.key], already pointed at [pack]. */
    val done: Set<String> get() = prefs.getStringSet(DONE, emptySet())!!.toSet()

    /** The run for [pack] went through the whole list; false while one is under way or was cut off. */
    val finished: Boolean get() = prefs.getBoolean(FINISHED, false)

    /** Every app still has to be pointed at [installed]: a new pack, or a run cut off. */
    fun needsRun(installed: String): Boolean = pack != installed || !finished

    /** Starts a run for [installed], keeping what an earlier cut-off run for it did. */
    fun begin(installed: String) {
        if (pack == installed) prefs.edit().putBoolean(FINISHED, false).apply()
        else prefs.edit().putString(PACK, installed).putStringSet(DONE, emptySet()).putBoolean(FINISHED, false).apply()
    }

    fun markDone(key: String) {
        prefs.edit().putStringSet(DONE, done + key).apply()
    }

    fun finish() {
        prefs.edit().putBoolean(FINISHED, true).apply()
    }

    /** Iconoclast has sent the user to Accessibility settings once already. */
    var askedForService: Boolean
        get() = prefs.getBoolean(ASKED, false)
        set(value) {
            prefs.edit().putBoolean(ASKED, value).apply()
        }

    /** Neo's icon screen for [t], as Neo opens it itself. */
    fun editIntent(t: Target): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("android-app://androidx.navigation//edit_icon/${t.key}/"))
            .setClassName(NEO, "$NEO.compose.ComposeActivity")

    fun neoInstalled(): Boolean = installed(NEO)

    /**
     * Every launchable activity Neo lists, in every profile that is open (a paused work
     * profile lists none), as (component, user, label). Each launcher activity is its own
     * Neo icon, so a package with two is two entries.
     */
    private fun launchable(): List<Triple<ComponentName, UserHandle, String>> {
        val la = context.getSystemService(LauncherApps::class.java)
        val um = context.getSystemService(UserManager::class.java)
        val users = try { la.profiles } catch (e: SecurityException) { listOf(Process.myUserHandle()) }
        return users.filter { it == Process.myUserHandle() || !um.isQuietModeEnabled(it) }.flatMap { u ->
            try {
                la.getActivityList(null, u).map { Triple(it.componentName, u, it.label.toString()) }
            } catch (e: SecurityException) {
                emptyList()
            }
        }.distinctBy { (c, u) -> c to u }
    }

    /**
     * Every app to point at [pack], with where its icon sits in Neo's top row. Apps the
     * pack has no icon for are left out: they keep whatever Neo shows now. Call off the main
     * thread: it reads every pack's appfilter.
     */
    fun targets(pack: String): Plan {
        val order = NeoRow.order(packList(), lawnicons = installed(LAWNICONS))
        val packs = HashMap<String, Map<ComponentName, String>?>()
        val resources = HashMap<String, Resources?>()
        fun shows(pkg: String, c: ComponentName): Boolean {
            if (pkg == "") return true // System icons: the app's own icon
            val res = resources.getOrPut(pkg) { resourcesOf(pkg) } ?: return false
            val drawable = packs.getOrPut(pkg) { appfilter(pkg, res) }?.get(c) ?: return false
            return res.getIdentifier(drawable, "drawable", pkg) != 0
        }
        val apps = launchable()
        val targets = apps.mapNotNull { (c, u, label) ->
            val at = NeoRow.position(order.map { NeoRow.Entry(it, shows(it, c)) }, pack) ?: return@mapNotNull null
            Target(c, u, label, at.first, at.second)
        }.sortedBy { it.label.lowercase() }
        return Plan(targets, apps.size - targets.size)
    }

    /** [targets] to point at the pack; [skipped] launchable apps the pack has no icon for. */
    data class Plan(val targets: List<Target>, val skipped: Int)

    /** Neo's `IconPackProvider.getIconPackList` packs: every icon pack intent, in that order, as (package, label). */
    private fun packList(): List<Pair<String, String>> {
        val pm = context.packageManager
        return ICON_INTENTS.flatMap { pm.queryIntentActivities(Intent(it), PackageManager.ResolveInfoFlags.of(0)) }
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
    }

    private fun installed(pkg: String) = try {
        context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun resourcesOf(pkg: String): Resources? = try {
        context.packageManager.getResourcesForApplication(pkg)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** A pack's appfilter items as Neo's `CustomIconPack.loadInternal` reads them: `res/xml/appfilter` first, else `assets/appfilter.xml`. */
    private fun appfilter(pkg: String, res: Resources): Map<ComponentName, String>? = try {
        val id = res.getIdentifier("appfilter", "xml", pkg)
        val parser: XmlPullParser = if (id != 0) res.getXml(id) else
            XmlPullParserFactory.newInstance().newPullParser().apply { setInput(res.assets.open("appfilter.xml"), Xml.Encoding.UTF_8.toString()) }
        val out = HashMap<ComponentName, String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG || parser.name != "item") continue
            var component = parser.getAttributeValue(null, "component") ?: continue
            val drawable = parser.getAttributeValue(null, "drawable") ?: continue
            if (component.startsWith("ComponentInfo{") && component.endsWith("}")) component = component.substring(14, component.length - 1)
            ComponentName.unflattenFromString(component)?.let { out[it] = drawable }
        }
        out
    } catch (e: Exception) {
        null
    }

    companion object {
        const val NEO = "com.saggitt.omega"
        private const val LAWNICONS = "app.lawnchair.lawnicons"
        private const val FILE = "neo"
        private const val OLD_APPS = "custom_icon_apps"
        private const val PACK = "pack"
        private const val DONE = "done"
        private const val FINISHED = "finished"
        private const val ASKED = "asked_for_service"

        /** Neo 0.9.3 `Config.ICON_INTENTS`, in its order. The manifest queries each. */
        val ICON_INTENTS = listOf(
            "com.novalauncher.THEME",
            "org.adw.launcher.THEMES",
            "org.adw.launcher.icons.ACTION_PICK_ICON",
            "com.anddoes.launcher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.fede.launcher.THEME_ICONPACK",
            "com.gau.go.launcherex.theme",
            "com.dlto.atom.launcher.THEME",
        )
    }
}

/** The order of Neo 0.9.3's icon screen top row, from `IconPackProvider.getIconPackList` and `EditIconScreen`. */
object NeoRow {
    /** A pack in list order; [shows] when the top row has an icon from it for the app. */
    data class Entry(val pkg: String, val shows: Boolean)

    /**
     * Neo's list: System icons (""), Lawnicons when installed, Themed Icons (never a pack, so
     * never shown), then the packs from [found] (package, label), one per package keeping its
     * first place and last label, sorted by label.
     */
    fun order(found: List<Pair<String, String>>, lawnicons: Boolean): List<String> {
        val byPkg = LinkedHashMap<String, String>()
        for ((pkg, label) in found) byPkg[pkg] = label
        val packs = byPkg.entries.sortedBy { it.value }.map { it.key }
        return listOfNotNull("", "app.lawnchair.lawnicons".takeIf { lawnicons }, "themed") + packs
    }

    /** (index, total) of [pack] among the icons the top row shows, or null when it shows none from it or more than one. */
    fun position(entries: List<Entry>, pack: String): Pair<Int, Int>? {
        val shown = entries.filter { it.shows }
        if (shown.count { it.pkg == pack } != 1) return null
        return shown.indexOfFirst { it.pkg == pack } to shown.size
    }
}
