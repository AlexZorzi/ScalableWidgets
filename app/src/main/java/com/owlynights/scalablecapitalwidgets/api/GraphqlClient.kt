package com.owlynights.scalablecapitalwidgets.api

import org.json.JSONArray
import org.json.JSONObject

/** DPoP-bound GraphQL transport, mirroring the CLI's `graphql.rs`. */
class GraphqlClient(private val dpop: DpopKey) {

    fun execute(
        accessToken: String,
        query: String,
        variables: JSONObject,
        operationName: String,
    ): JSONObject {
        // The CLI serializes its request struct with serde defaults, so the field is snake_case.
        val body = JSONObject()
            .put("query", query)
            .put("variables", variables)
            .put("operation_name", operationName)
        val response = Http.postWithDpop(
            ScalableConfig.GRAPHQL_URL,
            "application/json",
            body.toString(),
            dpop,
            accessToken,
        )

        if (!response.isSuccess) {
            when (response.code) {
                429 -> throw RateLimitedException(response.header("Retry-After")?.trim()?.toLongOrNull())
                401 -> throw UnauthorizedException("GraphQL HTTP error 401 during $operationName")
            }
            val code = runCatching { JSONObject(response.body).optString("error") }.getOrNull()
            throw ScalableException(
                "GraphQL HTTP error ${response.code} during $operationName" +
                    (code?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
            )
        }

        val parsed = JSONObject(response.body)
        val errors = parsed.optJSONArray("errors")
        if (errors != null && errors.length() > 0) {
            throw ScalableException(graphqlErrorMessage(operationName, errors))
        }
        return parsed.optJSONObject("data")
            ?: throw ScalableException("GraphQL response for $operationName is missing data")
    }

    private fun graphqlErrorMessage(operationName: String, errors: JSONArray): String {
        val first = errors.optJSONObject(0)
        val code = first?.optJSONObject("extensions")?.optString("code")?.takeIf { it.isNotBlank() }
        val message = first?.optString("message")?.takeIf { it.isNotBlank() }
        return buildString {
            append("GraphQL returned errors for $operationName")
            if (code != null) append(" (code: $code)")
            if (message != null) append(": $message")
        }
    }
}
