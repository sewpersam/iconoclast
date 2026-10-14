package dev.iconoclast.app.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Long-press help, docs/naming-spec.md 3: holding [content], a control's label row, opens
 * a bubble with the [tip]'s name and help. It stays until a tap elsewhere. Material's
 * TooltipBox brings the long press, the mouse hover and TalkBack's long-press action, so
 * the anchor needs no gesture code of its own. Never put the anchor on a slider track or a
 * switch: they take the press themselves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpAnchor(tip: Tip, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (tip.help.isEmpty()) return Box(modifier) { content() }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { HelpBubble(tip.label, tip.help) },
        state = rememberTooltipState(isPersistent = true),
        modifier = modifier,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun androidx.compose.material3.TooltipScope.HelpBubble(title: String, text: String) {
    val c = MaterialTheme.colorScheme
    RichTooltip(
        title = { Text(title, style = MaterialTheme.typography.titleSmall) },
        colors = TooltipDefaults.richTooltipColors(
            containerColor = c.inverseSurface,
            contentColor = c.inverseOnSurface,
            titleContentColor = c.inverseOnSurface,
            actionContentColor = c.inverseOnSurface,
        ),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) { Text(text, style = MaterialTheme.typography.bodyMedium) }
}

/**
 * A page header's ⓘ: a tap shows the page's help in the same bubble a long press gives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoButton(tip: Tip) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { HelpBubble(tip.label, tip.help) },
        state = state,
        enableUserInput = false,
    ) {
        IconButton(onClick = { scope.launch { state.show() } }, modifier = Modifier.semantics { contentDescription = "About ${tip.label}" }) {
            Icon(EditorIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * An icon button with no visible text gets its one or two word [name] in a plain bubble
 * on long press, as the spec asks for undo, redo, the eye, delete and reorder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NamedIcon(name: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip(containerColor = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
                Text(name)
            }
        },
        state = rememberTooltipState(),
        content = content,
    )
}
