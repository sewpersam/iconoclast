package dev.iconoclast.app.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The live preview, spec 2.2 screen 2: one big icon, [focused] or the first, and beside
 * it a three-row strip of every icon that scrolls sideways. Icons sit on a fixed dark
 * backdrop, like a wallpaper, so white line art and dark backgrounds both read. Strip
 * cells show their 128 px image at 48 dp; the big one shows its image from [heroImages], drawn larger.
 * Tapping any icon opens its override sheet, [onTap], and makes it the big one. An icon in
 * [overridden] wears a badge, and TalkBack says so. The strip's drawables on screen go to
 * [onVisible], for drags.
 */
@Composable
fun PreviewPanel(
    items: List<PreviewItem>,
    images: Map<Int, ImageBitmap>,
    focused: Int?,
    heroImages: Map<Int, ImageBitmap>,
    overridden: Set<Int>,
    onTap: (PreviewItem) -> Unit,
    onVisible: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyGridState()
    LaunchedEffect(state, items) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.map { it.key as Int }.toSet() }.collect(onVisible)
    }
    Row(modifier.fillMaxWidth().height(PANEL_HEIGHT).background(BACKDROP)) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text("No icons to show.", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
            return@Row
        }
        val hero = items.firstOrNull { it.drawable == focused } ?: items.first()
        Column(
            Modifier.width(HERO_WIDTH).fillMaxHeight().clickable(onClickLabel = "Override this icon") { onTap(hero) }
                .semantics(mergeDescendants = true) {
                    contentDescription = if (hero.drawable in overridden) "${hero.label}, large, overridden" else "${hero.label}, large"
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Cell(heroImages[hero.drawable] ?: images[hero.drawable], hero.drawable in overridden, HERO)
            Text(
                hero.label, Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp), color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
        LazyHorizontalGrid(
            rows = GridCells.Fixed(3),
            state = state,
            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            items(items, key = { it.drawable }) { item ->
                val badged = item.drawable in overridden
                Box(
                    Modifier.padding(2.dp)
                        .clickable(onClickLabel = "Override this icon") { onTap(item) }
                        .semantics(mergeDescendants = true) { contentDescription = if (badged) "${item.label}, overridden" else item.label },
                ) { Cell(images[item.drawable], badged, CELL) }
            }
        }
    }
}

@Composable
private fun Cell(image: ImageBitmap?, badged: Boolean, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(image, contentDescription = null, Modifier.size(size))
        } else {
            Box(Modifier.size(size * 0.84f).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(size / 4)))
        }
        if (badged) {
            Box(
                Modifier.align(Alignment.TopEnd).size(size / 4)
                    .border(2.dp, BACKDROP, CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary, CircleShape),
            )
        }
    }
}

private val BACKDROP = Color(0xFF3C4043)
private val CELL = 48.dp
private val HERO = 104.dp
private val HERO_WIDTH = 132.dp
/** Three strip rows of 48 dp cells with their padding. */
private val PANEL_HEIGHT = 168.dp
