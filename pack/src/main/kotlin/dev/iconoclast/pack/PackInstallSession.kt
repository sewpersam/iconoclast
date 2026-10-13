package dev.iconoclast.pack

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File

/** How an install ended. Every [PackageInstaller] status maps to one of these, spec 3.6. */
sealed interface InstallResult {
    data object Success : InstallResult

    /** "Install unknown apps" is off for the designer. */
    data object NeedsPermission : InstallResult

    /** A pack signed with another key is installed. It must be uninstalled first, spec 6.5. */
    data class SignatureMismatch(val detail: String?) : InstallResult

    data class Failed(val status: Int, val message: String) : InstallResult
}

/**
 * Installs a pack through a [PackageInstaller] session. On
 * [PackageInstaller.STATUS_PENDING_USER_ACTION] it calls [confirm] with the system's
 * confirmation intent and keeps waiting for the final status.
 */
class PackInstallSession(private val context: Context) {

    suspend fun install(apk: File, packageName: String, confirm: (Intent) -> Unit): InstallResult {
        if (!context.packageManager.canRequestPackageInstalls()) return InstallResult.NeedsPermission
        val installer = context.packageManager.packageInstaller
        val action = "${context.packageName}.PACK_INSTALL_STATUS"
        val events = Channel<Intent>(Channel.UNLIMITED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { events.trySend(intent) }
        }
        context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        try {
            val sessionId = withContext(Dispatchers.IO) {
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(packageName)
                    setSize(apk.length())
                    // UPDATE_PACKAGES_WITHOUT_USER_ACTION alone did not skip the update prompt on the
                    // API 36 emulator. From API 34 update ownership is enforced, so the designer
                    // must also own the pack's updates; see docs/verification/m3.md.
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) requestUpdateOwnership()
                }
                val id = installer.createSession(params)
                try {
                    installer.openSession(id).use { session ->
                        session.openWrite("base.apk", 0, apk.length()).use { out ->
                            apk.inputStream().use { it.copyTo(out) }
                            session.fsync(out)
                        }
                        val callback = Intent(action).setPackage(context.packageName)
                        val pending = PendingIntent.getBroadcast(context, id, callback, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                        session.commit(pending.intentSender)
                    }
                } catch (e: Exception) {
                    installer.abandonSession(id)
                    throw e
                }
                id
            }
            while (true) {
                val intent = events.receive()
                if (intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId) != sessionId) continue
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    val confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    if (confirmation == null) return InstallResult.Failed(status, "The system did not ask for confirmation.")
                    confirm(confirmation)
                    continue
                }
                return result(status, message)
            }
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    /** The app's manifest declares ENFORCE_UPDATE_OWNERSHIP; this library's has none. */
    @SuppressLint("MissingPermission")
    @TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun PackageInstaller.SessionParams.requestUpdateOwnership() = setRequestUpdateOwnership(true)

    private fun result(status: Int, message: String?): InstallResult = when (status) {
        PackageInstaller.STATUS_SUCCESS -> InstallResult.Success
        PackageInstaller.STATUS_FAILURE_CONFLICT ->
            if (message != null && ("UPDATE_INCOMPATIBLE" in message || "signature" in message.lowercase())) {
                InstallResult.SignatureMismatch(message)
            } else {
                InstallResult.Failed(status, "Another installed app conflicts with the pack." + detail(message))
            }
        PackageInstaller.STATUS_FAILURE_ABORTED -> InstallResult.Failed(status, "The install was cancelled.")
        PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallResult.Failed(status, "The system blocked the install." + detail(message))
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> InstallResult.Failed(status, "This device cannot run the pack." + detail(message))
        PackageInstaller.STATUS_FAILURE_INVALID -> InstallResult.Failed(status, "The generated pack was invalid." + detail(message))
        PackageInstaller.STATUS_FAILURE_STORAGE -> InstallResult.Failed(status, "Not enough storage to install the pack.")
        PackageInstaller.STATUS_FAILURE_TIMEOUT -> InstallResult.Failed(status, "The install timed out. Try again.")
        else -> InstallResult.Failed(status, "The install failed." + detail(message))
    }

    private fun detail(message: String?) = if (message.isNullOrBlank()) "" else " ($message)"
}
