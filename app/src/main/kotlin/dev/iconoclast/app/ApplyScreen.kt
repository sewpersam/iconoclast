package dev.iconoclast.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.iconoclast.app.theme.ThemeDocument

/**
 * Apply the active theme: progress, status and the post-install guidance. The button says
 * the theme is applied while the installed pack is this theme as it is, and offers Apply
 * again after an edit or when newly installed apps have icons.
 */
@Composable
fun ApplyScreen(
    active: ThemeDocument?,
    state: ApplyState,
    status: AppliedStatus,
    onApply: () -> Unit,
    onResume: () -> Unit,
    onRestoreKey: () -> Unit,
    onNewKey: () -> Unit,
    onAppInfo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onResume() }
    val working = state is ApplyState.Working
    val applied = status.applied && status.newApps == 0
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(onClick = onApply, enabled = !working && !applied && active != null, modifier = Modifier.fillMaxWidth()) {
            Text(
                when {
                    active == null -> "No theme to apply"
                    applied -> "${active.name} is applied"
                    status.applied -> "Apply ${active.name} to ${status.newApps} new ${if (status.newApps == 1) "app" else "apps"}"
                    else -> "Apply ${active.name}"
                },
            )
        }
        when (state) {
            ApplyState.Idle -> if (!applied) Text("Apply builds an icon pack from the theme in use and installs it.")
            is ApplyState.Working -> {
                if (state.total > 0) {
                    LinearProgressIndicator(progress = { state.done.toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
                    Text("${state.stage}: ${state.done} of ${state.total}")
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(state.stage)
                }
            }
            is ApplyState.Installed -> {
                val g = state.guide
                Text("Pack installed. ${state.themed} ${if (state.themed == 1) "app" else "apps"} themed.")
                if (state.firstInstall) {
                    Text("Select the pack in ${g.name}. ${g.selectPack}")
                } else {
                    Text("${g.name} keeps showing the old icons until you do this. ${g.refresh}")
                    if (g.forceStopFromAppInfo && g.packageName.isNotEmpty()) {
                        OutlinedButton(onClick = { onAppInfo(g.packageName) }) { Text("Open ${g.name} settings") }
                    }
                }
            }
            is ApplyState.Problem -> {
                Text(state.message)
                if (state.fix == Fix.DAMAGED_KEY) {
                    Button(onClick = onRestoreKey) { Text("Restore from a backup") }
                    OutlinedButton(onClick = onNewKey) { Text("Start over with a new key") }
                }
            }
        }
        if (status.noIcon > 0 && !working) {
            Text(
                if (status.noIcon == 1) "1 app has no Arcticons icon yet and keeps its own." else "${status.noIcon} apps have no Arcticons icon yet and keep their own.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
