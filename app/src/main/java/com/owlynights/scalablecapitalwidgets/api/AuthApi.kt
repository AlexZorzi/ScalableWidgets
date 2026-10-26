package com.owlynights.scalablecapitalwidgets.api

import org.json.JSONObject

data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String?,
    val expiresInSeconds: Long,
    val intervalSeconds: Long,
)

data class TokenSet(
    val accessToken: String,
    val refreshToken: String?,
    val idToken: String?,
    val expiresInSeconds: Long?,
)

sealed interface DevicePoll {
    data class Authorized(val tokens: TokenSet) : DevicePoll
    data object Pending : DevicePoll
    data object SlowDown : DevicePoll
}

/** OAuth 2.0 device-code flow and token refresh, as implemented by the CLI's `auth.rs`. */
object AuthApi {
    private const val FORM = "application/x-www-form-urlencoded"

    fun requestDeviceCode(dpop: DpopKey): DeviceCode {
        val response = postForm(
            ScalableConfig.DEVICE_CODE_URL,
            mapOf(
                "client_id" to ScalableConfig.CLIENT_ID,
                "audience" to ScalableConfig.AUDIENCE,
                "scope" to ScalableConfig.SCOPE,
            ),
            dpop,
        )
        if (!response.isSuccess) throw ScalableException(oauthError("Device code request", response))
        val json = JSONObject(response.body)
        return DeviceCode(
            deviceCode = json.getString("device_code"),
            userCode = json.getString("user_code"),
            verificationUri = json.getString("verification_uri"),
            verificationUriComplete = json.optString("verification_uri_complete").takeIf { it.isNotBlank() },
            expiresInSeconds = json.optLong("expires_in", 600),
            intervalSeconds = json.optLong("interval", 5).coerceAtLeast(1),
        )
    }

    fun pollDeviceToken(deviceCode: String, dpop: DpopKey): DevicePoll {
        val response = postForm(
            ScalableConfig.TOKEN_URL,
            mapOf(
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                "device_code" to deviceCode,
                "client_id" to ScalableConfig.CLIENT_ID,
            ),
            dpop,
        )
        if (response.isSuccess) return DevicePoll.Authorized(parseTokens(response.body))
        return when (errorCode(response.body)) {
            "authorization_pending" -> DevicePoll.Pending
            "slow_down" -> DevicePoll.SlowDown
            "access_denied" -> throw LoginFailedException("Login was denied")
            "expired_token" -> throw LoginFailedException("The login code expired")
            else -> throw LoginFailedException(oauthError("Login", response))
        }
    }

    fun refresh(refreshToken: String, sessionId: String?, dpop: DpopKey): TokenSet {
        val form = buildMap {
            put("grant_type", "refresh_token")
            put("client_id", ScalableConfig.CLIENT_ID)
            put("refresh_token", refreshToken)
            sessionId?.trim()?.takeIf { it.isNotEmpty() }?.let { put("session_id", it) }
        }
        val response = postForm(ScalableConfig.TOKEN_URL, form, dpop)
        if (!response.isSuccess) {
            if (response.code == 429) {
                throw RateLimitedException(response.header("Retry-After")?.trim()?.toLongOrNull())
            }
            val code = errorCode(response.body)
            if (code == "invalid_grant" || response.code in 400..499) {
                throw ReloginRequiredException(oauthError("Token refresh", response))
            }
            throw ScalableException(oauthError("Token refresh", response))
        }
        val tokens = parseTokens(response.body)
        if (tokens.refreshToken.isNullOrBlank()) {
            throw ReloginRequiredException("Token refresh returned no replacement refresh token")
        }
        return tokens
    }

    fun revokeRefreshToken(refreshToken: String, dpop: DpopKey) {
        postForm(
            ScalableConfig.REVOKE_URL,
            mapOf("token" to refreshToken, "client_id" to ScalableConfig.CLIENT_ID),
            dpop,
        )
    }

    private fun postForm(url: String, fields: Map<String, String>, dpop: DpopKey): HttpResponse =
        Http.postWithDpop(url, FORM, Http.formBody(fields), dpop)

    private fun parseTokens(body: String): TokenSet {
        val json = JSONObject(body)
        return TokenSet(
            accessToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
            idToken = json.optString("id_token").takeIf { it.isNotBlank() },
            expiresInSeconds = if (json.has("expires_in")) json.optLong("expires_in") else null,
        )
    }

    private fun errorCode(body: String): String? =
        runCatching { JSONObject(body).optString("error") }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun oauthError(action: String, response: HttpResponse): String {
        val json = runCatching { JSONObject(response.body) }.getOrNull()
        val code = json?.optString("error")?.takeIf { it.isNotBlank() } ?: "unknown_error"
        val description = json?.optString("error_description")?.takeIf { it.isNotBlank() }
        val lowerBody = response.body.lowercase()
        val hint = if (response.code == 401 && "dpop" in lowerBody) {
            " DPoP validation failed; check that the device clock is in sync."
        } else ""
        return buildString {
            append("$action failed (HTTP ${response.code} - $code)")
            if (description != null) append(": $description")
            append(hint)
        }
    }
}
