package dev.icondesigner.buildlogic.templatepack

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

/**
 * Builds the unsigned icon pack template with aapt2 and d8 from build-tools, following
 * spike/pack.py: [slotCount] placeholder PNGs stored uncompressed, stub assets the
 * designer rewrites, the licence, and one tiny activity.
 */
@CacheableTask
abstract class BuildTemplatePackTask @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val aapt2: RegularFileProperty

    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val d8: RegularFileProperty

    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val androidJar: RegularFileProperty

    /** The compiled OpenDesigner activity. */
    @get:Classpath
    abstract val activityClasses: ConfigurableFileCollection

    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val ccLicense: RegularFileProperty

    @get:Input abstract val packageName: Property<String>
    @get:Input abstract val designerPackage: Property<String>
    /** The designer's app name. The pack is labelled "<appName> Pack". */
    @get:Input abstract val appName: Property<String>
    @get:Input abstract val minSdk: Property<Int>
    @get:Input abstract val targetSdk: Property<Int>
    @get:Input abstract val slotCount: Property<Int>

    @get:Internal
    abstract val workDir: DirectoryProperty

    @get:OutputFile
    abstract val outputApk: RegularFileProperty

    @TaskAction
    fun build() {
        val work = workDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val res = work.resolve("res")
        val drawables = res.resolve("drawable-nodpi").apply { mkdirs() }
        val placeholder = TRANSPARENT_PNG
        for (i in 0 until slotCount.get()) drawables.resolve(slotName(i) + ".png").writeBytes(placeholder)
        drawables.resolve("pack_icon.png").writeBytes(placeholder)
        // No res/xml/appfilter.xml: Neo reads it before the assets copy, so an empty stub hides
        // every icon. See docs/verification/m3.md. The assets copy is the only appfilter.

        val assets = work.resolve("assets").apply { mkdirs() }
        assets.resolve("appfilter.xml").writeText(EMPTY_RESOURCES)
        assets.resolve("drawable.xml").writeText(EMPTY_RESOURCES)
        ccLicense.get().asFile.copyTo(assets.resolve("LICENSE-CC-BY-SA-4.0.txt"))
        assets.resolve("NOTICE.txt").writeText(notice(appName.get()))

        val manifest = work.resolve("AndroidManifest.xml")
        manifest.writeText(
            templateManifest(packageName.get(), designerPackage.get(), "${appName.get()} Pack", minSdk.get(), targetSdk.get()),
        )

        val compiled = work.resolve("res.zip")
        run(aapt2, "compile", "--no-crunch", "--dir", res.path, "-o", compiled.path)
        val linked = work.resolve("linked.apk")
        run(
            aapt2, "link", "-o", linked.path, "-I", androidJar.get().asFile.path,
            "--manifest", manifest.path, "-A", assets.path,
            "--no-compress-regex", "\\.png$", compiled.path,
        )

        val dexDir = work.resolve("dex").apply { mkdirs() }
        val classes = activityClasses.files.filter { it.exists() }
            .flatMap { dir -> dir.walk().filter { it.isFile && it.extension == "class" }.sortedBy { it.path }.toList() }
        run(
            d8, "--release", "--min-api", minSdk.get().toString(),
            "--lib", androidJar.get().asFile.path, "--output", dexDir.path, *classes.map { it.path }.toTypedArray(),
        )
        val dex = dexDir.resolve("classes.dex").readBytes()
        checkNoKotlinRuntime(dex)
        addDex(linked, dex, outputApk.get().asFile)
    }

    private fun run(tool: RegularFileProperty, vararg args: String) {
        exec.exec { it.commandLine(listOf(tool.get().asFile.path) + args) }
    }

    /** The pack ships no Kotlin stdlib, so its one class may only touch the framework. */
    private fun checkNoKotlinRuntime(dex: ByteArray) {
        val text = String(dex, Charsets.ISO_8859_1)
        val refs = Regex("Lkotlin/[A-Za-z0-9_/$]+;").findAll(text).map { it.value }.toSet() - "Lkotlin/Metadata;"
        check(refs.isEmpty()) { "OpenDesigner references the Kotlin runtime: $refs" }
    }

    /** Copies the aapt2 output raw and appends classes.dex, stored. See [TemplateZip]. */
    private fun addDex(linked: File, dex: ByteArray, out: File) {
        out.parentFile.mkdirs()
        out.writeBytes(TemplateZip.assemble(linked.readBytes(), listOf("classes.dex" to dex)))
    }

    companion object {
        fun slotName(i: Int) = "slot_%04d".format(java.util.Locale.ROOT, i)

        private const val EMPTY_RESOURCES = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources />\n"

        /**
         * A 1x1 transparent RGBA PNG, fixed bytes rather than an encoder's output, so the
         * template does not depend on the build JDK's zlib.
         */
        internal val TRANSPARENT_PNG: ByteArray = (
            "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489" +
                "0000000d4944415478da63606060600000000500017aa857500000000049454e44ae426082"
            ).chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        private fun notice(appName: String) = """
            This icon pack was generated by $appName.

            The icons are derived from Arcticons (https://github.com/Arcticons-Team/Arcticons)
            by the Arcticons Team and contributors, licensed under Creative Commons
            Attribution-ShareAlike 4.0 International (CC BY-SA 4.0),
            https://creativecommons.org/licenses/by-sa/4.0/. They were converted, restyled and
            rasterized by $appName: stroke weight, fill, colour, background, textures and
            effects differ from the originals. The licence text is in LICENSE-CC-BY-SA-4.0.txt.
            These icons are licensed under the same licence.
        """.trimIndent() + "\n"
    }
}
