package dev.iconoclast.pack

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PackKeyBackupTest {
    private fun dir(): File = Files.createTempDirectory("key").toFile().apply { deleteOnExit() }
    private fun store(d: File = dir()) = PackKeyStore(d, "Test Pack")

    @Test
    fun theKeyRoundTripsIntoAFreshInstall() {
        val old = store()
        assertNull(old.backup(), "no key before the first Apply")
        val key = old.loadOrCreate()
        val backup = old.backup()!!

        val fresh = store()
        assertEquals(RestoreResult.RESTORED, fresh.restore(backup))
        val restored = fresh.loadOrCreate()
        assertEquals(key.certificate, restored.certificate)
        assertEquals(key.privateKey, restored.privateKey)
        assertContentEquals(backup.p12, fresh.backup()!!.p12)
        assertEquals(RestoreResult.ALREADY_PRESENT, fresh.restore(backup), "the same key twice is fine")
    }

    @Test
    fun aDifferentLocalKeyIsAnErrorAndIsKept() {
        val other = store().apply { loadOrCreate() }.backup()!!
        val d = dir()
        val mine = store(d).apply { loadOrCreate() }
        val before = mine.backup()!!
        assertFailsWith<KeyMismatchException> { mine.restore(other) }
        assertContentEquals(before.p12, store(d).backup()!!.p12)
        assertContentEquals(before.password, store(d).backup()!!.password)
    }

    @Test
    fun aDamagedBackupWritesNothing() {
        val good = store().apply { loadOrCreate() }.backup()!!
        for (bad in listOf(KeyBackup(good.p12, "wrong".toByteArray()), KeyBackup(byteArrayOf(1, 2, 3), good.password))) {
            val d = dir()
            assertFailsWith<InvalidKeyBackupException> { store(d).restore(bad) }
            assertFalse(store(d).exists)
            assertEquals(0, d.list()!!.size)
        }
    }

    /** A damaged local store fails as DamagedKeyException, a GeneralSecurityException the app already catches. */
    @Test
    fun aDamagedLocalStoreFailsWithACaughtType() {
        for (damage in listOf(byteArrayOf(1, 2, 3), ByteArray(0))) {
            val d = dir()
            store(d).loadOrCreate()
            File(d, "pack-signing.p12").writeBytes(damage)
            val e = assertFailsWith<DamagedKeyException> { store(d).loadOrCreate() }
            assertTrue(e is java.security.GeneralSecurityException, "${e.javaClass}")
        }
    }
}
