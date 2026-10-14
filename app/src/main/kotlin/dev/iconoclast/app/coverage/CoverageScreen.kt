package dev.iconoclast.app.coverage

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Arcticons' icon request process: the issue chooser, whose first option is the request form. */
const val ARCTICONS_REQUEST_URL = "https://github.com/Arcticons-Team/Arcticons/issues/new/choose"

/**
 * The coverage screen, spec 2.2 screen 5: apps installed since the last Apply with a
 * re-apply call to action, then every installed launchable app as themed, overridden or
 * not in Arcticons, the last with a link to request the icon.
 */
@Composable
fun CoverageScreen(vm: CoverageViewModel, onReapply: () -> Unit, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    if (ui.loading) {
        Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
        return
    }
    val uri = LocalUriHandler.current
    val request = { try { uri.openUri(ARCTICONS_REQUEST_URL) } catch (e: ActivityNotFoundException) { } catch (e: IllegalArgumentException) { } }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ui.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (ui.newSinceApply.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Header("New since last Apply (${ui.newSinceApply.size})")
                        for (r in ui.newSinceApply) Text("${r.label}: ${if (r.drawable != null) "Arcticons has it" else "not in Arcticons"}")
                        Text(
                            if (ui.reapplyWouldTheme > 0) "Apply again to theme ${ui.reapplyWouldTheme} of them."
                            else "None of them is in Arcticons, so Apply would not change their icons.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = onReapply, modifier = Modifier.fillMaxWidth()) { Text("Apply again") }
                    }
                }
            }
        } else if (ui.neverApplied) {
            item { Text("No Apply yet. After one, apps installed later show here.", Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.bodySmall) }
        }
        section("Themed", ui.themed) { r -> AppRow(r) }
        section("Changed one by one", ui.overridden) { r -> AppRow(r, "Changed for this icon in this theme") }
        section("No Arcticons icon yet", ui.notInArcticons, "These keep their own icon. Arcticons takes requests for new icons.") { r ->
            AppRow(r, action = { TextButton(onClick = request) { Text("Request icon") } })
        }
        if (ui.staleOverrides.isNotEmpty()) {
            section("Saved changes for missing icons", ui.staleOverrides.map { CoverageRow(it.replace('_', ' '), it, null) },
                "This Arcticons version lacks these icons or the drawings they use. The changes stay in the theme.") { r ->
                Text(r.label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun LazyListScope.section(title: String, rows: List<CoverageRow>, note: String? = null, row: @Composable (CoverageRow) -> Unit) {
    item(key = "h-$title") {
        Column(Modifier.padding(top = 16.dp, bottom = 4.dp)) {
            Header("$title (${rows.size})")
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (rows.isEmpty()) item(key = "e-$title") { Text("None", style = MaterialTheme.typography.bodySmall) }
    items(rows, key = { "$title/${it.component}" }) { row(it) }
}

@Composable
private fun Header(text: String) {
    Text(text, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun AppRow(r: CoverageRow, detail: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(r.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(r.drawable?.replace('_', ' '), detail).joinToString(" · ").ifEmpty { r.component.substringBefore('/') },
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        action?.invoke()
    }
}
