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
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import javax.inject.Inject

/**
 * Builds the unsigned icon pack template with aapt2 and d8 from build-tools, following
 * spike/pack.py: [slotCount] placeholder PNGs stored uncompressed, a stub res/xml
 * appfilter, stub assets the designer rewrites, the licence, and one tiny activity.
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
        val placeholder = transparentPng()
        for (i in 0 until slotCount.get()) drawables.resolve(slotName(i) + ".png").writeBytes(placeholder)
        drawables.resolve("pack_icon.png").writeBytes(placeholder)
        res.resolve("xml").apply { mkdirs() }.resolve("appfilter.xml").writeText(EMPTY_RESOURCES)

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

    /** Copies the aapt2 output and appends classes.dex, with fixed timestamps. */
    private fun addDex(linked: File, dex: ByteArray, out: File) {
        out.parentFile.mkdirs()
        ZipFile(linked).use { zin ->
            ZipOutputStream(out.outputStream().buffered()).use { zout ->
                for (e in zin.entries()) {
                    val data = zin.getInputStream(e).use { it.readBytes() }
                    zout.putNextEntry(entry(e.name, data, e.method))
                    zout.write(data)
                    zout.closeEntry()
                }
                zout.putNextEntry(entry("classes.dex", dex, ZipEntry.DEFLATED))
                zout.write(dex)
                zout.closeEntry()
            }
        }
    }

    private fun entry(name: String, data: ByteArray, method: Int) = ZipEntry(name).apply {
        time = FIXED_TIME
        this.method = method
        if (method == ZipEntry.STORED) {
            size = data.size.toLong()
            compressedSize = data.size.toLong()
            crc = CRC32().apply { update(data) }.value
        }
    }

    private fun transparentPng(): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", out)
        return out.toByteArray()
    }

    companion object {
        fun slotName(i: Int) = "slot_%04d".format(i)

        private const val EMPTY_RESOURCES = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources />\n"

        /** 1981-01-01 local time, inside the DOS date range. */
        private val FIXED_TIME = java.util.GregorianCalendar(1981, 0, 1).timeInMillis

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
