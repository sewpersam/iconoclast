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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    ModalBottomSheet(onDismissRequest = vm::closeOverride, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(128.dp).background(Color(0xFF3C4043), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    if (image != null) Image(image, contentDescription = "${item.label} with these overrides", Modifier.size(112.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(item.label, style = MaterialTheme.typography.titleMedium)
                    Text(vm.drawableName(item.drawable).replace('_', ' '), style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (o.isEmpty) "Drawn as the theme says" else "Overridden in this theme",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SliderControl(
                "Stroke weight", o.strokeWeight ?: theme.shape.strokeWeight, 0.25f..4f, theme.shape.strokeWeight, decimals(2, " u"),
                { vm.dragOverride(vm.ui.value.override.copy(strokeWeight = it)) }, done, { commit(o.copy(strokeWeight = null)) },
            )
            val mode = o.fillMode ?: theme.fill.mode
            ChoiceControl(
                "Fill mode",
                listOf(
                    FillMode.OUTLINE to "Outline", FillMode.INVERT to "Invert fill",
                    FillMode.SILHOUETTE to "Silhouette", FillMode.OUTLINE_SILHOUETTE to "Outline + Silhouette",
                ),
                mode, theme.fill.mode, { commit(o.copy(fillMode = it.takeIf { m -> m != theme.fill.mode })) },
            )
            // Gap closing seals shapes for the flood fill: invert and both silhouette modes.
            if (mode != FillMode.OUTLINE) {
                SliderControl(
                    "Gap closing", o.gapClosing ?: theme.fill.gapClosing, 0f..2f, theme.fill.gapClosing, decimals(2, " u"),
                    { vm.dragOverride(vm.ui.value.override.copy(gapClosing = it)) }, done, { commit(o.copy(gapClosing = null)) },
                )
            }
            val own = vm.ownOffset(item.drawable)
            val offset = o.textureOffset ?: own
            fun at(x: Float, y: Float) = TextureOffset(x.coerceIn(0f, OFFSET_MAX), y.coerceIn(0f, OFFSET_MAX))
            SliderControl(
                "Texture offset X", offset.x, 0f..OFFSET_MAX, own.x, percent,
                { vm.dragOverride(vm.ui.value.override.copy(textureOffset = at(it, (vm.ui.value.override.textureOffset ?: own).y))) },
                done, { commit(o.copy(textureOffset = null)) },
            )
            SliderControl(
                "Texture offset Y", offset.y, 0f..OFFSET_MAX, own.y, percent,
                { vm.dragOverride(vm.ui.value.override.copy(textureOffset = at((vm.ui.value.override.textureOffset ?: own).x, it))) },
                done, { commit(o.copy(textureOffset = null)) },
            )
            val hasStreaks = theme.layers.any { it is EffectLayer && (it.effect is Effect.Streaks || it.effect is Effect.BrushStrokes) }
            SwitchControl("Hide strokes and scanlines", o.hideStreaks, default = false, enabled = hasStreaks || o.hideStreaks) {
                commit(o.copy(hideStreaks = it))
            }
            if (!hasStreaks) Text("This theme has no brush strokes or scanlines.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SwapSearch(
                current = o.swapDrawable,
                search = vm::searchDrawables,
                onPick = { picked -> commit(o.copy(swapDrawable = picked.takeIf { it != vm.drawableName(item.drawable) })) },
                onClear = { commit(o.copy(swapDrawable = null)) },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            OutlinedButton(onClick = vm::resetOverride, enabled = !o.isEmpty, modifier = Modifier.fillMaxWidth()) { Text("Reset to theme") }
        }
    }
}
