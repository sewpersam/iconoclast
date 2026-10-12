package dev.iconoclast.pack

import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.Date

class SigningKey(val privateKey: PrivateKey, val certificate: X509Certificate)

/**
 * The key files are there but do not open: empty, truncated, the wrong password, or the
 * store without its password. Never replaced silently: the user restores a backup or
 * asks for a new key, and the damaged files are set aside, not deleted.
 */
class DamagedKeyException(message: String, cause: Throwable? = null) : KeyStoreException(message, cause)

enum class KeyState {
    /** No key yet: the first Apply makes one. */
    NONE,
    OK,

    /** Files present that do not open. See [DamagedKeyException]. */
    DAMAGED,
}

/**
 * The pack signing key, spec 3.5: EC P-256, generated on first use, stored as PKCS12 in
 * [dir] with a random password kept beside it. The app passes `noBackupFilesDir`, so the
 * key never leaves the sandbox. If the designer is uninstalled the key is gone, spec 6.5.
 */
class PackKeyStore(private val dir: File, private val commonName: String) {
    internal val storeFile = File(dir, "pack-signing.p12")
    internal val passwordFile = File(dir, "pack-signing.pass")

    val exists: Boolean get() = storeFile.exists() && passwordFile.exists()

    /**
     * A store is the key. A password with no store is what a crash between the two writes
     * of [create] leaves, so it counts as no key; a store with no password is damage.
     */
    @Synchronized
    fun state(): KeyState = when {
        !storeFile.exists() -> KeyState.NONE
        else -> try {
            load()
            KeyState.OK
        } catch (e: DamagedKeyException) {
            KeyState.DAMAGED
        }
    }

    /** Throws [DamagedKeyException] when the key files are there but do not open. */
    @Synchronized
    fun loadOrCreate(): SigningKey = if (storeFile.exists()) load() else create()

    /**
     * Moves damaged key files to `*.damaged`, replacing an older pair, so the next
     * [loadOrCreate] or a restore starts clean. A key that opens is never touched.
     */
    @Synchronized
    fun setAsideDamaged() {
        if (state() != KeyState.DAMAGED) return
        for (f in listOf(storeFile, passwordFile)) {
            if (f.exists()) check(f.renameTo(File(dir, f.name + DAMAGED_SUFFIX))) { "could not set aside $f" }
        }
    }

    private fun load(): SigningKey = try {
        if (!passwordFile.exists()) throw DamagedKeyException("$storeFile has no password file")
        val password = passwordFile.readText().toCharArray()
        val store = KeyStore.getInstance("PKCS12")
        storeFile.inputStream().use { store.load(it, password) }
        val key = store.getKey(ALIAS, password) as? PrivateKey ?: throw DamagedKeyException("no signing key in $storeFile")
        val cert = store.getCertificate(ALIAS) as? X509Certificate ?: throw DamagedKeyException("no certificate in $storeFile")
        SigningKey(key, cert)
    } catch (e: DamagedKeyException) {
        throw e
    } catch (e: Exception) {
        // Empty or truncated: IOException or EOFException; wrong password: IOException
        // wrapping UnrecoverableKeyException; anything else from the provider likewise.
        throw DamagedKeyException("the signing key in $storeFile does not open: ${e.message ?: e.javaClass.simpleName}", e)
    }

    private fun create(): SigningKey {
        val random = SecureRandom()
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), random) }.generateKeyPair()
        val now = System.currentTimeMillis()
        val cert = SelfSignedCertificate.create(keys, commonName, Date(now - DAY), Date(now + VALIDITY_YEARS * 365L * DAY), random)
        val password = Base64.getEncoder().encodeToString(ByteArray(32).also(random::nextBytes)).toCharArray()
        val store = KeyStore.getInstance("PKCS12").apply { load(null, null) }
        store.setKeyEntry(ALIAS, keys.private, password, arrayOf(cert))
        dir.mkdirs()
        // Password first, store second: a crash in between leaves no store, so the next run starts over.
        writeAtomically(passwordFile) { it.write(String(password).toByteArray()) }
        writeAtomically(storeFile) { store.store(it, password) }
        return SigningKey(keys.private, cert)
    }

    /**
     * Writes [target] through a temporary file, synced before the rename: without the sync
     * a power loss soon after can leave the renamed file empty, and the key is lost.
     */
    internal fun writeAtomically(target: File, write: (java.io.OutputStream) -> Unit) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        java.io.FileOutputStream(tmp).use {
            write(it)
            it.fd.sync()
        }
        check(tmp.renameTo(target)) { "could not write $target" }
    }

    internal companion object {
        const val ALIAS = "pack"
        const val DAY = 86_400_000L
        const val VALIDITY_YEARS = 30
        const val DAMAGED_SUFFIX = ".damaged"
    }
}
