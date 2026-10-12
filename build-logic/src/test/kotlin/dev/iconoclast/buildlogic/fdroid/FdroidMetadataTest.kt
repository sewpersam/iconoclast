package dev.iconoclast.buildlogic.fdroid

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M8 Task 14: the fastlane metadata F-Droid reads, against the limits on
 * https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/, and the draft
 * recipe against the app's own version.
 */
class FdroidMetadataTest {
    private val root = File("..").canonicalFile
    private val en = root.resolve("fastlane/metadata/android/en-US")
    private val gradle = root.resolve("app/build.gradle.kts").readText()
    private val versionCode = Regex("""versionCode\s*=\s*(\d+)""").find(gradle)!!.groupValues[1]
    private val versionName = Regex("""versionName\s*=\s*"([^"]+)"""").find(gradle)!!.groupValues[1]

    private fun text(name: String) = en.resolve(name).readText().trim()

    @Test
    fun textsAreWithinTheLimits() {
        val limits = mapOf("title.txt" to 50, "short_description.txt" to 80, "full_description.txt" to 4000)
        for ((name, max) in limits) {
            val t = text(name)
            assertTrue(t.isNotEmpty() && t.length <= max, "$name is ${t.length} chars, max $max")
        }
    }

    @Test
    fun theCurrentVersionCodeHasAChangelog() {
        val log = en.resolve("changelogs/$versionCode.txt")
        assertTrue(log.isFile, "no $log for versionCode $versionCode")
        assertTrue(log.readText().trim().length in 1..500, "changelog over 500 chars")
    }

    @Test
    fun theRecipeMatchesTheApp() {
        val recipe = root.resolve("fdroid/dev.iconoclast.app.yml").readText()
        assertTrue("License: GPL-3.0-or-later" in recipe)
        assertTrue(root.resolve("LICENSE").readText().contains("GNU GENERAL PUBLIC LICENSE\n                       Version 3"))
        assertEquals(versionCode, Regex("""versionCode:\s*(\d+)""").find(recipe)!!.groupValues[1])
        assertEquals(versionName, Regex("""versionName:\s*'([^']+)'""").find(recipe)!!.groupValues[1])
        assertTrue("submodules: true" in recipe && "subdir: app" in recipe)
    }

    /** Task 15: only Google Maven and Maven Central, in every build file that could add one. */
    @Test
    fun noRepositoryButGoogleAndMavenCentral() {
        val files = root.walkTopDown()
            .onEnter { it.name !in setOf("build", ".gradle", ".git", "third_party", "spike", ".kotlin") }
            .filter { it.name.endsWith(".gradle.kts") || it.name.endsWith(".gradle") }
            .toList()
        assertTrue(files.size >= 8, "found only $files")
        val allowed = setOf("google()", "mavenCentral()")
        for (f in files) {
            for (block in Regex("""repositories\s*\{([^}]*)}""").findAll(f.readText())) {
                val entries = block.groupValues[1].lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("//") }
                assertTrue(allowed.containsAll(entries), "${f.relativeTo(root)} declares $entries")
            }
        }
        val settings = root.resolve("settings.gradle.kts").readText()
        assertTrue("RepositoriesMode.FAIL_ON_PROJECT_REPOS" in settings)
    }
}
