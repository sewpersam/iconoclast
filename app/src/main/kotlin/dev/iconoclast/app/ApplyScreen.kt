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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.iconoclast.app.theme.ThemeDocument

/** Apply the active theme: progress, status and the post-install guidance. */
@Composable
fun ApplyScreen(
    active: ThemeDocument?,
    state: ApplyState,
    onApply: () -> Unit,
    onFix: (Fix) -> Unit,
    onRestoreKey: () -> Unit,
    onNewKey: () -> Unit,
    onAppInfo: (String) -> Unit,
    onCoverage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val working = state is ApplyState.Working
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(onClick = onApply, enabled = !working && active != null, modifier = Modifier.fillMaxWidth()) {
            Text(if (active != null) "Apply ${active.name}" else "No theme to apply")
        }
        when (state) {
            ApplyState.Idle -> Text("Apply builds an icon pack from the theme in use and installs it.")
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
                Text("Pack installed. ${state.themed} apps themed, ${state.unthemed} not covered by Arcticons keep their own icon.")
                OutlinedButton(onClick = onCoverage) { Text("See which apps changed") }
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
                when (state.fix) {
                    Fix.GRANT_INSTALL_PERMISSION -> OutlinedButton(onClick = { onFix(Fix.GRANT_INSTALL_PERMISSION) }) { Text("Allow installing") }
                    Fix.UNINSTALL_OLD_PACK -> OutlinedButton(onClick = { onFix(Fix.UNINSTALL_OLD_PACK) }) { Text("Uninstall old pack") }
                    Fix.DAMAGED_KEY -> {
                        Button(onClick = onRestoreKey) { Text("Restore from a backup") }
                        OutlinedButton(onClick = onNewKey) { Text("Start over with a new key") }
                    }
                    null -> Unit
                }
            }
        }
    }
}
