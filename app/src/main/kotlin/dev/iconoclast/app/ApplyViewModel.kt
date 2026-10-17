package dev.iconoclast.app

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
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
        returned?.complete(Unit)
        if (_state.value !is ApplyState.Working) refreshStatus()
    }

    /** Shows [line], gives the user a moment to read it, opens [intent], and waits until they are back. */
    private suspend fun sendAway(line: String, intent: Intent) {
        _state.value = ApplyState.Working(line)
        delay(READ_MS)
        val back = CompletableDeferred<Unit>().also { returned = it }
        launches.trySend(intent)
        back.await()
        returned = null
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
        return ApplyState.Installed(currentLauncher(), plan.themedApps, plan.uncoveredApps, leftover)
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

    private val _neoBackup = MutableStateFlow<NeoBackupState?>(null)

    /** What Check Neo backup found, or null before. */
    val neoBackup: StateFlow<NeoBackupState?> = _neoBackup

    private fun installed(pkg: String) = try {
        getApplication<Application>().packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * Reads a Neo backup the user picked and lists its custom icons, on screen, on the
     * clipboard and in logcat. The file is only read.
     */
    fun checkNeoBackup(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _neoBackup.value = try {
                val lines = withContext(Dispatchers.IO) {
                    val zip = app.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    val found = NeoBackup.overrides(zip, File(app.cacheDir, "neo-backup"))
                    File(app.cacheDir, "neo-backup").deleteRecursively()
                    found?.let { NeoBackup.format(it, ::installed) }
                }
                if (lines == null) {
                    NeoBackupState(uri, listOf("This file is not a Neo backup with the home screen in it."), 0)
                } else {
                    lines.forEach { Log.i(TAG, "diag: $it") }
                    app.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Iconoclast diagnostics", lines.joinToString("\n")))
                    NeoBackupState(uri, lines, lines.size - 1)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Neo backup check failed", e)
                NeoBackupState(null, listOf("Could not read it: ${e.message ?: e.javaClass.simpleName}"), 0)
            }
        }
    }

    /** Writes a copy of the checked Neo backup to [dest] without custom icons. The checked file is untouched. */
    fun saveNeoBackupWithoutCustomIcons(dest: Uri) {
        val app = getApplication<Application>()
        val checked = _neoBackup.value ?: return
        val src = checked.source ?: return
        viewModelScope.launch {
            val note = try {
                withContext(Dispatchers.IO) {
                    val zip = app.contentResolver.openInputStream(src)!!.use { it.readBytes() }
                    val work = File(app.cacheDir, "neo-backup")
                    val cleaned = NeoBackup.withoutOverrides(zip, work) ?: error("no home screen database")
                    // Read the copy back before saying it is good.
                    val left = NeoBackup.overrides(cleaned, work)
                    work.deleteRecursively()
                    check(left != null && left.isEmpty()) { "the copy still has custom icons" }
                    app.contentResolver.openOutputStream(dest, "wt")!!.use { it.write(cleaned) }
                }
                "Saved. In Neo: Home settings, Backups, Restore, pick this copy, OK. Then Profile, Icon Packs: " +
                    "pick System icons, OK; then pick $appName Pack, OK."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Neo backup clean failed", e)
                "Could not save it: ${e.message ?: e.javaClass.simpleName}"
            }
            _neoBackup.value = checked.copy(saved = note)
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
    }
}

/** A checked Neo backup: [source] the picked file, [lines] the report, [custom] its custom icons, [saved] the result of saving a clean copy. */
data class NeoBackupState(val source: Uri?, val lines: List<String>, val custom: Int, val saved: String? = null)
