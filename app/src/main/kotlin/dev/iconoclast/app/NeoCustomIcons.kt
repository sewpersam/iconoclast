package dev.iconoclast.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Process
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Neo Launcher 0.9.3 custom icons. Neo draws an app's custom icon (long-press, Customize,
 * the icon) before any pack, whichever pack is picked, and 0.9.3 cannot remove one. It
 * can only point it at another icon. Its own icon screen does that and is open to other
 * apps: `ComposeActivity` is exported and takes a navigation deep link per app. So for each
 * app the user names, this opens that screen and [NeoIconService] taps the pack's icon in
 * its top row. Every Apply installs the pack under a new name with new drawable names, so
 * the apps are kept and pointed at each new pack.
 */
class NeoCustomIcons(private val context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** One app to point at the pack: tap [index] of [total] icons in the top row of the screen titled [label]. */
    data class Target(val component: ComponentName, val label: String, val index: Int, val total: Int)

    /** The apps, as `package/class`, the user keeps pointed at the pack. */
    var apps: Set<String>
        get() = prefs.getStringSet(APPS, emptySet())!!.toSet()
        set(value) {
            prefs.edit().putStringSet(APPS, value).commit()
        }

    /** Neo's icon screen for [component], as Neo opens it itself. */
    fun editIntent(component: ComponentName): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("android-app://androidx.navigation//edit_icon/${component.flattenToString()}#0/"))
            .setClassName(NEO, "$NEO.compose.ComposeActivity")

    fun neoInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(NEO, PackageManager.PackageInfoFlags.of(0)); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Apps Neo lists for this user: component, label as Neo's icon screen titles it, and the app's own icon. */
    fun launcherApps(): List<Triple<ComponentName, String, Bitmap>> =
        context.getSystemService(LauncherApps::class.java).getActivityList(null, Process.myUserHandle())
            .filter { it.componentName.packageName != context.packageName }
            .map { Triple(it.componentName, it.label.toString(), bitmap(it.getIcon(0))) }

    /** For the list only: the pack's icons are drawn by the renderer, never here. */
    private fun bitmap(d: Drawable): Bitmap {
        val px = (40 * context.resources.displayMetrics.density).toInt()
        return Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { b ->
            d.setBounds(0, 0, px, px)
            d.draw(Canvas(b))
        }
    }

    /**
     * Where [pack]'s icon sits in Neo's top row for each of [components]. Apps the pack has
     * no icon for are left out. Call off the main thread: it reads every pack's appfilter.
     */
    fun targets(pack: String, components: Collection<ComponentName>): List<Target> {
        val order = NeoRow.order(packList(), lawnicons = installed(LAWNICONS))
        val packs = HashMap<String, Map<ComponentName, String>?>()
        val resources = HashMap<String, Resources?>()
        fun shows(pkg: String, c: ComponentName): Boolean {
            if (pkg == "") return true // System icons: the app's own icon
            val res = resources.getOrPut(pkg) { resourcesOf(pkg) } ?: return false
            val drawable = packs.getOrPut(pkg) { appfilter(pkg, res) }?.get(c) ?: return false
            return res.getIdentifier(drawable, "drawable", pkg) != 0
        }
        val la = context.getSystemService(LauncherApps::class.java)
        return components.mapNotNull { c ->
            val label = la.resolveActivity(Intent().setComponent(c), Process.myUserHandle())?.label?.toString() ?: return@mapNotNull null
            val at = NeoRow.position(order.map { NeoRow.Entry(it, shows(it, c)) }, pack) ?: return@mapNotNull null
            Target(c, label, at.first, at.second)
        }
    }

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
        private const val APPS = "custom_icon_apps"

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
