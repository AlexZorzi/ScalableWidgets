package com.owlynights.scalablecapitalwidgets.api

/**
 * Production endpoints of the official Scalable CLI
 * (https://github.com/ScalableCapital/scalable-cli, src/channel.rs).
 *
 * The app talks to the same API the CLI uses, so "Agentic Investing" has to be
 * enabled on the Scalable web platform (Profile → Security → Agentic Investing).
 */
object ScalableConfig {
    const val GRAPHQL_URL = "https://de.scalable.capital/api/cli/graphql"
    const val ISSUER = "https://secure.scalable.capital"
    const val AUDIENCE = "https://de.scalable.capital/api-gateway"
    const val CLIENT_ID = "yBM3BrpRgwSTJZRdJllvtD6jJEmyxWfE"
    const val SCOPE = "offline_access openid email"

    const val DEVICE_CODE_URL = "$ISSUER/oauth/device/code"
    const val TOKEN_URL = "$ISSUER/oauth/token"
    const val REVOKE_URL = "$ISSUER/oauth/revoke"

    /** Refresh the access token when it expires within this window. */
    const val REFRESH_SKEW_SECONDS = 60L

    /** Scalable ends CLI sessions seven days after the original login. */
    const val MAX_SESSION_SECONDS = 7L * 24 * 60 * 60

    const val QUOTE_SOURCE = "CONSOLIDATED"
}
