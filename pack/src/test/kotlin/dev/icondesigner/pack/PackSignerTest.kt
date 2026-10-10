package dev.icondesigner.pack

import com.android.apksig.ApkVerifier
import java.io.File
import java.nio.file.Files
import java.security.interfaces.ECPublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PackSignerTest {
    private val dir: File = Files.createTempDirectory("signer").toFile().apply { deleteOnExit() }

    @Test
    fun signedPackVerifiesWithV2AndV3AndStaysAligned() {
        val key = PackKeyStore(File(dir, "keys"), "Test Pack").loadOrCreate()
        val unsigned = ZipRewriter.rewrite(templateBytes(), mapOf("res/drawable-nodpi-v4/slot_0001.png" to ByteArray(123) { 5 }))
        val apk = File(dir, "signed.apk")
        PackSigner.sign(unsigned, key, apk)

        val result = ApkVerifier.Builder(apk).build().verify()
        assertTrue(result.isVerified, "errors: ${result.errors} ${result.v2SchemeSigners.flatMap { it.errors }}")
        assertFalse(result.isVerifiedUsingV1Scheme)
        // From API 28 a verifier reads only v3 when it is present.
        assertTrue(result.isVerifiedUsingV3Scheme)
        assertEquals(key.certificate, result.signerCertificates.single())
        // The v2 block is there too: check it over a platform range that predates v3.
        val legacy = ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(24).setMaxCheckedPlatformVersion(27).build().verify()
        assertTrue(legacy.isVerified, "v2 errors: ${legacy.errors}")
        assertTrue(legacy.isVerifiedUsingV2Scheme)
        assertZipaligned(apk)
    }

    @Test
    fun keyIsEcP256AndSurvivesReload() {
        val keys = File(dir, "reload")
        val first = PackKeyStore(keys, "Test Pack").loadOrCreate()
        val second = PackKeyStore(keys, "Test Pack").loadOrCreate()
        assertEquals(first.certificate, second.certificate)
        assertEquals(first.privateKey, second.privateKey)
        assertEquals(256, (first.certificate.publicKey as ECPublicKey).params.order.bitLength())
        // The SubjectPublicKeyInfo names the curve: OID 1.2.840.10045.3.1.7 is secp256r1, P-256.
        val p256 = byteArrayOf(0x06, 0x08, 0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D, 0x03, 0x01, 0x07)
        val spki = first.certificate.publicKey.encoded
        assertTrue((0..spki.size - p256.size).any { i -> p256.indices.all { spki[i + it] == p256[it] } }, "not P-256")
        assertTrue(File(keys, "pack-signing.p12").exists())
        first.certificate.verify(first.certificate.publicKey)
        assertEquals("CN=Test Pack", first.certificate.subjectX500Principal.name)
    }
}
