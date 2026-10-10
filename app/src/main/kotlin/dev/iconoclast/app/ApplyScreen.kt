package dev.iconoclast.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.iconoclast.render.ShapeParams
import dev.iconoclast.render.Theme

/** The M3 screen: two Apply buttons, progress, status and the post-install guidance. */
@Composable
fun ApplyScreen(state: ApplyState, onApply: (Theme) -> Unit, onFix: (Fix) -> Unit, onAppInfo: (String) -> Unit) {
    val working = state is ApplyState.Working
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(onClick = { onApply(PLAIN) }, enabled = !working, modifier = Modifier.fillMaxWidth()) {
            Text("Apply plain theme")
        }
        Button(onClick = { onApply(THICK) }, enabled = !working, modifier = Modifier.fillMaxWidth()) {
            Text("Apply thick theme")
        }
        when (state) {
            ApplyState.Idle -> Text("Apply builds an icon pack from your installed apps and installs it.")
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
                Text("Pack installed. ${state.themed} apps themed, ${state.unthemed} keep their own icon.")
                if (state.firstInstall) {
                    Text("Select the pack in ${g.name}. ${g.selectPack}")
                } else {
                    Text("${g.name} keeps showing the old icons until you do this. ${g.refresh}")
                    if (g.forceStopFromAppInfo && g.packageName.isNotEmpty()) {
                        OutlinedButton(onClick = { onAppInfo(g.packageName) }) { Text("Open ${g.name} App info") }
                    }
                }
            }
            is ApplyState.Problem -> {
                Text(state.message)
                when (state.fix) {
                    Fix.GRANT_INSTALL_PERMISSION -> OutlinedButton(onClick = { onFix(Fix.GRANT_INSTALL_PERMISSION) }) { Text("Open settings") }
                    Fix.UNINSTALL_OLD_PACK -> OutlinedButton(onClick = { onFix(Fix.UNINSTALL_OLD_PACK) }) { Text("Uninstall old pack") }
                    null -> Unit
                }
            }
        }
    }
}

val PLAIN = Theme()
val THICK = Theme(shape = ShapeParams(strokeWeight = 2f))
