package dev.iconoclast.app

import android.app.Application
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
import kotlinx.coroutines.CancellationException
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
    data class Installed(val firstInstall: Boolean, val guide: LauncherGuide, val themed: Int, val unthemed: Int) : ApplyState
    data class Problem(val message: String, val fix: Fix? = null) : ApplyState
}

enum class Fix {
    GRANT_INSTALL_PERMISSION,
    UNINSTALL_OLD_PACK,

    /** The signing key is damaged: restore it from a backup, or make a new one. */
    DAMAGED_KEY,
}

/** Apply the active theme: match installed apps, render, rewrite, sign, install, then guide the restart. */
class ApplyViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<ApplyState>(ApplyState.Idle)
    val state: StateFlow<ApplyState> = _state

    /** Intents the screen must start: install confirmation, settings, uninstall, App info. */
    private val launches = Channel<Intent>(Channel.UNLIMITED)
    val intents = launches.receiveAsFlow()

    private val container = app.container
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
            "Nothing was installed: this theme uses a texture that is not on this phone. " +
                "Import the theme's .zip again, or remove the texture layer in the editor.",
        )
        // Before drawing anything: without the install permission the pack could not be installed.
        if (!app.packageManager.canRequestPackageInstalls()) return needsPermission()
        val components = withContext(Dispatchers.IO) { InstalledApps.launchableComponents(app) }
        val plan = CoverageMatcher.match(components, PackBuilder.slotCount(template), icons::resolve)
        val key = withContext(Dispatchers.IO) { container.packKey.loadOrCreate() }
        // Spec 6.5: a pack from an earlier install of the designer has another key. Say so
        // before drawing, and before the system offers an update that can only fail.
        if (installedPackSigner()?.let { !it.contentEquals(key.certificate.encoded) } == true) return mismatch()
        val resolution = container.settings.resolution.value
        val dir = withContext(Dispatchers.IO) { File(app.cacheDir, "pack").apply { mkdirs() } }
        val built = BuiltPack(dir)
        val recipe = withContext(Dispatchers.Default) { BuiltPack.key(doc, plan, resolution, template, key.certificate.encoded) }
        val t1 = SystemClock.elapsedRealtime()

        // An Apply that stopped before the install left this same pack: install it as it is.
        var drawn = false
        val apk = withContext(Dispatchers.IO) { built.find(recipe) } ?: run {
            drawn = true
            _state.value = ApplyState.Working("Drawing icons", 0, plan.drawables.size + 1)
            val unsigned = dir.resolve("unsigned.apk")
            val look = { name: String -> SlotLook(doc.effectiveGeometry(name), doc.effectiveTheme(name)) }
            PackBuilder(icons, container.renderer).build(template, plan, look, dir.resolve("icons"), unsigned, resolution) { done, total ->
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

        val firstInstall = !isPackInstalled()
        _state.value = ApplyState.Working("Installing")
        val result = PackInstallSession(app).install(apk, PACK_PACKAGE) { launches.trySend(it) }
        Log.i(TAG, "install: $result in ${SystemClock.elapsedRealtime() - t3} ms")
        if (result == InstallResult.Success) recordCoverage(doc, components)
        return when (result) {
            InstallResult.Success -> ApplyState.Installed(firstInstall, currentLauncher(), plan.items.size, plan.unmatched.size)
            InstallResult.NeedsPermission -> needsPermission()
            is InstallResult.SignatureMismatch -> mismatch()
            is InstallResult.Failed -> ApplyState.Problem(result.message)
        }
    }

    private fun needsPermission() = ApplyState.Problem(
        "To install the pack, allow $appName to install apps. Turn on the switch, come back, and tap Apply again.",
        Fix.GRANT_INSTALL_PERMISSION,
    )

    /** The pack is installed whatever happens here; a failed write only loses the next diff. */
    private suspend fun recordCoverage(doc: ThemeDocument, components: Set<String>) = withContext(Dispatchers.IO) {
        try {
            container.coverage.write(AppliedCoverage(doc.id, System.currentTimeMillis(), components))
        } catch (e: IOException) {
            Log.w(TAG, "Coverage of this Apply was not saved", e)
        }
    }

    /** M7 addendum, spec 6.5: the key files are there and do not open. Nothing was installed. */
    private fun damagedKey() = ApplyState.Problem(
        "The pack signing key on this phone is damaged, so $appName cannot sign the pack. Nothing was installed. " +
            "Restore the key from a theme backup that includes it. Or make a new key: the pack already installed " +
            "then has to be uninstalled once, and the launcher set to the new one.",
        Fix.DAMAGED_KEY,
    )

    /**
     * Sets the damaged key files aside and applies again. The new key's first Apply meets
     * the installed pack's old key and asks to uninstall it, the spec 6.5 path.
     */
    fun newKey() {
        if (_state.value is ApplyState.Working) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.packKey.setAsideDamaged() }
            apply()
        }
    }

    private fun mismatch() = ApplyState.Problem(
        "A pack signed with a different key is installed, probably from an earlier install of $appName. " +
            "Uninstall the old pack, then tap Apply again.",
        Fix.UNINSTALL_OLD_PACK,
    )

    /** The installed pack's signing certificate, or null when no pack is installed. */
    private fun installedPackSigner(): ByteArray? = try {
        getApplication<Application>().packageManager
            .getPackageInfo(PACK_PACKAGE, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            .signingInfo?.apkContentsSigners?.singleOrNull()?.toByteArray()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    fun fix(fix: Fix) {
        val pkg = getApplication<Application>().packageName
        launches.trySend(
            when (fix) {
                Fix.GRANT_INSTALL_PERMISSION -> Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$pkg"))
                Fix.UNINSTALL_OLD_PACK -> Intent(Intent.ACTION_DELETE, Uri.parse("package:$PACK_PACKAGE"))
                // The screen handles it: restore opens the import picker, the other button is newKey().
                Fix.DAMAGED_KEY -> return
            },
        )
    }

    fun openAppInfo(packageName: String) {
        launches.trySend(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    private fun isPackInstalled(): Boolean = try {
        getApplication<Application>().packageManager.getPackageInfo(PACK_PACKAGE, PackageManager.PackageInfoFlags.of(0))
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
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
        val PACK_PACKAGE: String = BuildConfig.PACK_PACKAGE
    }
}
