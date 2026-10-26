package com.owlynights.scalablecapitalwidgets.api

open class ScalableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The session can no longer be refreshed; the user has to sign in again. */
class ReloginRequiredException(message: String, cause: Throwable? = null) :
    ScalableException(message, cause)

/** The backend rejected the access token (HTTP 401) for a GraphQL call. */
class UnauthorizedException(message: String) : ScalableException(message)

class RateLimitedException(val retryAfterSeconds: Long?) :
    ScalableException(
        if (retryAfterSeconds != null) "Rate limited; retry after ${retryAfterSeconds}s"
        else "Rate limited; retry later"
    )

class LoginFailedException(message: String) : ScalableException(message)
