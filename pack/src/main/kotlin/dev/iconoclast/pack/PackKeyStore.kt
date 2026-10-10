package dev.iconoclast.pack

import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.Date

class SigningKey(val privateKey: PrivateKey, val certificate: X509Certificate)

/**
 * The pack signing key, spec 3.5: EC P-256, generated on first use, stored as PKCS12 in
 * [dir] with a random password kept beside it. The app passes `noBackupFilesDir`, so the
 * key never leaves the sandbox. If the designer is uninstalled the key is gone, spec 6.5.
 */
class PackKeyStore(private val dir: File, private val commonName: String) {
    private val storeFile = File(dir, "pack-signing.p12")
    private val passwordFile = File(dir, "pack-signing.pass")

    val exists: Boolean get() = storeFile.exists() && passwordFile.exists()

    @Synchronized
    fun loadOrCreate(): SigningKey = if (exists) load() else create()

    private fun load(): SigningKey {
        val password = passwordFile.readText().toCharArray()
        val store = KeyStore.getInstance("PKCS12")
        storeFile.inputStream().use { store.load(it, password) }
        val key = store.getKey(ALIAS, password) as PrivateKey
        return SigningKey(key, store.getCertificate(ALIAS) as X509Certificate)
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

    private fun writeAtomically(target: File, write: (java.io.OutputStream) -> Unit) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.outputStream().use(write)
        check(tmp.renameTo(target)) { "could not write $target" }
    }

    private companion object {
        const val ALIAS = "pack"
        const val DAY = 86_400_000L
        const val VALIDITY_YEARS = 30
    }
}
