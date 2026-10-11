package dev.iconoclast.app.theme

import dev.iconoclast.render.FillMode
import dev.iconoclast.render.TextureOffset
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * One icon's overrides in one theme, spec 2.2 screen 3. Every field left null, and
 * [hideStreaks] false, keeps the theme's value. Stored in [ThemeDocument.overrides] under
 * the icon's appfilter drawable name, never in the Theme: [effectiveTheme] resolves them.
 *
 * - [strokeWeight]: the shape's stroke weight, 48-grid units, 0.25 to 4.
 * - [fillMode]: the fill mode.
 * - [gapClosing]: the fill's gap closing, 48-grid units, 0 to 2.
 * - [textureOffset]: pins the icon's one per-icon offset, both numbers in [0, 1); texture
 *   and noise layers, streak breakup and grain all follow it.
 * - [hideStreaks]: drops every Streaks effect layer for this icon.
 * - [swapDrawable]: draws another Arcticons drawable's geometry in this icon's slot. The
 *   slot keeps its own seed, so its offset does not change with the swap.
 */
@Serializable
data class IconOverride(
    @Serializable(CanonicalFloatSerializer::class) val strokeWeight: Float? = null,
    val fillMode: FillMode? = null,
    @Serializable(CanonicalFloatSerializer::class) val gapClosing: Float? = null,
    @Serializable(TextureOffsetSerializer::class) val textureOffset: TextureOffset? = null,
    val hideStreaks: Boolean = false,
    val swapDrawable: String? = null,
) {
    init {
        strokeWeight?.let { require(it in 0.25f..4f) { "override strokeWeight $it" } }
        gapClosing?.let { require(it in 0f..2f) { "override gapClosing $it" } }
        swapDrawable?.let { require(it.isNotBlank()) { "override swapDrawable is blank" } }
    }

    /** Overrides nothing: "reset to theme" leaves this, and the document drops it. */
    val isEmpty: Boolean get() = this == NONE

    companion object {
        val NONE = IconOverride()
    }
}

/** A [TextureOffset] as `{"x": .., "y": ..}`, both in [0, 1). */
object TextureOffsetSerializer : KSerializer<TextureOffset> {
    @Serializable
    private data class Surrogate(
        @Serializable(CanonicalFloatSerializer::class) val x: Float,
        @Serializable(CanonicalFloatSerializer::class) val y: Float,
    )

    private val surrogate = Surrogate.serializer()
    override val descriptor = surrogate.descriptor
    override fun serialize(encoder: Encoder, value: TextureOffset) = encoder.encodeSerializableValue(surrogate, Surrogate(value.x, value.y))
    override fun deserialize(decoder: Decoder): TextureOffset = decoder.decodeSerializableValue(surrogate).let { TextureOffset(it.x, it.y) }
}
