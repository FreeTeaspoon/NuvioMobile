package com.nuvio.app.features.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.SurfaceTexture
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.roundToInt

private const val TAG = "NuvioMpvPlayer"
private const val MpvCacheBytes = 192 * 1024 * 1024
private const val MpvBackCacheBytes = 96 * 1024 * 1024
private const val MpvForwardCacheSeconds = 120
private const val DefaultUserAgent =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

@Composable
internal fun AndroidMpvPlayerSurface(
    sourceUrl: String,
    sourceAudioUrl: String?,
    sourceHeaders: Map<String, String>,
    initialPositionMs: Long,
    modifier: Modifier,
    playWhenReady: Boolean,
    resizeMode: PlayerResizeMode,
    onControllerReady: (PlayerEngineController) -> Unit,
    onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    onError: (String?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnSnapshot = rememberUpdatedState(onSnapshot)
    val latestOnError = rememberUpdatedState(onError)
    val sanitizedSourceHeaders = remember(sourceHeaders) {
        sanitizePlaybackHeaders(sourceHeaders)
    }
    val playerView = remember(context) { AndroidMpvPlayerView(context) }

    LaunchedEffect(playerView) {
        onControllerReady(AndroidMpvPlayerController(playerView))
    }

    LaunchedEffect(playerView) {
        while (isActive) {
            latestOnSnapshot.value(playerView.snapshot())
            delay(250L)
        }
    }

    DisposableEffect(playerView, lifecycleOwner) {
        PlayerPictureInPictureManager.registerPausePlaybackCallback {
            playerView.pause()
        }
        val activity = context.findActivity()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> playerView.resumeIfNeeded()
                Lifecycle.Event.ON_STOP -> {
                    val isInPictureInPicture =
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity?.isInPictureInPictureMode == true
                    val isFinishing = activity?.isFinishing == true
                    if (!isInPictureInPicture || isFinishing) {
                        playerView.pauseForBackground()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            PlayerPictureInPictureManager.registerPausePlaybackCallback(null)
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerView.destroyPlayer()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            playerView.apply {
                layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            }
        },
        update = { view ->
            view.onErrorChanged = latestOnError.value
            view.setResizeMode(resizeMode)
            view.setPlaybackSource(
                videoUrl = sourceUrl,
                audioUrl = sourceAudioUrl,
                requestHeaders = sanitizedSourceHeaders,
                initialPositionMs = initialPositionMs.coerceAtLeast(0L),
            )
            view.setPaused(!playWhenReady)
        },
    )
}

private class AndroidMpvPlayerController(
    private val view: AndroidMpvPlayerView,
) : PlayerEngineController {
    override fun play() {
        view.play()
    }

    override fun pause() {
        view.pause()
    }

    override fun seekTo(positionMs: Long) {
        view.seekTo(positionMs)
    }

    override fun seekBy(offsetMs: Long) {
        view.seekBy(offsetMs)
    }

    override fun retry() {
        view.retry()
    }

    override fun setPlaybackSpeed(speed: Float) {
        view.setPlaybackSpeed(speed)
    }

    override fun getAudioTracks(): List<AudioTrack> =
        view.getAudioTracks()

    override fun getSubtitleTracks(): List<SubtitleTrack> =
        view.getSubtitleTracks()

    override fun selectAudioTrack(index: Int) {
        view.selectAudioTrack(index)
    }

    override fun selectSubtitleTrack(index: Int) {
        view.selectSubtitleTrack(index)
    }

    override fun setSubtitleUri(url: String) {
        view.setSubtitleUri(url)
    }

    override fun clearExternalSubtitle() {
        view.clearExternalSubtitle()
    }

    override fun clearExternalSubtitleAndSelect(trackIndex: Int) {
        view.clearExternalSubtitleAndSelect(trackIndex)
    }

    override fun applySubtitleStyle(style: SubtitleStyleState) {
        view.applySubtitleStyle(style)
    }
}

private data class MpvPlaybackRequest(
    val videoUrl: String,
    val audioUrl: String?,
    val requestHeaders: Map<String, String>,
    val initialPositionMs: Long,
)

private data class MpvTrack(
    val index: Int,
    val id: Int,
    val title: String,
    val language: String?,
    val codec: String?,
    val isSelected: Boolean,
    val isExternal: Boolean = false,
    val isForced: Boolean = false,
)

private class AndroidMpvPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : TextureView(context, attrs, defStyleAttr),
    TextureView.SurfaceTextureListener,
    MPVLib.EventObserver,
    MPVLib.LogObserver {

    var onErrorChanged: (String?) -> Unit = {}

    private var isMpvInitialized = false
    private var surface: Surface? = null
    private var activeRequest: MpvPlaybackRequest? = null
    private var pendingRequest: MpvPlaybackRequest? = null
    private var currentRequestHeaders: Map<String, String> = emptyMap()
    private var isPaused = true
    private var resumeOnForeground = false
    private var isPlayerLoading = true
    private var hasRenderedFrameForCurrentRequest = false
    private var isSeekFramePending = false
    private var initialSeekAppliedForRequest: MpvPlaybackRequest? = null
    private var loadGeneration = 0
    private var resizeMode: PlayerResizeMode = PlayerResizeMode.Fit
    private var subtitleStyle: SubtitleStyleState = SubtitleStyleState.DEFAULT
    private var currentErrorMessage: String? = null
    private var recentPlaybackLogs: List<String> = emptyList()

    init {
        surfaceTextureListener = this
        isOpaque = false
        keepScreenOn = true
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        Log.d(TAG, "Surface texture available: ${width}x$height")
        try {
            surface = Surface(surfaceTexture)
            MPVLib.create(context.applicationContext)
            initOptions()
            MPVLib.init()
            MPVLib.attachSurface(surface!!)
            MPVLib.addObserver(this)
            MPVLib.addLogObserver(this)
            MPVLib.setPropertyString("android-surface-size", "${width}x$height")
            observeProperties()
            isMpvInitialized = true
            applyResizeMode()
            applySubtitleStyle(subtitleStyle)
            pendingRequest?.let { request ->
                pendingRequest = null
                loadRequest(request)
            }
            setPaused(isPaused)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MPV", e)
            setPlaybackError("MPV initialization failed: ${e.message}")
        }
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        if (isMpvInitialized) {
            MPVLib.setPropertyString("android-surface-size", "${width}x$height")
        }
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        pendingRequest = activeRequest
        destroyPlayer()
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        markFrameRendered()
    }

    fun setPlaybackSource(
        videoUrl: String,
        audioUrl: String?,
        requestHeaders: Map<String, String>,
        initialPositionMs: Long,
    ) {
        val request = MpvPlaybackRequest(
            videoUrl = videoUrl,
            audioUrl = audioUrl?.takeIf { it.isNotBlank() },
            requestHeaders = requestHeaders,
            initialPositionMs = initialPositionMs.coerceAtLeast(0L),
        )
        currentRequestHeaders = request.requestHeaders
        if (request == activeRequest && isMpvInitialized) return
        activeRequest = request
        if (isMpvInitialized) {
            loadRequest(request)
        } else {
            pendingRequest = request
        }
    }

    fun setPaused(paused: Boolean) {
        isPaused = paused
        if (isMpvInitialized) {
            MPVLib.setPropertyBoolean("pause", paused)
        }
    }

    fun play() {
        setPaused(false)
    }

    fun pause() {
        setPaused(true)
    }

    fun pauseForBackground() {
        resumeOnForeground = !isPaused
        if (resumeOnForeground) {
            pause()
        }
    }

    fun resumeIfNeeded() {
        if (resumeOnForeground) {
            play()
            resumeOnForeground = false
        }
    }

    fun seekTo(positionMs: Long) {
        if (!isMpvInitialized) return
        isSeekFramePending = true
        val seconds = (positionMs.coerceAtLeast(0L).toDouble() / 1000.0).formatSeconds()
        MPVLib.command(arrayOf("seek", seconds, "absolute"))
    }

    fun seekBy(offsetMs: Long) {
        if (!isMpvInitialized) return
        isSeekFramePending = true
        val seconds = (offsetMs.toDouble() / 1000.0).formatSeconds()
        MPVLib.command(arrayOf("seek", seconds, "relative"))
    }

    fun retry() {
        val request = activeRequest ?: return
        val previousPositionMs = snapshot().positionMs
        loadRequest(request)
        postDelayed({
            seekTo(previousPositionMs)
            if (!isPaused) play()
        }, 500L)
    }

    fun setPlaybackSpeed(speed: Float) {
        if (isMpvInitialized) {
            MPVLib.setPropertyDouble("speed", speed.coerceIn(0.25f, 4f).toDouble())
        }
    }

    fun setResizeMode(mode: PlayerResizeMode) {
        resizeMode = mode
        if (isMpvInitialized) {
            applyResizeMode()
        }
    }

    fun getAudioTracks(): List<AudioTrack> =
        readTracks("audio").map { track ->
            AudioTrack(
                index = track.index,
                id = track.id.toString(),
                label = track.displayLabel(),
                language = track.language,
                isSelected = track.isSelected,
            )
        }

    fun getSubtitleTracks(): List<SubtitleTrack> =
        readTracks("sub").map { track ->
            SubtitleTrack(
                index = track.index,
                id = track.id.toString(),
                label = track.displayLabel(),
                language = track.language,
                isSelected = track.isSelected,
                isForced = track.isForced,
            )
        }

    fun selectAudioTrack(index: Int) {
        if (!isMpvInitialized) return
        val trackId = readTracks("audio").firstOrNull { it.index == index }?.id ?: return
        MPVLib.setPropertyInt("aid", trackId)
    }

    fun selectSubtitleTrack(index: Int) {
        if (!isMpvInitialized) return
        if (index < 0) {
            MPVLib.setPropertyString("sid", "no")
            MPVLib.setPropertyString("sub-visibility", "no")
            logSubtitleState("selectSubtitleTrack disabled")
            return
        }
        val trackId = readTracks("sub").firstOrNull { it.index == index }?.id ?: return
        MPVLib.setPropertyString("sub-visibility", "yes")
        MPVLib.setPropertyInt("sid", trackId)
        applySubtitleStyle(subtitleStyle)
        logSubtitleState("selectSubtitleTrack index=$index trackId=$trackId")
    }

    fun setSubtitleUri(url: String) {
        if (!isMpvInitialized || url.isBlank()) return
        removeExternalSubtitleTracks()
        MPVLib.setPropertyString("sub-visibility", "yes")
        applySubtitleStyle(subtitleStyle)
        MPVLib.command(arrayOf("sub-add", url, "select"))
        logSubtitleState("setSubtitleUri command")
        postDelayed({
            if (isMpvInitialized) {
                MPVLib.setPropertyString("sub-visibility", "yes")
                applySubtitleStyle(subtitleStyle)
                logSubtitleState("setSubtitleUri delayed")
            }
        }, 300L)
    }

    fun clearExternalSubtitle() {
        if (!isMpvInitialized) return
        removeExternalSubtitleTracks()
        MPVLib.setPropertyString("sid", "no")
        MPVLib.setPropertyString("sub-visibility", "no")
        logSubtitleState("clearExternalSubtitle")
    }

    fun clearExternalSubtitleAndSelect(trackIndex: Int) {
        if (!isMpvInitialized) return
        removeExternalSubtitleTracks()
        selectSubtitleTrack(trackIndex)
    }

    fun applySubtitleStyle(style: SubtitleStyleState) {
        subtitleStyle = style
        if (!isMpvInitialized) return
        MPVLib.setPropertyString("sub-ass-override", "force")
        MPVLib.setPropertyString("sub-color", style.textColor.toMpvColorString())
        MPVLib.setPropertyString("sub-border-color", "#FF000000")
        MPVLib.setPropertyDouble("sub-border-size", if (style.outlineEnabled) 3.0 else 0.0)
        MPVLib.setPropertyInt("sub-shadow-offset", if (style.outlineEnabled) 2 else 0)
        MPVLib.setPropertyString("sub-shadow-color", "#80000000")
        MPVLib.setPropertyDouble("sub-font-size", style.toMpvSubtitleFontSize())
        MPVLib.setPropertyInt("sub-pos", style.toMpvSubtitlePosition())
        MPVLib.setPropertyString("sub-use-margins", "yes")
    }

    fun snapshot(): PlayerPlaybackSnapshot {
        if (!isMpvInitialized) {
            return PlayerPlaybackSnapshot(isLoading = true)
        }
        val durationSeconds = MPVLib.getPropertyDouble("duration/full")
            ?: MPVLib.getPropertyDouble("duration")
            ?: 0.0
        val positionSeconds = MPVLib.getPropertyDouble("time-pos") ?: 0.0
        val cachedSeconds = MPVLib.getPropertyDouble("demuxer-cache-time") ?: 0.0
        val speed = MPVLib.getPropertyDouble("speed") ?: 1.0
        val paused = MPVLib.getPropertyBoolean("pause") ?: isPaused
        val pausedForCache = MPVLib.getPropertyBoolean("paused-for-cache") ?: false
        val eofReached = MPVLib.getPropertyBoolean("eof-reached") ?: false
        val idle = MPVLib.getPropertyBoolean("core-idle") ?: false
        val seeking = MPVLib.getPropertyBoolean("seeking") ?: false
        val activelyPlaying = !paused && !pausedForCache && !idle && !eofReached
        val loading = pausedForCache ||
            seeking ||
            isSeekFramePending ||
            !hasRenderedFrameForCurrentRequest

        return PlayerPlaybackSnapshot(
            isLoading = loading,
            isPlaying = activelyPlaying,
            isEnded = eofReached,
            durationMs = (durationSeconds.coerceAtLeast(0.0) * 1000.0).toLong(),
            positionMs = (positionSeconds.coerceAtLeast(0.0) * 1000.0).toLong(),
            bufferedPositionMs = ((positionSeconds + cachedSeconds).coerceAtLeast(0.0) * 1000.0).toLong(),
            playbackSpeed = speed.toFloat().takeIf { it > 0f } ?: 1f,
        )
    }

    fun errorMessage(): String? =
        currentErrorMessage

    fun destroyPlayer() {
        if (!isMpvInitialized) return
        runCatching { MPVLib.removeObserver(this) }
        runCatching { MPVLib.removeLogObserver(this) }
        runCatching { MPVLib.detachSurface() }
        runCatching { MPVLib.destroy() }
        isMpvInitialized = false
    }

    private fun initOptions() {
        MPVLib.setOptionString("profile", "fast")
        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("hwdec", "auto-copy")
        MPVLib.setOptionString("target-colorspace-hint", "yes")
        MPVLib.setOptionString("target-prim", "auto")
        MPVLib.setOptionString("target-trc", "auto")
        MPVLib.setOptionString("tone-mapping", "auto")
        MPVLib.setOptionString("hdr-compute-peak", "auto")
        MPVLib.setOptionString("vd-lavc-o", "strict=-2")
        MPVLib.setOptionString("vd-lavc-film-grain", "cpu")
        MPVLib.setOptionString("ao", "audiotrack,opensles")

        MPVLib.setOptionString("demuxer-max-bytes", MpvCacheBytes.toString())
        MPVLib.setOptionString("demuxer-max-back-bytes", MpvBackCacheBytes.toString())
        MPVLib.setOptionString("cache", "yes")
        MPVLib.setOptionString("cache-secs", MpvForwardCacheSeconds.toString())
        MPVLib.setOptionString("network-timeout", "60")
        MPVLib.setOptionString("ytdl", "no")
        applyHttpHeadersAsOptions(currentRequestHeaders)
        MPVLib.setOptionString("tls-verify", "no")
        MPVLib.setOptionString("http-reconnect", "yes")
        MPVLib.setOptionString("stream-reconnect", "yes")
        MPVLib.setOptionString("demuxer-lavf-o", "live_start_index=0,prefer_x_start=1,http_persistent=1")
        MPVLib.setOptionString("demuxer-seekable-cache", "yes")
        MPVLib.setOptionString("force-seekable", "yes")
        applySubtitleRenderDefaults()
        MPVLib.setOptionString("osc", "no")
        MPVLib.setOptionString("osd-level", "1")
        MPVLib.setOptionString("terminal", "no")
        MPVLib.setOptionString("input-default-bindings", "no")
    }

    private fun applySubtitleRenderDefaults() {
        MPVLib.setOptionString("sub-auto", "fuzzy")
        MPVLib.setOptionString("sub-visibility", "yes")
        MPVLib.setOptionString("sub-font-size", "48")
        MPVLib.setOptionString("sub-pos", "100")
        MPVLib.setOptionString("sub-color", "#FFFFFFFF")
        MPVLib.setOptionString("sub-border-size", "3")
        MPVLib.setOptionString("sub-border-color", "#FF000000")
        MPVLib.setOptionString("sub-shadow-offset", "2")
        MPVLib.setOptionString("sub-shadow-color", "#80000000")
        MPVLib.setOptionString("osd-fonts-dir", "/system/fonts")
        MPVLib.setOptionString("sub-fonts-dir", "/system/fonts")
        MPVLib.setOptionString("sub-font", "Roboto")
        MPVLib.setOptionString("embeddedfonts", "yes")
        MPVLib.setOptionString("sub-codepage", "auto")
        MPVLib.setOptionString("blend-subtitles", "no")
        MPVLib.setOptionString("sub-use-margins", "yes")
        MPVLib.setOptionString("sub-ass-override", "force")
        MPVLib.setOptionString("sub-scale", "1.0")
        MPVLib.setOptionString("sub-fix-timing", "yes")
        MPVLib.setOptionString("sid", "auto")
    }

    private fun observeProperties() {
        val mpvFormatNone = 0
        val mpvFormatFlag = 3
        val mpvFormatInt64 = 4
        val mpvFormatDouble = 5

        MPVLib.observeProperty("time-pos", mpvFormatDouble)
        MPVLib.observeProperty("duration/full", mpvFormatDouble)
        MPVLib.observeProperty("duration", mpvFormatDouble)
        MPVLib.observeProperty("demuxer-cache-time", mpvFormatDouble)
        MPVLib.observeProperty("pause", mpvFormatFlag)
        MPVLib.observeProperty("paused-for-cache", mpvFormatFlag)
        MPVLib.observeProperty("core-idle", mpvFormatFlag)
        MPVLib.observeProperty("seeking", mpvFormatFlag)
        MPVLib.observeProperty("eof-reached", mpvFormatFlag)
        MPVLib.observeProperty("track-list", mpvFormatNone)
        MPVLib.observeProperty("track-list/count", mpvFormatInt64)
        MPVLib.observeProperty("sid", mpvFormatInt64)
        MPVLib.observeProperty("sub-visibility", mpvFormatFlag)
        MPVLib.observeProperty("sub-text", mpvFormatNone)
    }

    private fun loadRequest(request: MpvPlaybackRequest) {
        clearPlaybackError()
        isPlayerLoading = true
        hasRenderedFrameForCurrentRequest = false
        isSeekFramePending = false
        initialSeekAppliedForRequest = null
        loadGeneration++
        val generation = loadGeneration
        applyHttpHeadersAsOptions(request.requestHeaders)
        MPVLib.command(arrayOf("loadfile", request.videoUrl))
        postDelayed({
            if (
                isMpvInitialized &&
                activeRequest == request &&
                loadGeneration == generation &&
                !isPaused &&
                !hasRenderedFrameForCurrentRequest &&
                currentErrorMessage == null
            ) {
                isPlayerLoading = false
                val idle = MPVLib.getPropertyBoolean("core-idle") ?: false
                val pausedForCache = MPVLib.getPropertyBoolean("paused-for-cache") ?: false
                val seeking = MPVLib.getPropertyBoolean("seeking") ?: false
                val duration = MPVLib.getPropertyDouble("duration/full") ?: MPVLib.getPropertyDouble("duration") ?: 0.0
                val position = MPVLib.getPropertyDouble("time-pos") ?: 0.0
                setPlaybackError(
                    "MPV did not start playback. idle=$idle cache=$pausedForCache seeking=$seeking duration=$duration position=$position"
                )
            }
        }, 15_000L)
        val audioUrl = request.audioUrl
        if (!audioUrl.isNullOrBlank()) {
            postDelayed({
                if (isMpvInitialized && activeRequest == request) {
                    MPVLib.command(arrayOf("audio-add", audioUrl, "select"))
                }
            }, 200L)
        }
    }

    private fun applyInitialPositionSeekIfNeeded() {
        val request = activeRequest ?: return
        val positionMs = request.initialPositionMs
        if (positionMs <= 0L || initialSeekAppliedForRequest == request) return

        initialSeekAppliedForRequest = request
        postDelayed({
            if (isMpvInitialized && activeRequest == request) {
                seekTo(positionMs)
                if (!isPaused) {
                    MPVLib.setPropertyBoolean("pause", false)
                }
            }
        }, 100L)
    }

    private fun applyHttpHeadersAsOptions(headers: Map<String, String>) {
        val userAgentKey = headers.keys.firstOrNull { it.equals("User-Agent", ignoreCase = true) }
        val userAgent = userAgentKey?.let(headers::get) ?: DefaultUserAgent
        if (isMpvInitialized) {
            MPVLib.setPropertyString("user-agent", userAgent)
        } else {
            MPVLib.setOptionString("user-agent", userAgent)
        }

        val otherHeaders = headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) }
        val headerString = otherHeaders
            .map { (key, value) -> "$key: $value" }
            .joinToString("\n")
        if (isMpvInitialized) {
            MPVLib.setPropertyString("http-header-fields", headerString)
        } else {
            MPVLib.setOptionString("http-header-fields", headerString)
        }
    }

    private fun applyResizeMode() {
        when (resizeMode) {
            PlayerResizeMode.Fit -> {
                MPVLib.setPropertyString("video-unscaled", "no")
                MPVLib.setPropertyDouble("panscan", 0.0)
                MPVLib.setPropertyString("keepaspect", "yes")
            }
            PlayerResizeMode.Fill -> {
                MPVLib.setPropertyString("video-unscaled", "no")
                MPVLib.setPropertyDouble("panscan", 0.0)
                MPVLib.setPropertyString("keepaspect", "no")
            }
            PlayerResizeMode.Zoom -> {
                MPVLib.setPropertyString("video-unscaled", "no")
                MPVLib.setPropertyDouble("panscan", 1.0)
                MPVLib.setPropertyString("keepaspect", "yes")
            }
        }
    }

    private fun removeExternalSubtitleTracks() {
        val count = MPVLib.getPropertyInt("track-list/count") ?: 0
        for (index in count - 1 downTo 0) {
            val type = MPVLib.getPropertyString("track-list/$index/type") ?: continue
            val external = MPVLib.getPropertyBoolean("track-list/$index/external") ?: false
            if (type == "sub" && external) {
                val id = MPVLib.getPropertyInt("track-list/$index/id") ?: continue
                MPVLib.command(arrayOf("sub-remove", id.toString()))
            }
        }
    }

    private fun readTracks(typeFilter: String): List<MpvTrack> {
        if (!isMpvInitialized) return emptyList()
        val count = MPVLib.getPropertyInt("track-list/count") ?: 0
        val tracks = mutableListOf<MpvTrack>()
        var logicalIndex = 0
        for (index in 0 until count) {
            val type = MPVLib.getPropertyString("track-list/$index/type") ?: continue
            if (type != typeFilter) continue
            val id = MPVLib.getPropertyInt("track-list/$index/id") ?: continue
            val title = MPVLib.getPropertyString("track-list/$index/title").orEmpty()
            val language = MPVLib.getPropertyString("track-list/$index/lang")
                ?.takeIf { it.isNotBlank() }
            val codec = MPVLib.getPropertyString("track-list/$index/codec")
                ?.takeIf { it.isNotBlank() }
            val selected = MPVLib.getPropertyBoolean("track-list/$index/selected") ?: false
            val external = MPVLib.getPropertyBoolean("track-list/$index/external") ?: false
            val forced = type == "sub" && inferForcedSubtitleTrack(
                label = title,
                language = language,
                trackId = id.toString(),
            )
            tracks.add(
                MpvTrack(
                    index = logicalIndex,
                    id = id,
                    title = title,
                    language = language,
                    codec = codec,
                    isSelected = selected,
                    isExternal = external,
                    isForced = forced,
                )
            )
            logicalIndex++
        }
        return tracks
    }

    private fun logSubtitleState(reason: String) {
        val tracks = readTracks("sub")
        val selectedSid = MPVLib.getPropertyInt("sid")?.toString()
            ?: MPVLib.getPropertyString("sid")
            ?: "unknown"
        val visibility = MPVLib.getPropertyString("sub-visibility")
            ?: MPVLib.getPropertyBoolean("sub-visibility")?.toString()
            ?: "unknown"
        val selectedTrack = tracks.firstOrNull { it.isSelected }
        Log.d(
            TAG,
            buildString {
                append("Subtitle state [$reason]: ")
                append("sid=$selectedSid ")
                append("visibility=$visibility ")
                append("tracks=${tracks.size} ")
                append("external=${tracks.count { it.isExternal }}")
                if (selectedTrack != null) {
                    append(" selectedId=${selectedTrack.id}")
                    append(" selectedTitle=${selectedTrack.title.ifBlank { "none" }}")
                    append(" selectedLang=${selectedTrack.language ?: "none"}")
                }
            }
        )
    }

    private fun clearPlaybackError() {
        currentErrorMessage = null
        recentPlaybackLogs = emptyList()
        onErrorChanged(null)
    }

    private fun markFrameRendered() {
        if (!hasRenderedFrameForCurrentRequest) {
            hasRenderedFrameForCurrentRequest = true
        }
        isSeekFramePending = false
        if (isPlayerLoading) {
            isPlayerLoading = false
        }
        if (currentErrorMessage?.startsWith("MPV did not start playback.") == true) {
            clearPlaybackError()
        }
    }

    private fun setPlaybackError(message: String) {
        val logs = recentPlaybackLogs.takeLast(3)
        currentErrorMessage = (logs + message)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
            .ifBlank { "Unable to play this stream." }
        onErrorChanged(currentErrorMessage)
    }

    private fun appendPlaybackLog(prefix: String, level: Int, text: String) {
        val mpvLogLevelFatal = 10
        val mpvLogLevelError = 20
        val mpvLogLevelWarn = 30
        if (level != mpvLogLevelWarn && level != mpvLogLevelError && level != mpvLogLevelFatal) return
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        recentPlaybackLogs = (recentPlaybackLogs + "[$prefix] $trimmed").takeLast(4)
    }

    override fun eventProperty(property: String) {
        if (property == "track-list") {
            Log.d(TAG, "Track list changed")
        }
    }

    override fun eventProperty(property: String, value: Long) = Unit

    override fun eventProperty(property: String, value: Double) {
        if (property == "time-pos" && value > 0.0) {
            clearPlaybackError()
            return
        }
        if ((property == "duration/full" || property == "duration") && value > 0.0) {
            clearPlaybackError()
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        when (property) {
            "paused-for-cache" -> isPlayerLoading = value
            "seeking" -> {
                isPlayerLoading = value
                if (value) {
                    isSeekFramePending = true
                }
            }
            "eof-reached" -> if (value) isPlayerLoading = false
        }
    }

    override fun eventProperty(property: String, value: String) = Unit

    override fun event(eventId: Int) {
        val mpvEventEndFile = 7
        val mpvEventFileLoaded = 8
        val mpvEventLogMessage = 2
        val mpvEventVideoReconfig = 17
        val mpvEventPlaybackRestart = 21

        when (eventId) {
            mpvEventFileLoaded -> {
                clearPlaybackError()
                applyInitialPositionSeekIfNeeded()
                if (!isPaused) {
                    MPVLib.setPropertyBoolean("pause", false)
                }
            }
            mpvEventVideoReconfig,
            mpvEventPlaybackRestart -> {
                clearPlaybackError()
                if (!isPaused) {
                    MPVLib.setPropertyBoolean("pause", false)
                }
            }
            mpvEventEndFile -> {
                val duration = MPVLib.getPropertyDouble("duration/full")
                    ?: MPVLib.getPropertyDouble("duration")
                    ?: 0.0
                val eofReached = MPVLib.getPropertyBoolean("eof-reached") ?: false
                if (duration < 1.0 && !eofReached) {
                    setPlaybackError("Unable to play media. Source may be unreachable.")
                }
            }
            mpvEventLogMessage -> Unit
        }
    }

    override fun logMessage(prefix: String, level: Int, text: String) {
        appendPlaybackLog(prefix, level, text)
    }
}

private fun MpvTrack.displayLabel(): String =
    when {
        title.isNotBlank() -> title
        !language.isNullOrBlank() -> Locale(language).displayLanguage
            .takeIf { it.isNotBlank() && it != language }
            ?: language.uppercase()
        !codec.isNullOrBlank() -> codec.uppercase()
        else -> "Track ${index + 1}"
    }

private fun SubtitleStyleState.toMpvSubtitleFontSize(): Double =
    (fontSizeSp * 2.6).coerceIn(24.0, 96.0)

private fun SubtitleStyleState.toMpvSubtitlePosition(): Int =
    (100 - (bottomOffset / 2)).coerceIn(0, 150)

private fun Color.toMpvColorString(): String {
    fun component(value: Float): String =
        (value * 255f).roundToInt().coerceIn(0, 255).toString(16).padStart(2, '0').uppercase()

    return buildString {
        append('#')
        append(component(alpha))
        append(component(red))
        append(component(green))
        append(component(blue))
    }
}

private fun Double.formatSeconds(): String =
    String.format(Locale.US, "%.3f", this)

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
