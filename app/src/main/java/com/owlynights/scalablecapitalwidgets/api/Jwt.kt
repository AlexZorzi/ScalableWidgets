package com.owlynights.scalablecapitalwidgets.api

import android.util.Base64
import org.json.JSONObject

internal object Base64Url {
    private const val FLAGS = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

    fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, FLAGS)

    fun decode(value: String): ByteArray = Base64.decode(value, FLAGS)
}

/**
 * Reads claims from tokens issued to us over TLS by the Scalable issuer.
 * Signature verification is left to the backend, which validates every call.
 */
data class AccessTokenClaims(
    val personId: String,
    val sessionId: String?,
    val expiresAt: Long?,
) {
    companion object {
        private val PERSON_ID_CLAIMS = listOf(
            "https://de.scalable.capital/person_id",
            "https://de.scalable.capital/personId",
            "person_id",
        )
        private val SESSION_ID_CLAIMS = listOf(
            "https://de.scalable.capital/session_id",
            "session_id",
        )

        fun parse(token: String): AccessTokenClaims {
            val parts = token.split('.')
            if (parts.size < 2) throw ScalableException("Access token is not a JWT")
            val claims = JSONObject(String(Base64Url.decode(parts[1]), Charsets.UTF_8))
            val personId = PERSON_ID_CLAIMS.firstNotNullOfOrNull { claims.optNonBlank(it) }
                ?: throw ScalableException("Access token does not contain a person_id claim")
            return AccessTokenClaims(
                personId = personId,
                sessionId = SESSION_ID_CLAIMS.firstNotNullOfOrNull { claims.optNonBlank(it) },
                expiresAt = if (claims.has("exp")) claims.optLong("exp") else null,
            )
        }

        private fun JSONObject.optNonBlank(key: String): String? =
            if (has(key) && !isNull(key)) optString(key).trim().takeIf { it.isNotEmpty() } else null
    }
}
