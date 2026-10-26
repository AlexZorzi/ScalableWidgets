package com.owlynights.scalablecapitalwidgets.data

import org.json.JSONObject

data class Session(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long?,
    val personId: String,
    val dpopThumbprint: String,
    /** Epoch seconds of the interactive login; sessions end seven days later. */
    val loginAt: Long,
    /** Set when the refresh token was rejected and only a new login can help. */
    val needsRelogin: Boolean = false,
) {
    fun toJson(): String = JSONObject()
        .put("accessToken", accessToken)
        .put("refreshToken", refreshToken)
        .put("expiresAt", expiresAt)
        .put("personId", personId)
        .put("dpopThumbprint", dpopThumbprint)
        .put("loginAt", loginAt)
        .put("needsRelogin", needsRelogin)
        .toString()

    companion object {
        fun fromJson(raw: String): Session? = runCatching {
            val json = JSONObject(raw)
            Session(
                accessToken = json.getString("accessToken"),
                refreshToken = json.optString("refreshToken").takeIf { it.isNotBlank() && it != "null" },
                expiresAt = if (json.isNull("expiresAt")) null else json.optLong("expiresAt"),
                personId = json.getString("personId"),
                dpopThumbprint = json.getString("dpopThumbprint"),
                loginAt = json.getLong("loginAt"),
                needsRelogin = json.optBoolean("needsRelogin"),
            )
        }.getOrNull()
    }
}

sealed interface AuthState {
    data object SignedOut : AuthState
    data class SignedIn(val personId: String, val loginAt: Long) : AuthState
    data class Expired(val personId: String) : AuthState
}

sealed interface LoginStage {
    data object WaitingForBrowser : LoginStage
    data object WaitingForSecondFactor : LoginStage
    data object LoadingPortfolio : LoginStage
}
