package com.owlynights.scalablecapitalwidgets.api

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

internal class HttpResponse(
    val code: Int,
    val body: String,
    private val headers: Map<String?, List<String>>,
) {
    val isSuccess: Boolean get() = code in 200..299

    fun header(name: String): String? = headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value?.firstOrNull()
}

internal object Http {
    private const val TIMEOUT_MS = 30_000

    fun post(url: String, contentType: String, body: String, headers: Map<String, String>): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        require(connection is HttpsURLConnection) { "Only HTTPS endpoints are allowed: $url" }
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", contentType)
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpResponse(code, text, connection.headerFields)
        } finally {
            connection.disconnect()
        }
    }

    fun formBody(fields: Map<String, String>): String = fields.entries.joinToString("&") { (k, v) ->
        URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
    }

    /**
     * Sends a DPoP-protected POST. When the server asks for a nonce (`DPoP-Nonce` header),
     * the request is retried once with that nonce, like the CLI does.
     */
    fun postWithDpop(
        url: String,
        contentType: String,
        body: String,
        dpop: DpopKey,
        accessToken: String? = null,
    ): HttpResponse {
        var nonce: String? = null
        for (attempt in 0..1) {
            val headers = buildMap {
                put("DPoP", dpop.proof("POST", url, nonce, accessToken))
                if (accessToken != null) put("Authorization", "DPoP $accessToken")
            }
            val response = post(url, contentType, body, headers)
            if (response.isSuccess) return response

            val retryNonce = response.header("DPoP-Nonce")?.trim()?.takeIf { it.isNotEmpty() }
            val lowerBody = response.body.lowercase()
            val wantsNonce = response.code == 401 ||
                "use_dpop_nonce" in lowerBody ||
                "invalid_dpop_proof" in lowerBody
            if (attempt == 0 && retryNonce != null && wantsNonce) {
                nonce = retryNonce
                continue
            }
            return response
        }
        error("unreachable")
    }
}
