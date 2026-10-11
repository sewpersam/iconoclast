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
