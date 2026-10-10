package dev.iconoclast.iconsource

import android.content.res.AssetManager
import dev.iconoclast.render.format.ComponentTable
import dev.iconoclast.render.format.Icon
import dev.iconoclast.render.format.IconBundle
import java.nio.ByteBuffer

/**
 * The bundled Arcticons set: geometry, drawable names and the appfilter index.
 * Icons decode one at a time on request; nothing is decoded up front.
 */
class IconSource(val bundle: IconBundle, val names: DrawableNames, val components: ComponentIndex) {

    init {
        require(bundle.size == names.size) { "bundle has ${bundle.size} icons but ${names.size} names" }
    }

    /** Drawable index of [name], or -1. */
    fun indexOf(name: String): Int = names.indexOf(name)

    fun icon(index: Int): Icon = bundle.icon(index)

    fun icon(name: String): Icon? = indexOf(name).takeIf { it >= 0 }?.let(bundle::icon)

    /** Drawable indices whose name contains [query]. See [DrawableNames.search]. */
    fun search(query: String, limit: Int = Int.MAX_VALUE): List<Int> = names.search(query, limit)

    /** Drawable index for a launcher component string, or -1. See [ComponentIndex.resolve]. */
    fun resolve(component: String): Int = components.resolve(component)

    companion object {
        const val ASSET_DIR = "arcticons"

        /** Reads the three generated assets. Call off the main thread: it reads about 9 MB. */
        fun open(assets: AssetManager): IconSource = fromBytes(
            assets.open("$ASSET_DIR/icons.bin").use { it.readBytes() },
            assets.open("$ASSET_DIR/names.txt").use { it.readBytes() },
            assets.open("$ASSET_DIR/components.bin").use { it.readBytes() },
        )

        fun fromBytes(bundle: ByteArray, names: ByteArray, components: ByteArray) = IconSource(
            IconBundle(ByteBuffer.wrap(bundle)),
            DrawableNames.parse(names),
            ComponentIndex(ComponentTable(ByteBuffer.wrap(components))),
        )
    }
}
