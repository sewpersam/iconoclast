package dev.iconoclast.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private val model: ApplyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val state by model.state.collectAsState()
                    val active by model.active.collectAsState()
                    LaunchedEffect(Unit) { model.intents.collect { startActivity(it) } }
                    ApplyScreen(active, state, model::apply, model::fix, model::openAppInfo, Modifier.safeDrawingPadding())
                }
            }
        }
    }
}
