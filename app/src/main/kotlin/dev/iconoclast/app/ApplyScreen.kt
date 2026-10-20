package dev.iconoclast.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import dev.iconoclast.app.ui.theme.OutlinedAction
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    onRemoveLeftovers: () -> Unit,
    copied: String?,
    onCopyDiagnostics: () -> Unit,
    neo: NeoIconsState? = null,
    onChooseNeo: () -> Unit = {},
    onSaveNeo: (Set<String>) -> Unit = {},
    onCancelNeo: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onResume() }
    val packLabel = "${stringResource(R.string.app_name)} Pack"
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
                Text("Pick $packLabel in ${g.name}. ${g.pick}")
                if (state.leftover > 0) {
                    Text("An older copy of the pack is still installed and also shows as $packLabel. Remove it first, so you pick the right one.")
                    OutlinedAction(onClick = onRemoveLeftovers) { Text("Remove the old copy") }
                }
            }
            is ApplyState.Problem -> {
                Text(state.message)
                if (state.fix == Fix.DAMAGED_KEY) {
                    Button(onClick = onRestoreKey) { Text("Restore from a backup") }
                    OutlinedAction(onClick = onNewKey) { Text("Start over with a new key") }
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
        // For bug reports: icons that do not change in a launcher.
        TextButton(onClick = onCopyDiagnostics, enabled = !working) { Text("Copy diagnostics") }
        if (copied != null) Text(copied, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (neo != null) NeoSection(neo, packLabel, working, onChooseNeo, onSaveNeo, onCancelNeo)
    }
}

/**
 * Neo Launcher custom icons: Neo draws an icon set by hand (long-press, Customize) over any
 * pack, and 0.9.3 cannot remove one. The user names those apps once; each Apply points them
 * at the new pack.
 */
@Composable
private fun NeoSection(
    neo: NeoIconsState,
    packLabel: String,
    working: Boolean,
    onChoose: () -> Unit,
    onSave: (Set<String>) -> Unit,
    onCancel: () -> Unit,
) {
    Text(
        "Neo keeps an icon you gave an app by hand (long-press, Customize) whatever pack you pick, and cannot remove it. " +
            "Name those apps here once: they get the $packLabel icon now and after every Apply.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedAction(onClick = onChoose, enabled = !working) {
        Text(if (neo.apps.isEmpty()) "Neo custom icons" else "Neo custom icons: ${neo.apps.size} ${if (neo.apps.size == 1) "app" else "apps"}")
    }
    neo.note?.let { Text(it) }
    neo.choices?.let { NeoChooser(it, neo.apps, packLabel, onSave, onCancel) }
}

@Composable
private fun NeoChooser(choices: List<NeoChoice>, kept: Set<String>, packLabel: String, onSave: (Set<String>) -> Unit, onCancel: () -> Unit) {
    var picked by remember { mutableStateOf(kept.filterTo(HashSet()) { k -> choices.any { it.component == k } }) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Apps Neo keeps on a custom icon") },
        text = {
            LazyColumn {
                items(choices, key = { it.component }) { c ->
                    Row(
                        Modifier.fillMaxWidth().toggleable(c.component in picked, role = Role.Checkbox) { on ->
                            picked = HashSet(picked).apply { if (on) add(c.component) else remove(c.component) }
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = c.component in picked, onCheckedChange = null)
                        Image(c.icon, contentDescription = null, modifier = Modifier.padding(start = 12.dp).size(40.dp))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(c.label)
                            // Two apps can share a name, such as two Cameras.
                            if (choices.count { it.label == c.label } > 1) {
                                Text(c.component.substringBefore('/'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(picked) }) { Text("Give them the $packLabel icon") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
