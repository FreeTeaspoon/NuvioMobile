package com.nuvio.app.features.player

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.nuvio.app.core.network.IPv4FirstDns
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okio.ByteString.Companion.encodeUtf8
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

internal object PlayerPlaybackNetworking {
    private val DEFAULT_STREAM_HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "*/*",
        "Accept-Encoding" to "identity",
        "Connection" to "keep-alive",
    )

    internal const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val playbackHostnameVerifier = HostnameVerifier { _, _ -> true }

    private val sslContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        }
    }

    private val playbackHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(IPv4FirstDns())
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url
                val hasUserInfo = url.username.isNotBlank() || url.password.isNotBlank()
                val hasAuthorization = request.header("Authorization") != null
                if (hasUserInfo && !hasAuthorization) {
                    chain.proceed(
                        request.newBuilder()
                            .header("Authorization", Credentials.basic(url.username, url.password))
                            .build()
                    )
                } else {
                    chain.proceed(request)
                }
            }
            .authenticator(WebDavAuthenticator)
            .sslSocketFactory(sslContext.socketFactory, trustAllManager)
            .hostnameVerifier(playbackHostnameVerifier)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun createHttpDataSourceFactory(defaultHeaders: Map<String, String> = emptyMap()): DataSource.Factory {
        val mergedHeaders = DEFAULT_STREAM_HEADERS + defaultHeaders
        return OkHttpDataSource.Factory(playbackHttpClient).apply {
            setDefaultRequestProperties(mergedHeaders)
            setUserAgent(DEFAULT_USER_AGENT)
        }
    }

    fun createDataSourceFactory(
        context: Context,
        defaultHeaders: Map<String, String> = emptyMap(),
    ): DataSource.Factory {
        return DefaultDataSource.Factory(context, createHttpDataSourceFactory(defaultHeaders))
    }

    fun openConnection(
        url: String,
        headers: Map<String, String>,
        method: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        range: String? = null,
    ): HttpURLConnection {
        val mergedHeaders = DEFAULT_STREAM_HEADERS + headers
        val parsedUrl = URL(url)
        return (parsedUrl.openConnection() as HttpURLConnection).apply {
            if (this is HttpsURLConnection) {
                sslSocketFactory = sslContext.socketFactory
                hostnameVerifier = playbackHostnameVerifier
            }
            instanceFollowRedirects = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            requestMethod = method
            setRequestProperty("User-Agent", mergedHeaders["User-Agent"] ?: DEFAULT_USER_AGENT)
            mergedHeaders.forEach { (key, value) ->
                if (key.equals("Range", ignoreCase = true)) return@forEach
                if (key.equals("User-Agent", ignoreCase = true)) return@forEach
                setRequestProperty(key, value)
            }
            if (mergedHeaders.keys.none { it.equals("Authorization", ignoreCase = true) }) {
                parsedUrl.userInfo
                    ?.takeIf { it.isNotBlank() }
                    ?.let { userInfo ->
                        val username = userInfo.substringBefore(':')
                        val password = userInfo.substringAfter(':', missingDelimiterValue = "")
                        setRequestProperty("Authorization", Credentials.basic(username, password))
                    }
            }
            range?.let { setRequestProperty("Range", it) }
        }
    }
}

private object WebDavAuthenticator : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("Authorization") != null) return null

        val username = response.request.url.username.takeIf { it.isNotBlank() } ?: return null
        val password = response.request.url.password
        val challenge = response.headers("WWW-Authenticate")
            .firstOrNull { it.startsWith("Digest", ignoreCase = true) }
            ?: return response.request.newBuilder()
                .header("Authorization", Credentials.basic(username, password))
                .build()

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
