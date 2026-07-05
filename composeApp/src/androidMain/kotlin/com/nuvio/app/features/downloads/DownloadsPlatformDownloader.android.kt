package com.nuvio.app.features.downloads

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import nuvio.composeapp.generated.resources.*
import okio.ByteString.Companion.encodeUtf8
import org.jetbrains.compose.resources.getString
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

private val downloadHttpClient = OkHttpClient.Builder()
    .addInterceptor { chain ->
        val request = chain.request()
        val url = request.url
        val hasUserInfo = url.username.isNotBlank() || url.password.isNotBlank()
        val hasAuthorization = request.header("Authorization") != null
        if (hasUserInfo && !hasAuthorization) {
            chain.proceed(
                request.newBuilder()
                    .header("Authorization", Credentials.basic(url.username, url.password))
                    .build(),
            )
        } else {
            chain.proceed(request)
        }
    }
    .authenticator(DownloadWebDavAuthenticator)
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .retryOnConnectionFailure(true)
    .build()

internal actual object DownloadsPlatformDownloader {
    private var appContext: Context? = null
    private val foregroundLock = Any()
    private val activeForegroundTokens = mutableSetOf<Int>()
    private var nextForegroundToken = 0

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    actual fun start(
        request: DownloadPlatformRequest,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
        onSuccess: (localFileUri: String, totalBytes: Long?) -> Unit,
        onFailure: (message: String) -> Unit,
    ): DownloadsTaskHandle {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        var call: Call? = null
        var foregroundToken: Int? = null

        scope.launch {
            val context = appContext
            if (context == null) {
                onFailure(runBlocking { getString(Res.string.downloads_error_not_initialized) })
                return@launch
            }
            foregroundToken = registerForegroundDownload(context)

            val downloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }
            val destination = File(downloadsDir, request.destinationFileName)
            val tempFile = File(downloadsDir, "${request.destinationFileName}.part")

            try {
                var resumeFromBytes = tempFile.takeIf { it.exists() }?.length()?.coerceAtLeast(0L) ?: 0L

                fun buildRequest(rangeStart: Long?): Request {
                    val requestBuilder = Request.Builder().url(request.sourceUrl)
                    request.sourceHeaders.forEach { (key, value) ->
                        requestBuilder.header(key, value)
                    }
                    if (rangeStart != null && rangeStart > 0L) {
                        requestBuilder.header("Range", "bytes=$rangeStart-")
                    }
                    return requestBuilder.get().build()
                }

                var attemptedRangeRequest = resumeFromBytes > 0L
                var httpRequest = buildRequest(if (attemptedRangeRequest) resumeFromBytes else null)
                call = downloadHttpClient.newCall(httpRequest)
                var response = call?.execute() ?: error(
                    runBlocking { getString(Res.string.downloads_error_request_failed) },
                )

                if (attemptedRangeRequest && response.code == 416) {
                    response.close()
                    tempFile.delete()
                    resumeFromBytes = 0L
                    attemptedRangeRequest = false
                    httpRequest = buildRequest(null)
                    call = downloadHttpClient.newCall(httpRequest)
                    response = call?.execute() ?: error(
                        runBlocking { getString(Res.string.downloads_error_request_failed) },
                    )
                }

                response.use { response ->
                    if (!response.isSuccessful) {
                        error(
                            runBlocking {
                                getString(Res.string.downloads_error_http_failed, response.code)
                            },
                        )
                    }

                    val isPartialResume = attemptedRangeRequest && response.code == 206 && resumeFromBytes > 0L
                    val appendToTemp = isPartialResume
                    val startingBytes = if (appendToTemp) resumeFromBytes else 0L

                    if (!appendToTemp && tempFile.exists()) {
                        tempFile.delete()
                    }

                    val body = response.body ?: error(
                        runBlocking { getString(Res.string.downloads_error_empty_body) },
                    )
                    val totalBytes = resolveTotalBytes(
                        startingBytes = startingBytes,
                        isPartialResume = isPartialResume,
                        contentRangeHeader = response.header("Content-Range"),
                        contentLength = body.contentLength().takeIf { it > 0L },
                    )
                    var downloadedBytes = startingBytes
                    onProgress(downloadedBytes, totalBytes)

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile, appendToTemp).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                downloadedBytes += read.toLong()
                                onProgress(downloadedBytes, totalBytes)
                            }
                            output.flush()
                        }
                    }

                    if (destination.exists()) {
                        destination.delete()
                    }
                    if (!tempFile.renameTo(destination)) {
                        tempFile.copyTo(destination, overwrite = true)
                        tempFile.delete()
                    }

                    val finalSize = destination.length()
                    onSuccess(destination.toURI().toString(), totalBytes ?: finalSize)
                }
            } catch (error: Throwable) {
                onFailure(error.message ?: runBlocking { getString(Res.string.download_failed) })
            }
        }

        job.invokeOnCompletion {
            call?.cancel()
            foregroundToken?.let(::unregisterForegroundDownload)
        }

        return AndroidDownloadsTaskHandle(job)
    }

    actual fun removeFile(localFileUri: String?): Boolean {
        if (localFileUri.isNullOrBlank()) return false
        val file = localFileUri.toLocalFileOrNull() ?: return false
        return runCatching { file.delete() }.getOrDefault(false)
    }

    actual fun removePartialFile(destinationFileName: String): Boolean {
        val context = appContext ?: return false
        val downloadsDir = File(context.filesDir, "downloads")
        val tempFile = File(downloadsDir, "$destinationFileName.part")
        if (!tempFile.exists()) return true
        return runCatching { tempFile.delete() }.getOrDefault(false)
    }

    actual fun resolveLocalFileUri(localFileUri: String?, destinationFileName: String): String? {
        localFileUri
            ?.toLocalFileOrNull()
            ?.takeIf { it.exists() }
            ?.let { return it.toURI().toString() }

        val context = appContext ?: return null
        val fileName = destinationFileName.trim().takeIf { it.isNotBlank() }
            ?: localFileUri
                ?.toLocalFileOrNull()
                ?.name
                ?.takeIf { it.isNotBlank() }
            ?: return null
        val downloadsDir = File(context.filesDir, "downloads")
        val localFile = File(downloadsDir, fileName)
        return localFile.takeIf { it.exists() }?.toURI()?.toString()
    }

    private fun registerForegroundDownload(context: Context): Int =
        synchronized(foregroundLock) {
            nextForegroundToken += 1
            val token = nextForegroundToken
            activeForegroundTokens += token
            DownloadsForegroundService.start(context)
            token
        }

    private fun unregisterForegroundDownload(token: Int) {
        val shouldStop = synchronized(foregroundLock) {
            activeForegroundTokens.remove(token)
            activeForegroundTokens.isEmpty()
        }
        if (shouldStop) {
            appContext?.let(DownloadsForegroundService::stop)
        }
    }

    actual fun openDownloadsDirectory(): Boolean {
        val context = appContext ?: return false
        val downloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }
        val uri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                downloadsDir,
            )
        }.getOrNull() ?: return false

        val intents = listOf(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
            },
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "vnd.android.document/directory")
            },
            Intent(Intent.ACTION_VIEW).apply {
                data = uri
            },
        )

        return intents.any { intent ->
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)

            runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }
    }
}

private class AndroidDownloadsTaskHandle(
    private val job: Job,
) : DownloadsTaskHandle {
    override fun cancel() {
        job.cancel()
    }
}

private fun String.toLocalFileOrNull(): File? {
    return runCatching {
        if (startsWith("file:")) {
            File(URI(this))
        } else {
            File(this)
        }
    }.getOrNull()
}

private fun resolveTotalBytes(
    startingBytes: Long,
    isPartialResume: Boolean,
    contentRangeHeader: String?,
    contentLength: Long?,
): Long? {
    parseContentRangeTotal(contentRangeHeader)?.let { return it }
    val normalizedLength = contentLength?.takeIf { it > 0L } ?: return null
    return if (isPartialResume && startingBytes > 0L) {
        startingBytes + normalizedLength
    } else {
        normalizedLength
    }
}

private fun parseContentRangeTotal(headerValue: String?): Long? {
    val value = headerValue?.trim().orEmpty()
    if (value.isBlank()) return null
    val slashIndex = value.lastIndexOf('/')
    if (slashIndex == -1 || slashIndex == value.lastIndex) return null
    val totalPart = value.substring(slashIndex + 1).trim()
    if (totalPart == "*") return null
    return totalPart.toLongOrNull()?.takeIf { it > 0L }
}

private object DownloadWebDavAuthenticator : Authenticator {
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
