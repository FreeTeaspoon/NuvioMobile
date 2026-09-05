package com.nuvio.app.features.downloads

import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okio.ByteString.Companion.encodeUtf8

internal object DownloadWebDavAuthenticator : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val username = response.request.url.username.takeIf { it.isNotBlank() } ?: return null
        val password = response.request.url.password
        val challenge = response.headers("WWW-Authenticate")
            .firstOrNull { it.startsWith("Digest", ignoreCase = true) }
        val previousAuthorization = response.request.header("Authorization")
        if (challenge == null) {
            if (previousAuthorization != null) return null
            return response.request.newBuilder()
                .header("Authorization", Credentials.basic(username, password))
                .build()
        }
        if (previousAuthorization?.startsWith("Digest", ignoreCase = true) == true) return null

        val authorization = buildDigestAuthorization(
            challenge = challenge,
            method = response.request.method,
            uri = response.request.url.encodedPath.let { path ->
                val query = response.request.url.encodedQuery
                if (query.isNullOrBlank()) path else "$path?$query"
            },
            username = username,
            password = password,
        ) ?: return null

        return response.request.newBuilder()
            .header("Authorization", authorization)
            .build()
    }
}

private fun buildDigestAuthorization(
    challenge: String,
    method: String,
    uri: String,
    username: String,
    password: String,
): String? {
    val params = parseDigestChallenge(challenge)
    val realm = params["realm"] ?: return null
    val nonce = params["nonce"] ?: return null
    val qop = params["qop"]
        ?.split(',')
        ?.map { it.trim() }
        ?.firstOrNull { it.equals("auth", ignoreCase = true) }
    val algorithm = params["algorithm"]?.uppercase().orEmpty().ifBlank { "MD5" }
    if (algorithm != "MD5") return null

    val cnonce = "${System.nanoTime()}".encodeUtf8().md5().hex()
    val nc = "00000001"
    val ha1 = "$username:$realm:$password".md5Hex()
    val ha2 = "$method:$uri".md5Hex()
    val responseDigest = if (qop == null) {
        "$ha1:$nonce:$ha2".md5Hex()
    } else {
        "$ha1:$nonce:$nc:$cnonce:$qop:$ha2".md5Hex()
    }

    return buildString {
        append("Digest ")
        appendDigestParam("username", username)
        append(", ")
        appendDigestParam("realm", realm)
        append(", ")
        appendDigestParam("nonce", nonce)
        append(", ")
        appendDigestParam("uri", uri)
        append(", ")
        appendDigestParam("response", responseDigest)
        params["opaque"]?.let {
            append(", ")
            appendDigestParam("opaque", it)
        }
        append(", algorithm=MD5")
        if (qop != null) {
            append(", qop=$qop")
            append(", nc=$nc")
            append(", ")
            appendDigestParam("cnonce", cnonce)
        }
    }
}

private fun parseDigestChallenge(challenge: String): Map<String, String> {
    val value = challenge.removePrefix("Digest").trim()
    val params = linkedMapOf<String, String>()
    var index = 0
    while (index < value.length) {
        while (index < value.length && (value[index] == ',' || value[index].isWhitespace())) index++
        val keyStart = index
        while (index < value.length && value[index] != '=') index++
        if (index >= value.length) break
        val key = value.substring(keyStart, index).trim().lowercase()
        index++
        val parsedValue = if (index < value.length && value[index] == '"') {
            index++
            val start = index
            while (index < value.length && value[index] != '"') index++
            value.substring(start, index).also {
                if (index < value.length) index++
            }
        } else {
            val start = index
            while (index < value.length && value[index] != ',') index++
            value.substring(start, index).trim()
        }
        if (key.isNotBlank() && parsedValue.isNotBlank()) {
            params[key] = parsedValue
        }
    }
    return params
}

private fun String.md5Hex(): String = encodeUtf8().md5().hex()

private fun StringBuilder.appendDigestParam(name: String, value: String) {
    append(name)
    append("=\"")
    append(value.replace("\\", "\\\\").replace("\"", "\\\""))
    append('"')
}
