package dev.icondesigner.pack

/**
 * Post-install guidance for known launchers, spec 6.6. Launchers cache icon packs, so an
 * updated pack needs a launcher-specific step before new icons show, spec 1.3. Every
 * path below was walked on the emulator; see docs/verification/m3.md.
 *
 * @property selectPack how to choose the pack the first time.
 * @property refresh how to make the launcher show an updated pack.
 * @property forceStopFromAppInfo true when [refresh] is Force stop in App info, which the
 *   designer deep links to.
 */
data class LauncherGuide(
    val packageName: String,
    val name: String,
    val selectPack: String,
    val refresh: String,
    val forceStopFromAppInfo: Boolean,
)

object LauncherTable {
    private const val FORCE_STOP = "Open its App info, tap Force stop and confirm, then press Home."

    val known: List<LauncherGuide> = listOf(
        // Neo 0.9.3 on the emulator: Restart alone refreshed once and failed once; switching
        // to System icons and back alone failed; Restart then switching worked both times.
        LauncherGuide(
            packageName = "com.saggitt.omega",
            name = "Neo Launcher",
            selectPack = "Long-press the home screen, then Home settings, Profile, Icon Packs, pick the pack and tap OK. " +
                "If the pack is not listed, open the menu at the top right of Home settings, tap Restart, and try again.",
            refresh = "Long-press the home screen, then Home settings. Open the menu at the top right and tap Restart. " +
                "Then open Home settings again, Profile, Icon Packs, pick System icons and tap OK, " +
                "then open Icon Packs again, pick the pack and tap OK.",
            forceStopFromAppInfo = false,
        ),
        // Lawnchair 15 beta 3: "Restart Lawnchair" exists only in its hidden debug menu.
        LauncherGuide(
            packageName = "app.lawnchair",
            name = "Lawnchair",
            selectPack = "Long-press the home screen, then Home settings, General, Icon style, and pick the pack under Icon pack.",
            refresh = FORCE_STOP,
            forceStopFromAppInfo = true,
        ),
        LauncherGuide(
            packageName = "de.mm20.launcher2.release",
            name = "Kvaesitso",
            selectPack = "Tap More actions at the top right of the home screen, then Settings, Grid and icons, Icon pack, and pick the pack.",
            refresh = FORCE_STOP,
            forceStopFromAppInfo = true,
        ),
    )

    private val byPackage = known.associateBy { it.packageName }

    /** The guide for [packageName], or a generic one that restarts through App info. */
    fun guide(packageName: String?, label: String?): LauncherGuide = byPackage[packageName] ?: LauncherGuide(
        packageName = packageName.orEmpty(),
        name = label ?: "your launcher",
        selectPack = "Open your launcher's settings and choose the pack as its icon pack.",
        refresh = FORCE_STOP,
        forceStopFromAppInfo = true,
    )
}
