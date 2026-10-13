package dev.iconoclast.app

import dev.iconoclast.app.theme.ThemeDocument
import dev.iconoclast.pack.CoveragePlan
import dev.iconoclast.render.OutputResolution
import java.io.File
import java.security.MessageDigest

/**
 * The last signed pack, kept in [dir] beside the recipe it was built from. An Apply that
 * stops before the install finishes (the install permission, a cancelled confirmation, an
 * old pack to uninstall) leaves it there, and the next Apply of the same recipe installs
 * it without drawing every icon again. The same recipe gives the same bytes, spec 3.5.
 */
class BuiltPack(private val dir: File) {
    private val apk = File(dir, "built.apk")
    private val recipe = File(dir, "built.recipe")

    /** The pack built from [key], or null. */
    fun find(key: String): File? =
        apk.takeIf { it.isFile && recipe.isFile && recipe.readText() == key }

    /** Keeps [signed] as the pack of [key], replacing any earlier one. Returns its new place. */
    fun keep(key: String, signed: File): File {
        recipe.delete()
        check(signed.renameTo(apk)) { "could not keep $signed" }
        recipe.writeText(key)
        return apk
    }

    companion object {
        /**
         * Everything the pack's bytes depend on: the theme and its overrides (textures are
         * named by hash inside), which apps get which drawables, the size, the template and
         * the signing certificate. The theme's id, name and dates are left out.
         */
        fun key(doc: ThemeDocument, plan: CoveragePlan, resolution: OutputResolution, template: ByteArray, certificate: ByteArray): String {
            val sha = MessageDigest.getInstance("SHA-256")
            fun part(bytes: ByteArray) {
                sha.update(bytes.size.toString().toByteArray())
                sha.update(':'.code.toByte())
                sha.update(bytes)
            }
            part(doc.copy(id = "-", name = "", created = 0, modified = 0).toJson().toByteArray())
            part(plan.drawables.joinToString(",").toByteArray())
            part(plan.items.joinToString("\n") { (c, slot) -> "$c=$slot" }.toByteArray())
            part(resolution.name.toByteArray())
            part(template)
            part(certificate)
            return sha.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
