package dev.iconoclast.pack

import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * The signing key as it travels in a theme backup, spec 6.5: the PKCS12 file and its
 * password, exactly the two files [PackKeyStore] keeps. Whoever holds both can sign packs
 * that update this one, so it leaves the phone only behind the explicit warning.
 */
class KeyBackup(val p12: ByteArray, val password: ByteArray) {
    /**
     * The key and certificate the backup holds. Throws [InvalidKeyBackupException] when the
     * bytes are not a PKCS12 store this password opens, holding an EC key under the pack alias.
     */
    fun open(): SigningKey = try {
        val pass = String(password, Charsets.UTF_8).toCharArray()
        val store = KeyStore.getInstance("PKCS12").apply { load(p12.inputStream(), pass) }
        val key = store.getKey(PackKeyStore.ALIAS, pass) as? PrivateKey ?: throw InvalidKeyBackupException("no key")
        val cert = store.getCertificate(PackKeyStore.ALIAS) as? X509Certificate ?: throw InvalidKeyBackupException("no certificate")
        if (key.algorithm != "EC") throw InvalidKeyBackupException("not an EC key")
        SigningKey(key, cert)
    } catch (e: InvalidKeyBackupException) {
        throw e
    } catch (e: Exception) {
        throw InvalidKeyBackupException(e.message ?: e.javaClass.simpleName, e)
    }
}

class InvalidKeyBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The backup holds another key than the one on this phone. Nothing was changed. */
class KeyMismatchException : Exception("a different signing key is already on this phone")

/** The stored key's two files, or null when this install has no key yet. Throws [DamagedKeyException] rather than export a damaged key. */
fun PackKeyStore.backup(): KeyBackup? = synchronized(this) {
    when (state()) {
        KeyState.NONE -> null
        KeyState.DAMAGED -> throw DamagedKeyException("the signing key on this phone is damaged")
        KeyState.OK -> KeyBackup(storeFile.readBytes(), passwordFile.readBytes())
    }
}

enum class RestoreResult {
    /** No key was here; the backup's key is now this install's key. */
    RESTORED,

    /** This phone already holds the same key; nothing was written. */
    ALREADY_PRESENT,
}

/**
 * Restores [backup] as this install's key, spec 6.5. Only when no usable key exists: the
 * same key already present is [RestoreResult.ALREADY_PRESENT], a different one throws
 * [KeyMismatchException] and leaves both as they are. A damaged local key is set aside
 * (`*.damaged`) and replaced. Never overwrites a key that opens. The backup is opened
 * first, so a damaged one throws [InvalidKeyBackupException] and writes nothing.
 */
fun PackKeyStore.restore(backup: KeyBackup): RestoreResult = synchronized(this) {
    val incoming = backup.open()
    when (state()) {
        KeyState.OK -> {
            if (loadOrCreate().certificate.encoded.contentEquals(incoming.certificate.encoded)) return RestoreResult.ALREADY_PRESENT
            throw KeyMismatchException()
        }
        KeyState.DAMAGED -> setAsideDamaged()
        KeyState.NONE -> Unit
    }
    storeFile.parentFile?.mkdirs()
    // Password first, store second, as create() does: a crash between leaves no store.
    writeAtomically(passwordFile) { it.write(backup.password) }
    writeAtomically(storeFile) { it.write(backup.p12) }
    RestoreResult.RESTORED
}
