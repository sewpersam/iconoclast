package dev.iconoclast.app.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The live preview, spec 2.2 screen 2. Icons sit on a fixed dark backdrop, like a
 * wallpaper, so white line art and dark backgrounds both read. Each cell shows its 128 px
 * image at 48 dp, close to one to one on a 420 dpi screen. Tapping an icon opens its
 * override sheet, [onTap]; an icon in [overridden] wears a badge, and TalkBack says so.
 * The drawables on screen go to [onVisible], for drags.
 */
@Composable
fun PreviewGrid(
    items: List<PreviewItem>,
    images: Map<Int, ImageBitmap>,
    overridden: Set<Int>,
    onTap: (PreviewItem) -> Unit,
    onVisible: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyGridState()
    LaunchedEffect(state, items) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.map { it.key as Int }.toSet() }.collect(onVisible)
    }
    Box(modifier.background(BACKDROP)) {
        if (items.isEmpty()) {
            Text("No icons to show.", Modifier.align(Alignment.Center), color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(56.dp),
            state = state,
            contentPadding = PaddingValues(8.dp),
        ) {
            items(items, key = { it.drawable }) { item ->
                val badged = item.drawable in overridden
                val label = if (badged) "${item.label}, overridden" else item.label
                Box(
                    Modifier.padding(4.dp).size(48.dp)
                        .clickable(onClickLabel = "Override this icon") { onTap(item) }
                        .semantics(mergeDescendants = true) { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    val image = images[item.drawable]
                    if (image != null) {
                        Image(image, contentDescription = null, Modifier.size(48.dp))
                    } else {
                        Box(Modifier.size(40.dp).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)))
                    }
                    if (badged) {
                        Box(
                            Modifier.align(Alignment.TopEnd).size(12.dp)
                                .border(2.dp, BACKDROP, CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

private val BACKDROP = Color(0xFF3C4043)
