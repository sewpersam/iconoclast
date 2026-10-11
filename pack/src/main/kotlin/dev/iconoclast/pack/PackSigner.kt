package dev.iconoclast.pack

import com.android.apksig.ApkSigner
import com.android.apksig.KeyConfig
import java.io.File

/** Signs an aligned, unsigned pack with APK Signature Schemes v2 and v3, spec 3.5. */
object PackSigner {
    /** Reads [unsigned] from disk and writes the signed pack to [out]; neither is held in memory whole. */
    fun sign(unsigned: File, key: SigningKey, out: File) {
        val signer = ApkSigner.SignerConfig.Builder("pack", KeyConfig.Jca(key.privateKey), listOf(key.certificate)).build()
        val tmp = File(out.parentFile, out.name + ".tmp")
        tmp.delete()
        ApkSigner.Builder(listOf(signer))
            .setInputApk(unsigned)
            .setOutputApk(tmp)
            .setV1SigningEnabled(false)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            // ZipRewriter already aligned every STORED entry; keep its layout.
            .setAlignmentPreserved(true)
            .build()
            .sign()
        check(tmp.renameTo(out)) { "could not write $out" }
    }
}
