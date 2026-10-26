package com.owlynights.scalablecapitalwidgets.data

import android.content.Context
import com.owlynights.scalablecapitalwidgets.api.AccessTokenClaims
import com.owlynights.scalablecapitalwidgets.api.AuthApi
import com.owlynights.scalablecapitalwidgets.api.DeviceCode
import com.owlynights.scalablecapitalwidgets.api.DevicePoll
import com.owlynights.scalablecapitalwidgets.api.DpopKey
import com.owlynights.scalablecapitalwidgets.api.GraphqlClient
import com.owlynights.scalablecapitalwidgets.api.LoginFailedException
import com.owlynights.scalablecapitalwidgets.api.Queries
import com.owlynights.scalablecapitalwidgets.api.ReloginRequiredException
import com.owlynights.scalablecapitalwidgets.api.ScalableConfig
import com.owlynights.scalablecapitalwidgets.api.UnauthorizedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PendingLogin internal constructor(
    internal val key: DpopKey,
    val deviceCode: DeviceCode,
) {
    /** Link that pre-fills the user code, falling back to the plain verification page. */
    val loginUrl: String get() = deviceCode.verificationUriComplete ?: deviceCode.verificationUri
}

/**
 * Owns the OAuth session: device-code login with the trusted-device 2FA step,
 * proactive token refresh and logout. All network work runs on [Dispatchers.IO].
 */
class SessionManager(context: Context) {
    private val store = SecureStore(context)
    private val refreshMutex = Mutex()

    private val _state = MutableStateFlow(stateOf(loadSession()))
    val state: StateFlow<AuthState> = _state.asStateFlow()

    suspend fun beginLogin(): PendingLogin = withContext(Dispatchers.IO) {
        val key = DpopKey.create()
        PendingLogin(key, AuthApi.requestDeviceCode(key))
    }

    /** Polls until the browser login is confirmed, then completes 2FA and stores the session. */
    suspend fun completeLogin(pending: PendingLogin, onStage: (LoginStage) -> Unit) =
        withContext(Dispatchers.IO) {
            val device = pending.deviceCode
            val deadline = System.currentTimeMillis() + device.expiresInSeconds * 1000
            var interval = device.intervalSeconds
            onStage(LoginStage.WaitingForBrowser)
            while (true) {
                if (System.currentTimeMillis() >= deadline) {
                    throw LoginFailedException("The login code expired. Please try again.")
                }
                delay(interval * 1000)
                when (val poll = AuthApi.pollDeviceToken(device.deviceCode, pending.key)) {
                    DevicePoll.Pending -> Unit
                    DevicePoll.SlowDown -> interval += 2
                    is DevicePoll.Authorized -> {
                        val tokens = poll.tokens
                        val claims = AccessTokenClaims.parse(tokens.accessToken)
                        val now = nowSeconds()
                        val session = Session(
                            accessToken = tokens.accessToken,
                            refreshToken = tokens.refreshToken,
                            expiresAt = tokens.expiresInSeconds?.let { now + it } ?: claims.expiresAt,
                            personId = claims.personId,
                            dpopThumbprint = pending.key.thumbprint,
                            loginAt = now,
                        )
                        try {
                            secondFactor(GraphqlClient(pending.key), session, onStage)
                        } catch (e: Exception) {
                            revokeQuietly(session, pending.key)
                            throw LoginFailedException("2FA verification on your trusted device failed.")
                        }
                        saveSession(session)
                        onStage(LoginStage.LoadingPortfolio)
                        return@withContext
                    }
                }
            }
        }

    private suspend fun secondFactor(client: GraphqlClient, session: Session, onStage: (LoginStage) -> Unit) {
        val token = session.accessToken
        val userId = session.personId
        val stateData = client.execute(
            token,
            Queries.IS_2FA_ON_LOGIN_ENABLED,
            JSONObject().put("input", JSONObject().put("userId", userId)),
            "Is2faOnLoginEnabled",
        ).optJSONObject("is2faOnLoginEnabled")
        if (stateData?.optBoolean("enabled") != true) return
        if (stateData.optBoolean("hasApprovedSession")) return

        val mfaSessionId = client.execute(
            token,
            Queries.START_2FA_ON_LOGIN,
            JSONObject().put(
                "input",
                JSONObject().put("userId", userId).put("deviceName", "CLI").put("deviceType", "CLI"),
            ),
            "Start2faOnLogin",
        ).optJSONObject("start2faOnLogin")?.optString("mfaSessionId")?.takeIf { it.isNotBlank() }
            ?: throw LoginFailedException("2FA is enabled but no MFA session was returned")

        onStage(LoginStage.WaitingForSecondFactor)
        val deadline = System.currentTimeMillis() + 120_000
        while (System.currentTimeMillis() < deadline) {
            val status = client.execute(
                token,
                Queries.VALIDATE_2FA_ON_LOGIN,
                JSONObject().put(
                    "input",
                    JSONObject().put("userId", userId).put("mfaSessionId", mfaSessionId),
                ),
                "Validate2faOnLogin",
            ).optJSONObject("validate2faOnLogin")?.optString("status")
            when (status) {
                "SUCCESS" -> return
                "PENDING" -> delay(2_000)
                else -> throw LoginFailedException("2FA status: $status")
            }
        }
        throw LoginFailedException("2FA login challenge timed out")
    }

    /**
     * Runs [block] with a valid access token. Refreshes proactively and once more if the
     * backend answers 401, like the CLI's `execute_with_refresh_retry`.
     */
    suspend fun <T> withAuth(block: (GraphqlClient, String) -> T): T = withContext(Dispatchers.IO) {
        val (client, token) = validToken(force = false)
        try {
            block(client, token)
        } catch (e: UnauthorizedException) {
            val (retryClient, retryToken) = validToken(force = true)
            block(retryClient, retryToken)
        }
    }

    private suspend fun validToken(force: Boolean): Pair<GraphqlClient, String> = refreshMutex.withLock {
        val session = loadSession() ?: throw ReloginRequiredException("Not signed in")
        if (session.needsRelogin) throw ReloginRequiredException("Session expired")
        val key = DpopKey.loadExisting()
        if (key == null || key.thumbprint != session.dpopThumbprint) {
            markNeedsRelogin(session)
            throw ReloginRequiredException("The DPoP signing key changed; sign in again")
        }
        val now = nowSeconds()
        if (now >= session.loginAt + ScalableConfig.MAX_SESSION_SECONDS) {
            markNeedsRelogin(session)
            throw ReloginRequiredException("Scalable sessions last at most seven days; sign in again")
        }
        val expiring = session.expiresAt?.let { it <= now + ScalableConfig.REFRESH_SKEW_SECONDS } ?: false
        if (!force && !expiring) return GraphqlClient(key) to session.accessToken

        val refreshToken = session.refreshToken
        if (refreshToken == null) {
            markNeedsRelogin(session)
            throw ReloginRequiredException("No refresh token available; sign in again")
        }
        val sessionId = runCatching { AccessTokenClaims.parse(session.accessToken).sessionId }.getOrNull()
        val tokens = try {
            AuthApi.refresh(refreshToken, sessionId, key)
        } catch (e: ReloginRequiredException) {
            markNeedsRelogin(session)
            throw e
        }
        val claims = AccessTokenClaims.parse(tokens.accessToken)
        if (claims.personId != session.personId) {
            markNeedsRelogin(session)
            throw ReloginRequiredException("Refreshed token belongs to another person")
        }
        val refreshed = session.copy(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresAt = tokens.expiresInSeconds?.let { nowSeconds() + it } ?: claims.expiresAt,
        )
        saveSession(refreshed)
        GraphqlClient(key) to refreshed.accessToken
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val session = loadSession()
        val key = DpopKey.loadExisting()
        if (session != null && key != null) revokeQuietly(session, key)
        store.put(SESSION, null)
        DpopKey.delete()
        _state.value = AuthState.SignedOut
    }

    private fun revokeQuietly(session: Session, key: DpopKey) {
        session.refreshToken?.let { runCatching { AuthApi.revokeRefreshToken(it, key) } }
        runCatching {
            GraphqlClient(key).execute(
                session.accessToken,
                Queries.REVOKE_ACCESS_TOKEN,
                JSONObject().put("input", JSONObject().put("accessToken", session.accessToken)),
                "revokeAuthAccessToken",
            )
        }
    }

    fun currentSession(): Session? = loadSession()

    private fun markNeedsRelogin(session: Session) = saveSession(session.copy(needsRelogin = true))

    private fun saveSession(session: Session) {
        store.put(SESSION, session.toJson())
        _state.value = stateOf(session)
    }

    private fun loadSession(): Session? = store.get(SESSION)?.let(Session::fromJson)

    private fun stateOf(session: Session?): AuthState = when {
        session == null -> AuthState.SignedOut
        session.needsRelogin -> AuthState.Expired(session.personId)
        else -> AuthState.SignedIn(session.personId, session.loginAt)
    }

    private fun nowSeconds() = System.currentTimeMillis() / 1000

    private companion object {
        const val SESSION = "session"
    }
}
