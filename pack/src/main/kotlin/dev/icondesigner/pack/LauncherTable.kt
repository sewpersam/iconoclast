package dev.icondesigner.pack

/**
 * Restart guidance for known launchers, spec 6.6. Launchers cache icon packs and only a
 * process restart shows new icons, spec 1.3. Paths were checked on the emulator; see
 * docs/verification/m3.md.
 *
 * @property selectPack how to choose the pack the first time.
 * @property restart how to restart the launcher after an update.
 * @property forceStopFromAppInfo true when the restart is Force stop in App info, which
 *   the designer can deep link to.
 */
data class LauncherGuide(
    val packageName: String,
    val name: String,
    val selectPack: String,
    val restart: String,
    val forceStopFromAppInfo: Boolean,
)

object LauncherTable {
    val known: List<LauncherGuide> = listOf(
        LauncherGuide(
            packageName = "com.saggitt.omega",
            name = "Neo Launcher",
            selectPack = "Long-press the home screen, then Home settings, Profile, Icon pack, and pick the pack.",
            restart = "Long-press the home screen, then Home settings, open the three-dot menu and tap Restart.",
            forceStopFromAppInfo = false,
        ),
        LauncherGuide(
            packageName = "app.lawnchair",
            name = "Lawnchair",
            selectPack = "Long-press the home screen, then Home settings, General, Icon style, and pick the pack.",
            restart = "Long-press the home screen, then Home settings, open the three-dot menu and tap Restart Lawnchair.",
            forceStopFromAppInfo = false,
        ),
        LauncherGuide(
            packageName = "de.mm20.launcher2.release",
            name = "Kvaesitso",
            selectPack = "Open More actions, then Settings, Appearance, Icons, Icon pack, and pick the pack.",
            restart = "Kvaesitso has no restart option. Open its App info and tap Force stop, then press Home.",
            forceStopFromAppInfo = true,
        ),
    )

    private val byPackage = known.associateBy { it.packageName }

    /** The guide for [packageName], or a generic one that restarts through App info. */
    fun guide(packageName: String?, label: String?): LauncherGuide = byPackage[packageName] ?: LauncherGuide(
        packageName = packageName.orEmpty(),
        name = label ?: "your launcher",
        selectPack = "Open your launcher's settings and choose the pack as its icon pack.",
        restart = "Open your launcher's App info and tap Force stop, then press Home.",
        forceStopFromAppInfo = true,
    )
}
