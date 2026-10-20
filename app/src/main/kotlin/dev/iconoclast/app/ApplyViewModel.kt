package dev.iconoclast.app

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.iconoclast.app.coverage.AppliedCoverage
import dev.iconoclast.app.texture.TexturePresence
import dev.iconoclast.app.theme.ThemeDocument
import dev.iconoclast.app.theme.effectiveGeometry
import dev.iconoclast.app.theme.effectiveTheme
import dev.iconoclast.pack.CoverageMatcher
import dev.iconoclast.pack.DamagedKeyException
import dev.iconoclast.pack.InstallResult
import dev.iconoclast.pack.KeyState
import dev.iconoclast.pack.LauncherGuide
import dev.iconoclast.pack.LauncherTable
import dev.iconoclast.pack.PackInstallSession
import dev.iconoclast.pack.PackSigner
import dev.iconoclast.pack.TooManyIconsException
import dev.iconoclast.render.OutputResolution
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException

sealed interface ApplyState {
    data object Idle : ApplyState
    data class Working(val stage: String, val done: Int = 0, val total: Int = 0) : ApplyState
    /** [leftover] older packs are still installed: their uninstall was cancelled. */
    data class Installed(val guide: LauncherGuide, val themed: Int, val unthemed: Int, val leftover: Int = 0) : ApplyState
    data class Problem(val message: String, val fix: Fix? = null) : ApplyState
}

enum class Fix {
    /** The signing key is damaged: restore it from a backup, or make a new one. */
    DAMAGED_KEY,
}

/**
 * The active theme against the last Apply: [applied] when the installed pack is this
 * theme as it is now, [newApps] apps installed since then that Arcticons covers, and
 * [noIcon] apps that have no Arcticons icon yet.
 */
data class AppliedStatus(val applied: Boolean = false, val newApps: Int = 0, val noIcon: Int = 0)

/** Apply the active theme: match installed apps, render, rewrite, sign, install as a new pack, remove the old one, then guide the pick. */
class ApplyViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<ApplyState>(ApplyState.Idle)
    val state: StateFlow<ApplyState> = _state

    /** Intents the screen must start: install confirmation, settings, uninstall, App info. */
    private val launches = Channel<Intent>(Channel.UNLIMITED)
    val intents = launches.receiveAsFlow()

    private val _status = MutableStateFlow(AppliedStatus())
    val status: StateFlow<AppliedStatus> = _status

    /** Completed by [resumed]: the user came back from a system screen Apply sent them to. */
    private var returned: CompletableDeferred<Unit>? = null

    private val container = app.container
    private val packs = PackFamily(app)
    private val appName = app.getString(R.string.app_name)

    /** The active theme, which Apply installs. */
    val active: StateFlow<ThemeDocument?> = combine(container.themes.themes, container.themes.activeId) { themes, id ->
        themes.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun apply() {
        val doc = active.value ?: return
        if (_state.value is ApplyState.Working) return
        _state.value = ApplyState.Working("Reading icons")
        viewModelScope.launch {
            _state.value = try {
                run(doc)
            } catch (e: DamagedKeyException) {
                Log.e(TAG, "Apply failed", e)
                damagedKey()
            } catch (e: TooManyIconsException) {
                ApplyState.Problem("${e.needed} apps have icons, but a pack holds at most ${e.available}. Nothing was installed.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Apply failed", e)
                ApplyState.Problem("Apply failed: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                returned = null
            }
            refreshStatus()
        }
    }

    /** The Apply screen is showing again. Resumes an Apply waiting on a system screen. */
    fun resumed() {
        foreground = true
        returned?.complete(Unit)
        if (_state.value !is ApplyState.Working) {
            refreshStatus()
            refreshNeo()
            runNeoIfDue()
        }
    }

    /**
     * Shows [line], gives the user [readMs] to read it, opens [intent], and waits until they
     * are back, or [timeoutMs] when given. Returns false on the timeout.
     */
    private suspend fun sendAway(line: String, intent: Intent, readMs: Long = READ_MS, timeoutMs: Long? = null): Boolean {
        _state.value = ApplyState.Working(line)
        delay(readMs)
        val back = CompletableDeferred<Unit>().also { returned = it }
        launches.trySend(intent)
        val ok = if (timeoutMs == null) { back.await(); true } else withTimeoutOrNull(timeoutMs) { back.await() } != null
        returned = null
        return ok
    }

    /** The theme the last result on screen belongs to. */
    private var shownFor: String? = null

    /** Recomputes [status] for the active theme. A result shown for another theme is cleared. */
    fun refreshStatus() {
        val doc = active.value ?: return
        if (shownFor != doc.id && _state.value !is ApplyState.Working) _state.value = ApplyState.Idle
        shownFor = doc.id
        viewModelScope.launch {
            _status.value = try {
                withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val icons = container.iconSource()
                    val installed = InstalledApps.launchableComponents(app)
                    val now = CoverageMatcher.match(installed, Int.MAX_VALUE, icons::resolveApp)
                    val last = container.coverage.read()
                    val same = last != null && packs.current?.let(packs::isOnPhone) == true && last.themeId == doc.id &&
                        last.look == BuiltPack.look(doc, container.settings.resolution.value)
                    val newApps = if (last == null) 0 else now.items.map { it.first }.filter { it !in last.components }
                        .mapTo(HashSet()) { it.substringBefore('/') }.size
                    AppliedStatus(same, newApps, now.uncoveredApps)
                }.also {
                    // After an edit the result of the last Apply no longer describes this theme.
                    if (!it.applied && _state.value is ApplyState.Installed) _state.value = ApplyState.Idle
                }
            } catch (e: IOException) {
                Log.w(TAG, "Applied status not read", e)
                AppliedStatus()
            }
        }
    }

    private suspend fun run(doc: ThemeDocument): ApplyState {
        val theme = doc.theme
        val app = getApplication<Application>()
        // Before drawing anything: a damaged key would fail the pack only after the render.
        if (withContext(Dispatchers.IO) { container.packKey.state() } == KeyState.DAMAGED) return damagedKey()
        val t0 = SystemClock.elapsedRealtime()
        val (icons, template) = withContext(Dispatchers.IO) {
            container.iconSource() to app.assets.open(PackBuilder.TEMPLATE_ASSET).use { it.readBytes() }
        }
        // The renderer throws on a texture it does not hold: check before drawing anything.
        // Overrides add no texture, so the document theme's textures are every slot's.
        val missing = withContext(Dispatchers.IO) { TexturePresence.ensure(container.textures, theme) }
        if (missing.isNotEmpty()) return ApplyState.Problem(
            "Nothing was installed: this theme uses an image that is not on this phone. " +
                "Import the theme's .zip again, or delete the image layer in the editor.",
        )
        // Before drawing anything: without the install permission the pack could not be installed.
        if (!askToInstall()) return needsPermission()
        val components = withContext(Dispatchers.IO) { InstalledApps.launchableComponents(app) }
        val plan = CoverageMatcher.match(components, PackBuilder.slotCount(template), icons::resolveApp)
        val key = withContext(Dispatchers.IO) { container.packKey.loadOrCreate() }
        val resolution = container.settings.resolution.value
        val dir = withContext(Dispatchers.IO) { File(app.cacheDir, "pack").apply { mkdirs() } }
        val built = BuiltPack(dir)
        val build = app.packageManager.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(0)).let { "${it.longVersionCode}@${it.lastUpdateTime}" }
        // A fresh package name per Apply: launchers that cache icons by pack name then load
        // every icon again. An Apply that stopped before the install gets the same name back.
        val name = withContext(Dispatchers.IO) { packs.nextName() }
        val recipe = withContext(Dispatchers.Default) { BuiltPack.key(doc, plan, resolution, template, key.certificate.encoded, build, name) }
        val t1 = SystemClock.elapsedRealtime()

        // An Apply that stopped before the install left this same pack: install it as it is.
        var drawn = false
        val apk = withContext(Dispatchers.IO) { built.find(recipe) } ?: run {
            drawn = true
            _state.value = ApplyState.Working("Drawing icons", 0, plan.drawables.size + 1)
            val unsigned = dir.resolve("unsigned.apk")
            val look = { name: String -> SlotLook(doc.effectiveGeometry(name), doc.effectiveTheme(name)) }
            PackBuilder(icons, container.renderer).build(template, plan, look, dir.resolve("icons"), unsigned, resolution, name) { done, total ->
                _state.value = ApplyState.Working("Drawing icons", done, total)
            }
            _state.value = ApplyState.Working("Signing the pack")
            withContext(Dispatchers.IO) {
                val signed = dir.resolve("pack.apk")
                PackSigner.sign(unsigned, key, signed)
                unsigned.delete()
                built.keep(recipe, signed)
            }
        }
        val t3 = SystemClock.elapsedRealtime()
        Log.i(TAG, "apply: ${resolution.px} px, ${components.size} activities, ${plan.items.size} themed, ${plan.drawables.size} slots; " +
            "load ${t1 - t0} ms, " +
            (if (drawn) "render+rewrite+sign ${t3 - t1} ms" else "built pack reused in ${t3 - t1} ms") + ", ${apk.length()} bytes")

        val session = PackInstallSession(app)
        // The new name is never installed, so whatever key signed the older packs, this one
        // installs beside them; they are removed only once it is in.
        _state.value = ApplyState.Working("Installing")
        var result = session.install(apk, name) { launches.trySend(it) }
        // The checks above can race the system: the switch turned off meanwhile.
        if (result == InstallResult.NeedsPermission && askToInstall()) result = session.install(apk, name) { launches.trySend(it) }
        Log.i(TAG, "install $name: $result in ${SystemClock.elapsedRealtime() - t3} ms")
        if (result != InstallResult.Success) return when (result) {
            InstallResult.NeedsPermission -> needsPermission()
            is InstallResult.SignatureMismatch -> ApplyState.Problem("Android refused the pack: ${result.detail ?: "signature mismatch"}. Tap Apply to try again.")
            is InstallResult.Failed -> ApplyState.Problem(result.message)
            InstallResult.Success -> error("unreachable")
        }
        withContext(Dispatchers.IO) { packs.installed(name) }
        recordCoverage(doc, components, resolution)
        val leftover = removeOldPacks(session, name)
        val guide = currentLauncher()
        // Neo custom icons point at a pack by name and drawable: move the user's to this one.
        val neoNote = if (guide.packageName == NeoCustomIcons.NEO) pointNeoIcons(name, ask = true) else null
        _neoIcons.value = _neoIcons.value.copy(note = neoNote)
        refreshNeo()
        return ApplyState.Installed(guide, plan.themedApps, plan.uncoveredApps, leftover)
    }

    /**
     * Uninstalls every pack but [keep] through the system's confirmation, one prompt each.
     * Returns how many are still installed: a cancelled prompt leaves its pack, and the
     * next Apply, or [removeLeftovers], asks again.
     */
    private suspend fun removeOldPacks(session: PackInstallSession, keep: String): Int {
        val old = withContext(Dispatchers.IO) { packs.onPhone() - keep }
        for (pkg in old) {
            _state.value = ApplyState.Working("The new pack is installed. Tap OK to remove the old copy.")
            delay(READ_MS)
            val gone = session.uninstall(pkg) { launches.trySend(it) }
            Log.i(TAG, "old pack $pkg uninstalled: $gone")
        }
        return withContext(Dispatchers.IO) { (packs.onPhone() - keep).size }
    }

    /** Asks again to remove the older packs an earlier uninstall prompt left. */
    fun removeLeftovers() {
        val shown = _state.value as? ApplyState.Installed ?: return
        val keep = packs.current ?: return
        viewModelScope.launch {
            val left = removeOldPacks(PackInstallSession(getApplication()), keep)
            _state.value = shown.copy(leftover = left)
            runNeoIfDue()
        }
    }

    /**
     * True when the designer may install apps. If not, sends the user to the one switch
     * in Settings and checks again when they are back.
     */
    private suspend fun askToInstall(): Boolean {
        val app = getApplication<Application>()
        if (app.packageManager.canRequestPackageInstalls()) return true
        sendAway(
            "Android asks you once to let $appName install apps. Turn on the switch, then come back.",
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}")),
        )
        return app.packageManager.canRequestPackageInstalls()
    }

    private fun needsPermission() = ApplyState.Problem(
        "Nothing was installed: $appName may not install apps yet. Tap Apply and turn on the switch when Android asks.",
    )

    /** The pack is installed whatever happens here; a failed write only loses the next diff. */
    private suspend fun recordCoverage(doc: ThemeDocument, components: Set<String>, resolution: OutputResolution) = withContext(Dispatchers.IO) {
        try {
            container.coverage.write(AppliedCoverage(doc.id, System.currentTimeMillis(), components, BuiltPack.look(doc, resolution)))
        } catch (e: IOException) {
            Log.w(TAG, "Coverage of this Apply was not saved", e)
        }
    }

    /** M7 addendum, spec 6.5: the key files are there and do not open. Nothing was installed. */
    private fun damagedKey() = ApplyState.Problem(
        "The pack key on this phone is damaged, so $appName cannot sign the pack. Nothing was installed. " +
            "Restore the key from a theme backup that includes it, or make a new key.",
        Fix.DAMAGED_KEY,
    )

    /**
     * Sets the damaged key files aside and applies again. The new key's pack installs under
     * a new name like every Apply, and the old key's pack is removed after it.
     */
    fun newKey() {
        if (_state.value is ApplyState.Working) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.packKey.setAsideDamaged() }
            apply()
        }
    }

    private val _copied = MutableStateFlow<String?>(null)

    /** The line shown after Copy diagnostics, or null before. */
    val copied: StateFlow<String?> = _copied

    /** Puts [Diagnostics] on the clipboard, for a bug report pasted into a chat. */
    fun copyDiagnostics() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _copied.value = try {
                val report = withContext(Dispatchers.IO) {
                    Diagnostics.collect(app, container.iconSource(), packs, container.coverage.read()?.appliedAt)
                }
                report.lines().forEach { Log.i(TAG, "diag: $it") }
                app.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Iconoclast diagnostics", report))
                "Copied ${report.lines().count { it.isNotEmpty() }} lines. Paste them into the chat."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Diagnostics failed", e)
                "Diagnostics failed: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    private val neo = NeoCustomIcons(app)
    private val _neoIcons = MutableStateFlow(NeoIconsState())

    /** Neo custom icons: shown while Neo is the home app. */
    val neoIcons: StateFlow<NeoIconsState> = _neoIcons

    /** The Apply screen is in front: a run opens the next Neo screen only then. */
    private var foreground = false
    private var neoRunning = false

    /** The Apply screen went to the background. */
    fun paused() {
        foreground = false
    }

    private fun neoHome() = neo.neoInstalled() && currentLauncher().packageName == NeoCustomIcons.NEO

    private fun refreshNeo() {
        _neoIcons.value = _neoIcons.value.copy(home = neoHome(), serviceOn = serviceOn())
    }

    /** Opens Accessibility settings, where the user turns the service on; the run starts when they are back. */
    fun turnOnNeoService() {
        launches.trySend(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /**
     * Starts a run when the installed pack is not yet on every app in Neo: the service was
     * just turned on, Neo just became home, or a run was cut off. Nothing runs while Apply does.
     */
    private fun runNeoIfDue() {
        if (neoRunning || _state.value is ApplyState.Working) return
        val pack = packs.current?.takeIf(packs::isOnPhone) ?: return
        if (!neo.needsRun(pack) || !neoHome() || !serviceOn() || (packs.onPhone() - pack).isNotEmpty()) return
        val shown = _state.value
        viewModelScope.launch {
            val note = pointNeoIcons(pack, ask = false)
            if (_state.value is ApplyState.Working) _state.value = shown
            _neoIcons.value = _neoIcons.value.copy(note = note)
            refreshNeo()
        }
    }

    private fun serviceOn(): Boolean {
        val am = getApplication<Application>().getSystemService(AccessibilityManager::class.java)
        return NeoIconService.running || am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == getApplication<Application>().packageName }
    }

    /** Waits until the Apply screen is in front again. */
    private suspend fun awaitForeground() {
        while (!foreground) {
            val back = CompletableDeferred<Unit>().also { returned = it }
            back.await()
            returned = null
        }
    }

    /**
     * Points every app the pack has an icon for at [pack] in Neo: opens Neo's icon screen per
     * app and [NeoIconService] taps the pack's icon. Neo cannot say which apps have a custom
     * icon, so all of them get one. Without the service nothing is opened: the Apply screen
     * says to turn it on, once sending the user there when [ask]. Returns the line to show
     * after, or null when nothing ran.
     */
    private suspend fun pointNeoIcons(pack: String, ask: Boolean): String? {
        if (!neo.neoInstalled()) return null
        if ((packs.onPhone() - pack).isNotEmpty()) return "An older copy of the pack is still installed. Remove it, then every app in Neo gets the pack's icon."
        if (!serviceOn() && ask && !neo.askedForService) {
            neo.askedForService = true
            sendAway(
                "Neo keeps icons you set by hand over any pack. Turn on $appName for Neo custom icons, then come back: every app gets the pack's icon. " +
                    "If the switch is greyed out: App info for $appName, menu at the top right, Allow restricted settings.",
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            )
        }
        if (!serviceOn()) return null
        neoRunning = true
        try {
            neo.begin(pack)
            _state.value = ApplyState.Working("Finding the pack's icon in Neo")
            val t0 = SystemClock.elapsedRealtime()
            val plan = withContext(Dispatchers.IO) { neo.targets(pack) }
            val all = plan.targets
            val earlier = neo.done
            var ok = all.count { it.key in earlier }
            var failed = 0
            val t1 = SystemClock.elapsedRealtime()
            for (t in all) {
                if (t.key in earlier) continue
                if (!serviceOn()) {
                    return "Stopped: $appName for Neo custom icons is off. $ok of ${all.size} apps have the pack's icon. Turn it on to go on."
                }
                // Home pressed just as Neo's screen closed reaches this screen a few ms after
                // it resumed; launching then would pull the task back in front of the home screen.
                do {
                    awaitForeground()
                    delay(SETTLE_MS)
                } while (!foreground)
                _state.value = ApplyState.Working("Giving every app in Neo the pack's icon", ok + failed, all.size)
                val before = NeoIconService.tapped
                NeoIconService.pending = t
                val back = CompletableDeferred<Unit>().also { returned = it }
                launches.trySend(neo.editIntent(t))
                while (withTimeoutOrNull(NEO_TAP_MS) { back.await() } == null) {
                    // Neo's screen is in front with no tap: leave it. Not in front: the
                    // screen locked or the user left. Wait for them: the service taps it
                    // when it shows again, or this goes on when the Apply screen does.
                    if (NeoIconService.leaveIfStuck(t)) {
                        withTimeoutOrNull(NEO_TAP_MS) { back.await() }
                        break
                    }
                }
                returned = null
                NeoIconService.pending = null
                if (NeoIconService.tapped > before) {
                    ok++
                    neo.markDone(t.key)
                } else {
                    failed++
                }
            }
            neo.finish()
            val t2 = SystemClock.elapsedRealtime()
            Log.i(TAG, "neo: $pack on $ok of ${all.size} apps, $failed missed, ${plan.skipped} not in the pack; " +
                "targets ${t1 - t0} ms, ${all.size - earlier.size} screens ${t2 - t1} ms")
            return "Neo shows the pack's icon on $ok apps." +
                (if (failed > 0) " $failed did not take it and keep the icon ${if (failed == 1) "it" else "they"} had." else "") +
                (if (plan.skipped > 0) " ${plan.skipped} ${if (plan.skipped == 1) "has" else "have"} no icon in the pack." else "")
        } finally {
            NeoIconService.pending = null
            returned = null
            neoRunning = false
        }
    }
    private fun currentLauncher(): LauncherGuide {
        val pm = getApplication<Application>().packageManager
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = pm.resolveActivity(home, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))?.activityInfo
        // "android" is the chooser: no default home app is set.
        val pkg = info?.packageName?.takeIf { it != "android" }
        return LauncherTable.guide(pkg, info?.takeIf { pkg != null }?.applicationInfo?.loadLabel(pm)?.toString())
    }

    companion object {
        private const val TAG = "Iconoclast"

        /** How long a line explaining the next system screen stays before that screen opens. */
        private const val READ_MS = 1500L

        /** How long Neo's icon screen may take to open and take the tap. */
        private const val NEO_TAP_MS = 8000L

        /** How long the Apply screen must stay in front before the next Neo screen opens. */
        private const val SETTLE_MS = 50L
    }
}

/** Neo custom icons: [home] Neo is the home app, [serviceOn] the service is on, [note] the last run's result. */
data class NeoIconsState(val home: Boolean = false, val serviceOn: Boolean = false, val note: String? = null)
