package dev.iconoclast.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.iconoclast.app.theme.ThemeDocument

/** Placeholder for the editor route. Task 6 replaces it with the real editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(doc: ThemeDocument?, onBack: () -> Unit) {
    Scaffold(topBar = { BackBar(doc?.name ?: "Theme", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(doc?.name ?: "This theme no longer exists.", style = MaterialTheme.typography.headlineSmall)
            if (doc != null) Text("The editor comes next.")
        }
    }
}
