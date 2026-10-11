package dev.iconoclast.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.iconoclast.app.coverage.CoverageScreen
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
    const val COVERAGE = "coverage"
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
                onCoverage = { nav.navigate(Routes.COVERAGE) { launchSingleTop = true } },
                onSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
            )
        }
        composable(Routes.COVERAGE) {
            Scaffold(topBar = { BackBar("Coverage", onBack = { nav.popBackStack() }) }) { padding ->
                CoverageScreen(viewModel(), onReapply = {
                    nav.navigate(Routes.APPLY) { popUpTo(Routes.HOME) }
                    apply.apply()
                }, Modifier.padding(padding))
            }
        }
        composable(Routes.SETTINGS) {
            val settings = (LocalContext.current.applicationContext as IconoclastApp).container.settings
            Scaffold(topBar = { BackBar("Settings and about", onBack = { nav.popBackStack() }) }) { padding ->
                AboutScreen(settings, Modifier.padding(padding))
            }
        }
        composable(Routes.EDITOR, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = requireNotNull(entry.arguments?.getString("id"))
            EditorScreen(viewModel(factory = EditorViewModel.factory(id)), onBack = { nav.popBackStack() })
        }
        composable(Routes.APPLY) {
            val state by apply.state.collectAsState()
            val active by apply.active.collectAsState()
            Scaffold(topBar = { BackBar("Apply", onBack = { nav.popBackStack() }) }) { padding ->
                ApplyScreen(
                    active, state, apply::apply, apply::fix, apply::openAppInfo,
                    onCoverage = { nav.navigate(Routes.COVERAGE) { launchSingleTop = true } },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

/** A top bar with a text Back button. The app ships no icon library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackBar(title: String, onBack: () -> Unit) {
    TopAppBar(title = { Text(title) }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } })
}
