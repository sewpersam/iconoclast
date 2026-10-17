package dev.iconoclast.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.iconoclast.app.editor.EditorIcons
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.iconoclast.app.editor.EditorScreen
import dev.iconoclast.app.settings.AboutScreen
import dev.iconoclast.app.editor.EditorViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

/** The app's routes. One activity, these destinations. */
object Routes {
    const val HOME = "home"
    const val APPLY = "apply"
    const val SETTINGS = "settings"
    const val EDITOR = "editor/{id}"
    fun editor(id: String) = "editor/$id"
}

@Composable
fun IconoclastNavHost(home: HomeViewModel, apply: ApplyViewModel) {
    val nav = rememberNavController()
    // A new theme opens in the editor. Collected here, by the current NavController.
    LaunchedEffect(nav) { home.created.collect { nav.navigate(Routes.editor(it)) { launchSingleTop = true } } }
    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                home,
                onOpen = { nav.navigate(Routes.editor(it)) { launchSingleTop = true } },
                onApply = { nav.navigate(Routes.APPLY) { launchSingleTop = true } },
                onSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
            )
        }
        composable(Routes.SETTINGS) {
            val settings = (LocalContext.current.applicationContext as IconoclastApp).container.settings
            Scaffold(topBar = { BackBar("Settings", onBack = { nav.popBackStack() }) }) { padding ->
                AboutScreen(settings, Modifier.padding(padding))
            }
        }
        composable(Routes.EDITOR, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = requireNotNull(entry.arguments?.getString("id"))
            // Apply from the editor: the editor saves this theme and makes it active, then the Apply screen.
            EditorScreen(viewModel(factory = EditorViewModel.factory(id)), onBack = { nav.popBackStack() }, onApply = {
                nav.navigate(Routes.APPLY) { launchSingleTop = true }
            })
        }
        composable(Routes.APPLY) {
            val state by apply.state.collectAsState()
            val active by apply.active.collectAsState()
            val status by apply.status.collectAsState()
            val copied by apply.copied.collectAsState()
            LaunchedEffect(active) { apply.refreshStatus() }
            // Restoring a damaged key is a theme import: home runs it and asks about the key.
            val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    home.import(uri)
                    nav.popBackStack(Routes.HOME, inclusive = false)
                }
            }
            val neoBackup by apply.neoBackup.collectAsState()
            val pickNeoBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let(apply::checkNeoBackup)
            }
            val saveNeoBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
                uri?.let(apply::saveNeoBackupWithoutCustomIcons)
            }
            Scaffold(topBar = { BackBar("Apply", onBack = { nav.popBackStack() }) }) { padding ->
                ApplyScreen(
                    active, state, status, apply::apply, apply::resumed,
                    onRestoreKey = { restore.launch(ThemeTransfer.IMPORT_TYPES) },
                    onNewKey = apply::newKey,
                    onRemoveLeftovers = apply::removeLeftovers,
                    copied = copied,
                    onCopyDiagnostics = apply::copyDiagnostics,
                    neoBackup = neoBackup,
                    onCheckNeoBackup = { pickNeoBackup.launch(arrayOf("*/*")) },
                    onSaveNeoBackup = { saveNeoBackup.launch("neo-no-custom-icons.zbk") },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

/** A top bar with a back arrow, drawn by [EditorIcons]: the app ships no icon library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
                Icon(EditorIcons.Back, contentDescription = null)
            }
        },
    )
}
