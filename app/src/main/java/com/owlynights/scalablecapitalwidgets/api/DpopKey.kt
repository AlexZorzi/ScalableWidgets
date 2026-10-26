package com.owlynights.scalablecapitalwidgets.api

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.math.BigInteger
import java.net.URI
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * EC P-256 key used to sign DPoP proofs (RFC 9449), mirroring the CLI's `dpop` module.
 * The private key lives in the Android Keystore and never leaves it.
 */
class DpopKey private constructor(
    private val privateKey: PrivateKey,
    publicKey: ECPublicKey,
) {
    private val x = Base64Url.encode(publicKey.w.affineX.toUnsigned32())
    private val y = Base64Url.encode(publicKey.w.affineY.toUnsigned32())

    val publicJwk: JSONObject
        get() = JSONObject()
            .put("kty", "EC")
            .put("crv", "P-256")
            .put("x", x)
            .put("y", y)

    /** RFC 7638 thumbprint, used to check that a stored session belongs to this key. */
    val thumbprint: String by lazy {
        val canonical = """{"crv":"P-256","kty":"EC","x":"$x","y":"$y"}"""
        Base64Url.encode(sha256(canonical.toByteArray(Charsets.UTF_8)))
    }

    fun proof(method: String, url: String, nonce: String? = null, accessToken: String? = null): String {
        val header = JSONObject()
            .put("typ", "dpop+jwt")
            .put("alg", "ES256")
            .put("jwk", publicJwk)
        val claims = JSONObject()
            .put("htm", method.uppercase())
            .put("htu", canonicalHtu(url))
            .put("iat", System.currentTimeMillis() / 1000)
            .put("jti", randomJti())
        nonce?.trim()?.takeIf { it.isNotEmpty() }?.let { claims.put("nonce", it) }
        accessToken?.trim()?.takeIf { it.isNotEmpty() }?.let {
            claims.put("ath", Base64Url.encode(sha256(it.toByteArray(Charsets.UTF_8))))
        }

        val signingInput = Base64Url.encode(header.toString().toByteArray(Charsets.UTF_8)) +
            "." + Base64Url.encode(claims.toString().toByteArray(Charsets.UTF_8))
        val der = Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(signingInput.toByteArray(Charsets.US_ASCII))
            sign()
        }
        return signingInput + "." + Base64Url.encode(derToRaw(der))
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "scalable_dpop_p256"

        /** Creates a fresh key, replacing any previous one. Used at login. */
        fun create(): DpopKey {
            delete()
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
            generator.initialize(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
            )
            val pair = generator.generateKeyPair()
            return DpopKey(pair.private, pair.public as ECPublicKey)
        }

        fun loadExisting(): DpopKey? {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            val entry = keyStore.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return null
            val publicKey = entry.certificate.publicKey as? ECPublicKey ?: return null
            return DpopKey(entry.privateKey, publicKey)
        }

        fun delete() {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
        }

        private fun canonicalHtu(url: String): String {
            val uri = URI(url)
            return URI(uri.scheme, uri.authority, uri.path, null, null).toString()
        }

        private fun randomJti(): String =
            Base64Url.encode(ByteArray(16).also { SecureRandom().nextBytes(it) })

        private fun sha256(bytes: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(bytes)

        private fun BigInteger.toUnsigned32(): ByteArray {
            val bytes = toByteArray()
            return when {
                bytes.size == 32 -> bytes
                bytes.size > 32 -> bytes.copyOfRange(bytes.size - 32, bytes.size)
                else -> ByteArray(32 - bytes.size) + bytes
            }
        }

        /** Converts an ASN.1 DER ECDSA signature into the 64-byte R||S form JWS expects. */
        private fun derToRaw(der: ByteArray): ByteArray {
            var offset = 2
            if (der[1].toInt() and 0x80 != 0) offset += der[1].toInt() and 0x7f
            fun readInt(): ByteArray {
                require(der[offset].toInt() == 0x02) { "Invalid DER signature" }
                val length = der[offset + 1].toInt()
                val value = der.copyOfRange(offset + 2, offset + 2 + length)
                offset += 2 + length
                return BigInteger(1, value).toUnsigned32()
            }
            return readInt() + readInt()
        }
    }
}
