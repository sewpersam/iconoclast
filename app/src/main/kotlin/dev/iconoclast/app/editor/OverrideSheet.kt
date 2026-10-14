package dev.iconoclast.app.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.iconoclast.app.ui.theme.PreviewBackdrop
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import dev.iconoclast.app.theme.IconOverride
import dev.iconoclast.render.Effect
import dev.iconoclast.render.EffectLayer
import dev.iconoclast.render.FillMode
import dev.iconoclast.render.TextureOffset

/** Offsets are in [0, 1); the slider stops just short of 1. */
private const val OFFSET_MAX = 0.999f

/**
 * The per-icon override sheet, spec 2.2 screen 3: stroke weight, fill mode, gap closing,
 * texture offset, hide streaks and swap, for this icon in this theme only. Each control
 * starts at the theme's value and its reset returns to it. Every change is written
 * through the repository; sliders on release. [image] is the icon as the preview draws it,
 * 128 px, so a picked swap shows here at once.
 *
 * Texture offset is two sliders, X and Y, the stored numbers themselves, so TalkBack can
 * set them and a chosen patch can be fine-tuned. Their reset returns to the icon's own
 * seeded offset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverrideSheet(vm: EditorViewModel, ui: EditorUi, item: PreviewItem, image: ImageBitmap?) {
    val theme = ui.theme
    val o = ui.override
    fun commit(next: IconOverride) = vm.commitOverride(next)
    val done = { vm.commitOverride(vm.ui.value.override) }
    ModalBottomSheet(
        onDismissRequest = vm::closeOverride,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HelpAnchor(Tips.CHANGE_ICON) {
                Text("This icon only", Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(128.dp).background(PreviewBackdrop, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    if (image != null) Image(image, contentDescription = "${item.label} with these changes", Modifier.size(112.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(item.label, style = MaterialTheme.typography.titleMedium)
                    Text(vm.drawableName(item.drawable).replace('_', ' '), style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (o.isEmpty) "Same as the rest" else "Changed for this icon",
                        Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Gap()

            SliderControl(
                Tips.ICON_LINE_THICKNESS, o.strokeWeight ?: theme.shape.strokeWeight, 0.25f..4f, theme.shape.strokeWeight, times,
                { vm.dragOverride(vm.ui.value.override.copy(strokeWeight = it)) }, done, { commit(o.copy(strokeWeight = null)) },
            )
            val mode = o.fillMode ?: theme.fill.mode
            ChoiceControl(
                Tips.ICON_FILL_STYLE,
                FILL_STYLE_NAMES.toList(),
                mode, theme.fill.mode, { commit(o.copy(fillMode = it.takeIf { m -> m != theme.fill.mode })) },
            )
            // Gap closing seals shapes for the flood fill: invert and both silhouette modes.
            if (mode != FillMode.OUTLINE) {
                SliderControl(
                    Tips.ICON_CLOSE_GAPS, o.gapClosing ?: theme.fill.gapClosing, 0f..2f, theme.fill.gapClosing, times,
                    { vm.dragOverride(vm.ui.value.override.copy(gapClosing = it)) }, done, { commit(o.copy(gapClosing = null)) },
                )
            }
            val own = vm.ownOffset(item.drawable)
            val offset = o.textureOffset ?: own
            fun at(x: Float, y: Float) = TextureOffset(x.coerceIn(0f, OFFSET_MAX), y.coerceIn(0f, OFFSET_MAX))
            SliderControl(
                Tips.PATTERN_X, offset.x, 0f..OFFSET_MAX, own.x, percent,
                { vm.dragOverride(vm.ui.value.override.copy(textureOffset = at(it, (vm.ui.value.override.textureOffset ?: own).y))) },
                done, { commit(o.copy(textureOffset = null)) },
            )
            SliderControl(
                Tips.PATTERN_Y, offset.y, 0f..OFFSET_MAX, own.y, percent,
                { vm.dragOverride(vm.ui.value.override.copy(textureOffset = at((vm.ui.value.override.textureOffset ?: own).x, it))) },
                done, { commit(o.copy(textureOffset = null)) },
            )
            val hasStreaks = theme.layers.any { it is EffectLayer && (it.effect is Effect.Streaks || it.effect is Effect.BrushStrokes) }
            SwitchControl(Tips.HIDE_STREAKS, o.hideStreaks, default = false, enabled = hasStreaks || o.hideStreaks) {
                commit(o.copy(hideStreaks = it))
            }
            if (!hasStreaks) Text("This theme has no streaks or brush strokes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Gap()

            SwapSearch(
                current = o.swapDrawable,
                search = vm::searchDrawables,
                onPick = { picked -> commit(o.copy(swapDrawable = picked.takeIf { it != vm.drawableName(item.drawable) })) },
                onClear = { commit(o.copy(swapDrawable = null)) },
            )
            Gap()
            HelpAnchor(Tips.UNDO_ICON) {
                OutlinedButton(onClick = vm::resetOverride, enabled = !o.isEmpty, modifier = Modifier.fillMaxWidth()) { Text(Tips.UNDO_ICON.label) }
            }
        }
    }
}
