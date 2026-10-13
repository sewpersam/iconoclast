package dev.iconoclast.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import dev.iconoclast.app.ui.theme.IconoclastTheme
import kotlinx.coroutines.flow.merge

class MainActivity : ComponentActivity() {
    private val home: HomeViewModel by viewModels()
    private val apply: ApplyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            IconoclastTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    LaunchedEffect(Unit) { merge(home.intents, apply.intents).collect { startActivity(it) } }
                    IconoclastNavHost(home, apply)
                }
            }
        }
    }
}
