package dev.iconoclast.pack

/**
 * Post-install guidance for known launchers, spec 6.6. Every Apply installs the pack under
 * a new package name ([PackName]) and removes the old one, so a launcher meets a pack it
 * has never cached and loads every icon fresh. Picking the new pack once is the whole
 * step in Lawnchair and Kvaesitso: no force stop, no switching away and back. Neo 0.9.3
 * builds its Icon Packs list once per process (OmegaPreferences.themeIconPackGlobal), so
 * a pack installed after Neo started is listed only after its Restart; the restart then
 * the pick is order-proof, since picking first changes nothing. The pick needs one more
 * Restart after it: Neo reads a newly picked pack's appfilter on a background thread
 * while it already redraws the home screen (CustomIconProvider.resolveIconEntry never
 * waits for IconPack.loadBlocking), so the icons it draws first keep their own icon, and
 * it caches them until each app updates. A Restart reads the pack before the home screen
 * loads and redraws every icon. See docs/verification/pack-rename.md and
 * docs/verification/neo-pick-race.md.
 *
 * @property pick how to choose the pack as the launcher's icon pack.
 */
data class LauncherGuide(
    val packageName: String,
    val name: String,
    val pick: String,
)

object LauncherTable {
    val known: List<LauncherGuide> = listOf(
        LauncherGuide(
            packageName = "com.saggitt.omega",
            name = "Neo Launcher",
            pick = "Long-press the home screen, then Home settings. Open the menu at the top right and tap Restart: " +
                "Neo lists a new pack only after it restarts. Then open Home settings again, Profile, Icon Packs, " +
                "pick the pack and tap OK. Then tap Restart once more: Neo draws your home screen apps before it has read " +
                "a newly picked pack, and they keep their own icons until it restarts.",
        ),
        LauncherGuide(
            packageName = "app.lawnchair",
            name = "Lawnchair",
            pick = "Long-press the home screen, then Home settings, General, Icon style, and pick the pack under Icon pack.",
        ),
        LauncherGuide(
            packageName = "de.mm20.launcher2.release",
            name = "Kvaesitso",
            pick = "Tap More actions at the top right of the home screen, then Settings, Grid and icons, Icon pack, and pick the pack.",
        ),
    )

    private val byPackage = known.associateBy { it.packageName }

    /** The guide for [packageName], or a generic one. */
    fun guide(packageName: String?, label: String?): LauncherGuide = byPackage[packageName] ?: LauncherGuide(
        packageName = packageName.orEmpty(),
        name = label ?: "your launcher",
        pick = "Open your launcher's settings and choose the pack as its icon pack.",
    )
}
