package dev.iconoclast.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * An outlined button in the Geist palette: Material 3's outlined label is the muted
 * onSurfaceVariant grey, which reads as disabled here. Enabled, the label and the outline
 * are the text colour; disabled, both are the muted greys.
 */
@Composable
fun OutlinedAction(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.onSurface, disabledContentColor = c.onSurfaceVariant),
        border = BorderStroke(1.dp, if (enabled) c.onSurface else c.outlineVariant),
        content = content,
    )
}
