package dev.iconoclast.app.settings

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.iconoclast.app.BuildConfig
import dev.iconoclast.app.R
import dev.iconoclast.render.OutputResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ARCTICONS_URL = "https://github.com/Arcticons-Team/Arcticons"
private const val CC_BY_SA_URL = "https://creativecommons.org/licenses/by-sa/4.0/"
private const val GPL_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
private const val LICENSE_ASSET = "licenses/dependencies.txt"

/**
 * Pack size per resolution: PhoneBudgetTest on icondesign36, the Jade preset, 300 icons,
 * M7 Task 11 (docs/verification/m7.md). Grain-heavy looks are the largest.
 */
private val RESOLUTIONS = listOf(
    Triple(OutputResolution.FULL, "Full, 432 px", "Sharpest on every screen. A Jade pack of 300 icons measured 12.5 MB."),
    Triple(OutputResolution.COMPACT, "Compact, 324 px", "For phones short on storage. The same pack measured 8.4 MB."),
)

/** One line of the dependency list: `group:name:version<TAB>licence`. */
private class Dependency(val artifact: String, val version: String, val license: String)

/**
 * Settings and About, spec 2.2 screen 6: output resolution, the Arcticons credit and
 * licence, this app's licence, where the signing key export lives, and the open source
 * licences of every runtime dependency, read from the generated asset.
 */
@Composable
fun AboutScreen(settings: AppSettings, modifier: Modifier = Modifier) {
    val resolution by settings.resolution.collectAsState()
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val open = { url: String -> try { uri.openUri(url) } catch (e: ActivityNotFoundException) { } catch (e: IllegalArgumentException) { } }
    val deps by produceState(emptyList<Dependency>()) {
        value = withContext(Dispatchers.IO) {
            context.assets.open(LICENSE_ASSET).bufferedReader().readLines().filter { it.isNotBlank() }.map { line ->
                val (coords, license) = line.split('\t', limit = 2)
                Dependency(coords.substringBeforeLast(':'), coords.substringAfterLast(':'), license)
            }
        }
    }
    val app = stringResource(R.string.app_name)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Header("Output icon resolution")
            Column(Modifier.selectableGroup()) {
                for ((r, name, detail) in RESOLUTIONS) {
                    Row(
                        Modifier.fillMaxWidth().selectable(resolution == r, role = Role.RadioButton) { settings.setResolution(r) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = resolution == r, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                            Text(detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Text("Takes effect at the next Apply.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Header("Arcticons")
            Text(
                "The icons are Arcticons by the Arcticons Team and contributors, licensed CC BY-SA 4.0. " +
                    "$app restyles and rasterises them on this phone, so every generated pack carries derived icons " +
                    "under the same licence and this credit. The app-to-icon map is part of the Arcticons app, GPL-3.0.",
            )
            Row {
                TextButton(onClick = { open(ARCTICONS_URL) }) { Text("Arcticons") }
                TextButton(onClick = { open(CC_BY_SA_URL) }) { Text("CC BY-SA 4.0") }
            }
        }
        item {
            Header("$app")
            Text("Version ${BuildConfig.VERSION_NAME}. Free software under the GNU General Public License, version 3 or later.")
            Row {
                TextButton(onClick = { open(GPL_URL) }) { Text("GPL-3.0-or-later") }
                if (BuildConfig.SOURCE_URL.isNotEmpty()) TextButton(onClick = { open(BuildConfig.SOURCE_URL) }) { Text("Source code") }
            }
            if (BuildConfig.SOURCE_URL.isEmpty()) Text("The source code link is added at the first public release.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Header("Export signing key")
            Text(
                "Packs are signed with a key made on this phone. A reinstalled app has a new key and cannot update the pack " +
                    "you have, so it must uninstall it first. To keep the key, export a theme from the home screen and tick " +
                    "\"Include the pack signing key\". A warning comes first: anyone with that file can replace your pack. " +
                    "Importing the file on a fresh install offers to restore the key.",
            )
        }
        item { Header("Open source licences (${deps.size})") }
        items(deps, key = { it.artifact }) { d ->
            Column {
                Text(d.artifact, style = MaterialTheme.typography.bodyMedium)
                Text("${d.version}, ${d.license}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, Modifier.padding(top = 12.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
}
