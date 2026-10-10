package dev.iconoclast.app.theme

import android.content.res.AssetManager

/**
 * The starter presets, spec 2.4. Theme files in assets/presets/, parsed by the same
 * [migrate] as any import, and seeded by [ThemeRepository] on the first launch.
 */
object Presets {
    const val DIR = "presets"

    /** In seeding order. The first becomes the active theme on a fresh install. */
    val FILES = listOf("plain-arcticons.json", "jade.json")

    fun read(assets: AssetManager): List<String> = FILES.map { name ->
        assets.open("$DIR/$name").use { it.readBytes().decodeToString() }
    }
}
