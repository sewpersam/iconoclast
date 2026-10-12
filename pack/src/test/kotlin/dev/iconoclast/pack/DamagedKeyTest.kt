package dev.iconoclast.pack

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * M8 Task 18, the M7 addendum deferral: key files that exist but do not open are a
 * damaged key, reported as such, never a raw keystore error and never silently replaced.
 */
class DamagedKeyTest {
    private fun dir(): File = Files.createTempDirectory("key").toFile().apply { deleteOnExit() }
    private fun store(d: File) = PackKeyStore(d, "Test Pack")
    private fun p12(d: File) = File(d, "pack-signing.p12")
    private fun pass(d: File) = File(d, "pack-signing.pass")

    /** The ways the files were found on icondesign36 and the ways a write can leave them. */
    private fun damages(): List<Pair<String, (File) -> Unit>> = listOf(
        "both files empty" to { d -> p12(d).writeBytes(ByteArray(0)); pass(d).writeBytes(ByteArray(0)) },
        "store empty" to { d -> p12(d).writeBytes(ByteArray(0)) },
        "store truncated" to { d -> p12(d).writeBytes(p12(d).readBytes().copyOf(100)) },
        "wrong password" to { d -> pass(d).writeText("not the password") },
        "store without its password" to { d -> pass(d).delete() },
    )

    @Test
    fun aDamagedKeyIsReportedAndKept() {
        for ((name, damage) in damages()) {
            val d = dir()
            store(d).loadOrCreate()
            damage(d)
            val before = p12(d).readBytes()
            assertEquals(KeyState.DAMAGED, store(d).state(), name)
            assertFailsWith<DamagedKeyException>(name) { store(d).loadOrCreate() }
            assertContentEquals(before, p12(d).readBytes(), "$name: the damaged store is not replaced")
        }
    }

    @Test
    fun aFreshInstallStillMakesAKey() {
        val d = dir()
        assertEquals(KeyState.NONE, store(d).state())
        val key = store(d).loadOrCreate()
        assertEquals(KeyState.OK, store(d).state())
        assertEquals(key.certificate, store(d).loadOrCreate().certificate)

        // A crash between the two writes leaves a password and no store: start over, as before.
        val e = dir()
        pass(e).writeText("left behind")
        assertEquals(KeyState.NONE, store(e).state())
        store(e).loadOrCreate()
        assertEquals(KeyState.OK, store(e).state())
    }

    @Test
    fun aBackupRestoresOverADamagedKey() {
        val good = store(dir()).apply { loadOrCreate() }
        val backup = good.backup()!!
        for ((name, damage) in damages()) {
            val d = dir()
            store(d).loadOrCreate()
            damage(d)
            val damagedBytes = p12(d).readBytes()
            assertEquals(RestoreResult.RESTORED, store(d).restore(backup), name)
            assertEquals(good.loadOrCreate().certificate, store(d).loadOrCreate().certificate, name)
            assertContentEquals(damagedBytes, File(d, "pack-signing.p12.damaged").readBytes(), "$name: set aside, not deleted")
        }
    }

    @Test
    fun settingADamagedKeyAsideLetsANewOneBeMade() {
        val d = dir()
        val old = store(d).loadOrCreate()
        p12(d).writeBytes(ByteArray(0))
        store(d).setAsideDamaged()
        assertEquals(KeyState.NONE, store(d).state())
        assertTrue(File(d, "pack-signing.p12.damaged").exists())
        assertNotEquals(old.certificate, store(d).loadOrCreate().certificate)
    }

    @Test
    fun aGoodKeyIsNeverSetAside() {
        val d = dir()
        val key = store(d).loadOrCreate()
        store(d).setAsideDamaged()
        assertEquals(key.certificate, store(d).loadOrCreate().certificate)
        assertTrue(d.list()!!.none { it.endsWith(".damaged") })
    }
}
