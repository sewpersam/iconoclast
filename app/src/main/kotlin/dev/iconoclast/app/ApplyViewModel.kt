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
import dev.iconoclast.app.texture.TexturePresence
import dev.iconoclast.app.theme.ThemeDocument
import dev.iconoclast.pack.CoverageMatcher
import dev.iconoclast.pack.InstallResult
import dev.iconoclast.pack.LauncherGuide
import dev.iconoclast.pack.LauncherTable
import dev.iconoclast.pack.PackInstallSession
import dev.iconoclast.pack.PackKeyStore
import dev.iconoclast.pack.PackSigner
import dev.iconoclast.pack.TooManyIconsException
import dev.iconoclast.render.Theme
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

sealed interface ApplyState {
    data object Idle : ApplyState
    data class Working(val stage: String, val done: Int = 0, val total: Int = 0) : ApplyState
    data class Installed(val firstInstall: Boolean, val guide: LauncherGuide, val themed: Int, val unthemed: Int) : ApplyState
    data class Problem(val message: String, val fix: Fix? = null) : ApplyState
}

enum class Fix { GRANT_INSTALL_PERMISSION, UNINSTALL_OLD_PACK }

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
        val theme = active.value?.theme ?: return
        if (_state.value is ApplyState.Working) return
        _state.value = ApplyState.Working("Reading icons")
        viewModelScope.launch {
            _state.value = try {
                run(theme)
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

    private suspend fun run(theme: Theme): ApplyState {
        val app = getApplication<Application>()
        val t0 = SystemClock.elapsedRealtime()
        val (icons, template) = withContext(Dispatchers.IO) {
            container.iconSource() to app.assets.open(PackBuilder.TEMPLATE_ASSET).use { it.readBytes() }
        }
        // The renderer throws on a texture it does not hold: check before drawing anything.
        val missing = withContext(Dispatchers.IO) { TexturePresence.ensure(container.textures, theme) }
        if (missing.isNotEmpty()) return ApplyState.Problem(
            "Nothing was installed: this theme uses a texture that is not on this phone. " +
                "Import the theme's .zip again, or remove the texture layer in the editor.",
        )
        val components = withContext(Dispatchers.IO) { InstalledApps.launchableComponents(app) }
        val plan = CoverageMatcher.match(components, PackBuilder.slotCount(template), icons::resolve)
        val t1 = SystemClock.elapsedRealtime()

        _state.value = ApplyState.Working("Drawing icons", 0, plan.drawables.size + 1)
        val unsigned = PackBuilder(icons, container.renderer).build(template, plan, theme) { done, total ->
            _state.value = ApplyState.Working("Drawing icons", done, total)
        }
        val t2 = SystemClock.elapsedRealtime()

        _state.value = ApplyState.Working("Signing the pack")
        val key = withContext(Dispatchers.IO) { PackKeyStore(app.noBackupFilesDir, "$appName Pack").loadOrCreate() }
        // Spec 6.5: a pack from an earlier install of the designer has another key. Say so
        // before the system offers an update that can only fail.
        if (installedPackSigner()?.let { !it.contentEquals(key.certificate.encoded) } == true) return mismatch()
        val apk = withContext(Dispatchers.IO) {
            File(app.cacheDir, "pack").apply { mkdirs() }.resolve("pack.apk").also { PackSigner.sign(unsigned, key, it) }
        }
        val t3 = SystemClock.elapsedRealtime()
        Log.i(TAG, "apply: ${components.size} activities, ${plan.items.size} themed, ${plan.drawables.size} slots; " +
            "load ${t1 - t0} ms, render+rewrite ${t2 - t1} ms, sign ${t3 - t2} ms, ${apk.length()} bytes")

        val firstInstall = !isPackInstalled()
        _state.value = ApplyState.Working("Installing")
        val result = PackInstallSession(app).install(apk, PACK_PACKAGE) { launches.trySend(it) }
        Log.i(TAG, "install: $result in ${SystemClock.elapsedRealtime() - t3} ms")
        return when (result) {
            InstallResult.Success -> ApplyState.Installed(firstInstall, currentLauncher(), plan.items.size, plan.unmatched.size)
            InstallResult.NeedsPermission -> ApplyState.Problem(
                "To install the pack, allow $appName to install apps. Turn on the switch, come back, and tap Apply again.",
                Fix.GRANT_INSTALL_PERMISSION,
            )
            is InstallResult.SignatureMismatch -> mismatch()
            is InstallResult.Failed -> ApplyState.Problem(result.message)
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
