package dev.iconoclast.buildlogic.licenses

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LicenseListTest {
    @Test
    fun pomLicencesAndParentAreRead() {
        val pom = LicenseList.parsePom(
            """<?xml version="1.0"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <parent><groupId>g</groupId><artifactId>p</artifactId><version>1</version></parent>
              <licenses><license><name>The Apache Software License, Version 2.0</name><url>u</url></license></licenses>
            </project>""".toByteArray(),
        )
        assertEquals(listOf("The Apache Software License, Version 2.0"), pom.licenses)
        assertEquals(Triple("g", "p", "1"), pom.parent)
    }

    @Test
    fun aPomWithoutLicencesHasNone() {
        val pom = LicenseList.parsePom("<project><groupId>g</groupId></project>".toByteArray())
        assertEquals(emptyList(), pom.licenses)
        assertNull(pom.parent)
    }

    @Test
    fun licenceNamesMapToSpdx() {
        assertEquals("Apache-2.0", LicenseList.spdx("The Apache Software License, Version 2.0"))
        assertEquals("Apache-2.0", LicenseList.spdx("Apache-2.0"))
        assertEquals("MIT", LicenseList.spdx("MIT License"))
        assertEquals("BSD-3-Clause", LicenseList.spdx("New BSD License"))
        assertEquals("Some Licence", LicenseList.spdx(" Some Licence "))
    }

    @Test
    fun assetAndSectionAreSortedAndReplaceable() {
        val deps = listOf(Dependency("b", "y", "2", listOf("MIT")), Dependency("a", "x", "1", listOf("Apache-2.0")))
        assertEquals("a:x:1\tApache-2.0\nb:y:2\tMIT\n", LicenseList.asset(deps))
        val doc = "head\n${LicenseList.START}\nold\n${LicenseList.END}\ntail\n"
        val out = LicenseList.replaceSection(doc, LicenseList.markdown(deps))
        assert(out.startsWith("head\n${LicenseList.START}\n") && out.endsWith("${LicenseList.END}\ntail\n"))
        assert(out.indexOf("`a:x`") < out.indexOf("`b:y`"))
        assertFailsWith<IllegalArgumentException> { LicenseList.replaceSection("no markers", "x") }
    }
}

/** M8 Task 15: `checkDependencyLicenses` fails on what F-Droid would reject. */
class NonFreeTest {
    private fun dep(coordinates: String, licence: String = "Apache-2.0"): Dependency {
        val (g, n, v) = coordinates.split(':')
        return Dependency(g, n, v, listOf(licence))
    }

    @Test
    fun freeArtifactsPass() {
        val deps = listOf(dep("androidx.core:core:1.0"), dep("org.jetbrains:annotations:13.0"), dep("x:y:1", "MIT"), dep("x:z:1", "BSD-3-Clause"))
        assertEquals(emptyList(), LicenseList.problems(deps))
    }

    @Test
    fun aNonFreeLicenceFails() {
        val out = LicenseList.problems(listOf(dep("com.example:sdk:1.0", "Proprietary"), dep("com.example:other:1.0", "Android Software Development Kit License")))
        assertEquals(2, out.size, "$out")
        assert(out.all { "licence" in it }) { "$out" }
    }

    /** Groups from F-Droid's SUSS list (https://fdroid.gitlab.io/fdroid-suss/suss.json), Apache-licensed POMs included. */
    @Test
    fun knownNonFreeArtifactsFailWhateverTheirPomSays() {
        val bad = listOf(
            "com.google.android.gms:play-services-base:18.5.0",
            "com.google.firebase:firebase-analytics:22.0.0",
            "com.google.firebase:firebase-crashlytics:19.0.0",
            "com.crashlytics.sdk.android:crashlytics:2.10.1",
            "io.fabric.sdk.android:fabric:1.4.8",
            "com.facebook.android:facebook-android-sdk:17.0.0",
            "com.google.android.play:core:1.10.3",
            "com.android.billingclient:billing:7.0.0",
            "com.android.installreferrer:installreferrer:2.2",
            "com.google.android.ads:mediation-test-suite:3.0.0",
        )
        val out = LicenseList.problems(bad.map { dep(it) })
        assertEquals(bad.size, out.size, "$out")
    }
}
