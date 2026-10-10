package dev.iconoclast.pack

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A minimal X.509 v1 certificate for an EC P-256 key, DER-encoded by hand. Android has
 * no public certificate builder and the app takes no BouncyCastle dependency. The
 * signature scheme is ecdsa-with-SHA256. APK signing only uses the certificate to carry
 * the public key and identify the signer.
 */
object SelfSignedCertificate {
    private val ECDSA_SHA256 = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D, 0x04, 0x03, 0x02)
    private val COMMON_NAME = byteArrayOf(0x55, 0x04, 0x03)

    fun create(keys: KeyPair, commonName: String, notBefore: Date, notAfter: Date, random: SecureRandom = SecureRandom()): X509Certificate {
        val serial = ByteArray(8).also(random::nextBytes).also { it[0] = (it[0].toInt() and 0x3F or 0x01).toByte() }
        val algorithm = seq(tlv(0x06, ECDSA_SHA256))
        val name = seq(tlv(0x31, seq(tlv(0x06, COMMON_NAME), tlv(0x0C, commonName.toByteArray(Charsets.UTF_8)))))
        val tbs = seq(
            tlv(0x02, BigInteger(1, serial).toByteArray()),
            algorithm,
            name,
            seq(time(notBefore), time(notAfter)),
            name,
            keys.public.encoded,
        )
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keys.private)
            update(tbs)
            sign()
        }
        val der = seq(tbs, algorithm, tlv(0x03, byteArrayOf(0) + signature))
        return CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate
    }

    /** UTCTime before 2050, GeneralizedTime from 2050, as RFC 5280 requires. */
    private fun time(d: Date): ByteArray {
        val utc = TimeZone.getTimeZone("UTC")
        val year = java.util.Calendar.getInstance(utc).apply { time = d }.get(java.util.Calendar.YEAR)
        val (tag, pattern) = if (year < 2050) 0x17 to "yyMMddHHmmss'Z'" else 0x18 to "yyyyMMddHHmmss'Z'"
        val text = SimpleDateFormat(pattern, Locale.ROOT).apply { timeZone = utc }.format(d)
        return tlv(tag, text.toByteArray(Charsets.US_ASCII))
    }

    private fun seq(vararg parts: ByteArray): ByteArray = tlv(0x30, parts.fold(ByteArray(0)) { a, b -> a + b })

    private fun tlv(tag: Int, value: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val n = value.size
        when {
            n < 0x80 -> out.write(n)
            n < 0x100 -> { out.write(0x81); out.write(n) }
            else -> { out.write(0x82); out.write(n ushr 8); out.write(n and 0xFF) }
        }
        out.write(value)
        return out.toByteArray()
    }
}
