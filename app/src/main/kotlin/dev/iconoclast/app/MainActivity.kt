package dev.iconoclast.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.merge

class MainActivity : ComponentActivity() {
    private val home: HomeViewModel by viewModels()
    private val apply: ApplyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface {
                    LaunchedEffect(Unit) { merge(home.intents, apply.intents).collect { startActivity(it) } }
                    IconoclastNavHost(home, apply)
                }
            }
        }
    }
}
