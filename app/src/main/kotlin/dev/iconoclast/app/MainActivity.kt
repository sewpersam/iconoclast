package dev.iconoclast.app

import android.content.Intent
import android.net.Uri
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
        if (savedInstanceState == null) receive(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }

    /** An image shared from another app, such as a browser's Share image. */
    private fun receive(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let(home::receiveImage)
    }
}
