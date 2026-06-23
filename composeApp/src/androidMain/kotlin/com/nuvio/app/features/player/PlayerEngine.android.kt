package com.nuvio.app.features.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.text.SpannableString
import android.util.Log
import android.util.TypedValue
import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.runBlocking
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.CaptionStyleCompat
import com.nuvio.app.R
import com.nuvio.app.features.streams.normalizeStreamType
import io.github.peerless2012.ass.media.widget.AssSubtitleView
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private const val TAG = "NuvioPlayer"
private const val PlaybackTargetBufferBytes = 192 * 1024 * 1024
private const val PlaybackMaxBufferMs = 120_000
private const val PlaybackBackBufferMs = 120_000
private const val MaxExternalSubtitleBytes = 4 * 1024 * 1024

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun AndroidMedia3PlayerSurface(
    sourceUrl: String,
    sourceAudioUrl: String?,
    sourceHeaders: Map<String, String>,
    sourceResponseHeaders: Map<String, String>,
    externalSubtitles: List<com.nuvio.app.features.streams.StreamSubtitle>,
    streamType: String?,
    sourceFilename: String?,
    sourceVideoSize: Long?,
    useYoutubeChunkedPlayback: Boolean,
    modifier: Modifier,
    playWhenReady: Boolean,
    resizeMode: PlayerResizeMode,
    initialPositionMs: Long,
    useNativeController: Boolean,
    playerControlsState: PlayerControlsState,
    onPlayerControlsAction: (PlayerControlsAction) -> Boolean,
    onPlayerControlsEvent: (String, Double) -> Boolean,
    onPlayerControlsScrubChange: (Long) -> Boolean,
    onPlayerControlsScrubFinished: (Long) -> Boolean,
    onControllerReady: (PlayerEngineController) -> Unit,
    onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    onError: (String?) -> Unit,
    onRecoverableSourceError: (String, PlayerPlaybackSnapshot) -> Boolean = { _, _ -> false },
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnSnapshot = rememberUpdatedState(onSnapshot)
    val latestOnError = rememberUpdatedState(onError)
    val latestOnRecoverableSourceError = rememberUpdatedState(onRecoverableSourceError)
    val coroutineScope = rememberCoroutineScope()

    val playerSettings = remember {
        PlayerSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.uiState.value
    }

    val sanitizedSourceHeaders = remember(sourceHeaders) {
        sanitizePlaybackHeaders(sourceHeaders)
    }
    val sanitizedSourceResponseHeaders = remember(sourceResponseHeaders) {
        sanitizePlaybackResponseHeaders(sourceResponseHeaders)
    }
    val normalizedStreamType = remember(streamType) {
        normalizeStreamType(streamType)
    }
    val sourceMimeType = remember(sourceUrl, sanitizedSourceResponseHeaders, normalizedStreamType, sourceFilename) {
        inferPlaybackMimeType(
            sourceUrl = sourceUrl,
            responseHeaders = sanitizedSourceResponseHeaders,
            streamType = normalizedStreamType,
            sourceFilename = sourceFilename,
        )
    }
    val sourceAudioMimeType = remember(sourceAudioUrl) {
        sourceAudioUrl?.let { inferPlaybackMimeType(sourceUrl = it) }
    }
    val useLibass = playerSettings.useLibass
    val libassRenderType = runCatching {
        LibassRenderType.valueOf(playerSettings.libassRenderType)
    }.getOrDefault(LibassRenderType.CUES)
    val effectiveLibassRenderType = libassRenderType.toZoomIndependentRenderType()
    val playerSourceKey = listOf(
        sourceUrl,
        sourceAudioUrl.orEmpty(),
        sanitizedSourceHeaders,
        sanitizedSourceResponseHeaders,
        normalizedStreamType.orEmpty(),
        sourceFilename.orEmpty(),
        sourceVideoSize ?: 0L,
        useYoutubeChunkedPlayback,
        externalSubtitles,
    )
    var subtitleDelayMs by remember(playerSourceKey) { mutableStateOf(0) }
    var selectedExternalSubtitleMimeType by remember(playerSourceKey) { mutableStateOf<String?>(null) }
    val latestSubtitleDelayMs = rememberUpdatedState(subtitleDelayMs)
    val latestExternalSubtitleMimeType = rememberUpdatedState(selectedExternalSubtitleMimeType)
    var decoderPriorityOverride by remember(playerSourceKey) { mutableStateOf<Int?>(null) }
    var fallbackStartPositionMs by remember(playerSourceKey) { mutableStateOf<Long?>(null) }
    val effectiveDecoderPriority = decoderPriorityOverride ?: playerSettings.decoderPriority

    val initialMediaItem = remember(playerSourceKey) {
        buildPlaybackMediaItem(
            url = sourceUrl,
            mimeType = sourceMimeType,
            externalSubtitles = externalSubtitles,
        )
    }

    var resolvedMediaItem by remember(playerSourceKey) { mutableStateOf(initialMediaItem) }
    var probeAttempted by remember(playerSourceKey) { mutableStateOf(false) }

    val extractorsFactory = remember {
        DefaultExtractorsFactory()
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
            .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE)
    }
    val dataSourceFactory = remember(
        context,
        sanitizedSourceHeaders,
        sanitizedSourceResponseHeaders,
        useYoutubeChunkedPlayback,
        externalSubtitles,
    ) {
        PlatformPlaybackDataSourceFactory.create(
            context = context,
            defaultRequestHeaders = sanitizedSourceHeaders,
            defaultResponseHeaders = sanitizedSourceResponseHeaders,
            useYoutubeChunkedPlayback = useYoutubeChunkedPlayback,
            externalSubtitles = externalSubtitles,
        )
    }

    fun ExoPlayer.setPlaybackMediaItem(videoMediaItem: MediaItem, startPositionMs: Long? = null) {
        if (!sourceAudioUrl.isNullOrBlank()) {
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
            val videoSource = mediaSourceFactory.createMediaSource(videoMediaItem)
            val audioSource = mediaSourceFactory.createMediaSource(buildPlaybackMediaItem(sourceAudioUrl, sourceAudioMimeType))
            val mergedSource = MergingMediaSource(videoSource, audioSource)
            if (startPositionMs != null) {
                setMediaSource(mergedSource, startPositionMs.coerceAtLeast(0L))
            } else {
                setMediaSource(mergedSource)
            }
        } else if (startPositionMs != null) {
            setMediaItem(videoMediaItem, startPositionMs.coerceAtLeast(0L))
        } else {
            setMediaItem(videoMediaItem)
        }
    }

    val exoPlayer = remember(
        sourceUrl,
        sourceAudioUrl,
        sanitizedSourceHeaders,
        sanitizedSourceResponseHeaders,
        sourceFilename,
        sourceVideoSize,
        sourceMimeType,
        sourceAudioMimeType,
        useLibass,
        effectiveLibassRenderType,
        useYoutubeChunkedPlayback,
        effectiveDecoderPriority,
    ) {
        val renderersFactory = SubtitleOffsetRenderersFactory(
            context = context,
            subtitleDelayUsProvider = { latestSubtitleDelayMs.value.toLong() * 1_000L },
            shouldNormalizeCuePositionProvider = {
                latestExternalSubtitleMimeType.value == MimeTypes.TEXT_VTT
            },
        )
            .setExtensionRendererMode(effectiveDecoderPriority)
            .setEnableDecoderFallback(true)
            .setMapDV7ToHevc(playerSettings.mapDV7ToHevc)

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)
            )
            if (playerSettings.tunnelingEnabled) {
                setParameters(buildUponParameters().setTunnelingEnabled(true))
            }
        }

        val loadControl = DefaultLoadControl.Builder()
            .setTargetBufferBytes(PlaybackTargetBufferBytes)
            .setBufferDurationsMs(
                15_000,
                70_000,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                5_000
            )
            .setBackBuffer(PlaybackBackBufferMs, true)
            .build()

        val player = if (useLibass) {
            ExoPlayer.Builder(context)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .buildWithAssSupportCompat(
                    context = context,
                    renderType = effectiveLibassRenderType.toAssRenderType(),
                    dataSourceFactory = dataSourceFactory,
                    extractorsFactory = extractorsFactory,
                    renderersFactory = renderersFactory
                )
        } else {
            val mediaSourceFactory = DefaultMediaSourceFactory(
                dataSourceFactory,
                extractorsFactory,
            )

            ExoPlayer.Builder(context)
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
        }

        player
    }

    LaunchedEffect(exoPlayer, resolvedMediaItem) {
        exoPlayer.setPlaybackMediaItem(resolvedMediaItem, fallbackStartPositionMs)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = playWhenReady
    }

    val pendingSubtitleTrackIndex = remember { mutableListOf<Int>() }
    val pendingAudioTrackSelection = remember { mutableListOf<TrackSelectionSnapshot>() }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var currentSubtitleStyle by remember { mutableStateOf(SubtitleStyleState.DEFAULT) }
    var currentVideoZoomState by remember { mutableStateOf(PlayerVideoZoomState()) }
    var subtitleSelectionJob by remember { mutableStateOf<Job?>(null) }
    var terminalSnapshotOverride by remember(exoPlayer) { mutableStateOf<PlayerPlaybackSnapshot?>(null) }
    var hasRenderedFirstFrame by remember(exoPlayer) { mutableStateOf(false) }
    var externalSubtitleCues by remember(playerSourceKey) { mutableStateOf<List<TimedExternalSubtitleCue>>(emptyList()) }
    var externalSubtitleLoadGeneration by remember(playerSourceKey) { mutableStateOf(0) }
    var lastExternalSubtitleText by remember(playerSourceKey) { mutableStateOf<String?>(null) }

    fun syncPlayerViewKeepScreenOn() {
        playerViewRef?.keepScreenOn = exoPlayer.shouldKeepPlayerScreenOn()
    }

    fun currentSnapshotForUi(): PlayerPlaybackSnapshot =
        terminalSnapshotOverride ?: exoPlayer.snapshot().keepLoadingUntilFirstFrame(hasRenderedFirstFrame)

    fun preserveAudioSelectionForReload(reason: String) {
        pendingAudioTrackSelection.clear()
        val selection = exoPlayer.captureSelectedTrack(C.TRACK_TYPE_AUDIO) ?: return
        pendingAudioTrackSelection.add(selection)
        Log.d(TAG, "$reason: preserving audio track index=${selection.index} id=${selection.id}")
    }

    fun clearExternalSubtitleOverlay() {
        externalSubtitleLoadGeneration++
        externalSubtitleCues = emptyList()
        lastExternalSubtitleText = null
        playerViewRef?.subtitleView?.setCues(emptyList())
    }

    fun syncExternalSubtitleOverlay(positionMs: Long) {
        if (externalSubtitleCues.isEmpty()) return
        val adjustedPositionMs = (positionMs - subtitleDelayMs).coerceAtLeast(0L)
        val cueText = externalSubtitleCues.firstOrNull { cue ->
            adjustedPositionMs in cue.startTimeMs until cue.endTimeMs
        }?.text
        if (cueText == lastExternalSubtitleText) return
        lastExternalSubtitleText = cueText
        playerViewRef?.subtitleView?.setCues(
            cueText
                ?.takeIf { it.isNotBlank() }
                ?.let { listOf(Cue.Builder().setText(it).build()) }
                ?: emptyList()
        )
    }

    DisposableEffect(exoPlayer) {
        PlayerPictureInPictureManager.registerPausePlaybackCallback {
            exoPlayer.pause()
        }

        fun reportPlayerError(error: PlaybackException) {
            Log.e(TAG, "Media3 playback error: code=${error.errorCodeName}, message=${error.message}", error)
            val message = error.toPlayerErrorMessage()
            val snapshot = exoPlayer.snapshot()
            if (shouldTreatMedia3VarintFailureAsEnded(
                    sourceUrl = sourceUrl,
                    responseHeaders = sanitizedSourceResponseHeaders,
                    sourceFilename = sourceFilename,
                    errorMessage = message,
                    snapshot = snapshot,
                )
            ) {
                val endedSnapshot = snapshot.asEndedPlaybackSnapshot()
                terminalSnapshotOverride = endedSnapshot
                latestOnError.value(null)
                latestOnSnapshot.value(endedSnapshot)
                return
            }
            if (latestOnRecoverableSourceError.value(message, snapshot)) {
                latestOnError.value(null)
                return
            }
            if (
                playerSettings.decoderPriority == DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON &&
                effectiveDecoderPriority != DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER &&
                error.isDecoderFailure()
            ) {
                Log.w(
                    TAG,
                    "Decoder failure (${error.errorCodeName}); retrying with app decoders",
                    error,
                )
                fallbackStartPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                decoderPriorityOverride = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                latestOnError.value(null)
                return
            }
            latestOnError.value(message)
        }

        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                syncPlayerViewKeepScreenOn()
                if (shouldProbePlaybackMimeType(
                        errorCode = error.errorCode,
                        causeText = error.cause?.toString(),
                        probeAttempted = probeAttempted,
                    )
                ) {
                    probeAttempted = true
                    val retryPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                    coroutineScope.launch {
                        val probedMime = withContext(Dispatchers.IO) {
                            probeMimeType(sourceUrl, sanitizedSourceHeaders)
                        }
                        if (probedMime != null) {
                            Log.d(TAG, "Playback failed with source error. Probed MIME type: $probedMime. Retrying...")
                            fallbackStartPositionMs = retryPositionMs
                            resolvedMediaItem = buildPlaybackMediaItem(
                                url = sourceUrl,
                                mimeType = probedMime,
                                externalSubtitles = externalSubtitles,
                            )
                            latestOnError.value(null)
                            return@launch
                        }
                        reportPlayerError(error)
                    }
                    return
                }

                reportPlayerError(error)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($playbackState)"
                }
                Log.d(TAG, "onPlaybackStateChanged: $stateName")
                if (playbackState == Player.STATE_READY) {
                    terminalSnapshotOverride = null
                    fallbackStartPositionMs = null
                    latestOnError.value(null)
                    exoPlayer.logCurrentTracks("STATE_READY")
                }
                latestOnSnapshot.value(currentSnapshotForUi())
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
                latestOnSnapshot.value(currentSnapshotForUi())
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                latestOnSnapshot.value(currentSnapshotForUi())
            }

            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                latestOnSnapshot.value(currentSnapshotForUi())
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                Log.d(TAG, "onTracksChanged: ${tracks.groups.size} groups total")
                exoPlayer.logCurrentTracks("onTracksChanged")
                pendingAudioTrackSelection.firstOrNull()?.let { selection ->
                    if (tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO }) {
                        pendingAudioTrackSelection.clear()
                        val restored = exoPlayer.restoreTrackSelection(selection)
                        Log.d(TAG, "onTracksChanged: restored pending audio selection=$restored")
                    }
                }
                if (pendingSubtitleTrackIndex.isNotEmpty() && tracks.groups.isNotEmpty()) {
                    val idx = pendingSubtitleTrackIndex.removeAt(0)
                    Log.d(TAG, "onTracksChanged: applying pending subtitle selection index=$idx")
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, idx < 0)
                        .build()
                    if (idx >= 0) {
                        exoPlayer.selectTrackByIndex(C.TRACK_TYPE_TEXT, idx)
                    }
                }
                latestOnSnapshot.value(currentSnapshotForUi())
            }

        }
        exoPlayer.addListener(listener)
        onDispose {
            PlayerPictureInPictureManager.registerPausePlaybackCallback(null)
            exoPlayer.removeListener(listener)
            subtitleSelectionJob?.cancel()
            clearExternalSubtitleOverlay()
            playerViewRef = null
        }
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val activity = context.findActivity()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> exoPlayer.playWhenReady = playWhenReady
                Lifecycle.Event.ON_STOP -> {
                    val isInPictureInPicture =
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity?.isInPictureInPictureMode == true
                    val isFinishing = activity?.isFinishing == true
                    if (!isInPictureInPicture || isFinishing) {
                        exoPlayer.pause()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, playWhenReady) {
        exoPlayer.playWhenReady = playWhenReady
        latestOnSnapshot.value(currentSnapshotForUi())
    }

    LaunchedEffect(exoPlayer) {
        onControllerReady(
            object : PlayerEngineController {
                override fun play() {
                    terminalSnapshotOverride = null
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                }

                override fun pause() {
                    exoPlayer.pause()
                }

                override fun seekTo(positionMs: Long) {
                    terminalSnapshotOverride = null
                    exoPlayer.seekTo(positionMs.coerceAtLeast(0L))
                }

                override fun seekBy(offsetMs: Long) {
                    terminalSnapshotOverride = null
                    exoPlayer.seekTo((exoPlayer.currentPosition + offsetMs).coerceAtLeast(0L))
                }

                override fun retry() {
                    terminalSnapshotOverride = null
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }

                override fun setPlaybackSpeed(speed: Float) {
                    exoPlayer.setPlaybackSpeed(speed)
                }

                override fun setVideoZoom(state: PlayerVideoZoomState) {
                    currentVideoZoomState = state.normalized()
                    playerViewRef?.applyVideoZoom(currentVideoZoomState)
                }

                override fun getAudioTracks(): List<AudioTrack> =
                    exoPlayer.extractAudioTracks()

                override fun getSubtitleTracks(): List<SubtitleTrack> {
                    val tracks = exoPlayer.extractSubtitleTracks()
                    Log.d(TAG, "getSubtitleTracks: found ${tracks.size} tracks")
                    tracks.forEach { t ->
                        Log.d(TAG, "  track idx=${t.index} id=${t.id} label='${t.label}' lang=${t.language} selected=${t.isSelected}")
                    }
                    return tracks
                }

                override fun selectAudioTrack(index: Int) {
                    exoPlayer.selectTrackByIndex(C.TRACK_TYPE_AUDIO, index)
                }

                override fun selectSubtitleTrack(index: Int) {
                    Log.d(TAG, "selectSubtitleTrack: index=$index")
                    clearExternalSubtitleOverlay()
                    if (index < 0) {
                        Log.d(TAG, "selectSubtitleTrack: disabling text tracks")
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                        return
                    }
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .build()
                    exoPlayer.selectTrackByIndex(C.TRACK_TYPE_TEXT, index)
                    Log.d(TAG, "selectSubtitleTrack: after selection, textDisabled=${exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)}")
                    exoPlayer.logCurrentTracks("after selectSubtitleTrack")
                }

                override fun setSubtitleUri(url: String) {
                    setSubtitleUri(url) {}
                }

                override fun setSubtitleUri(url: String, onLoaded: (Boolean) -> Unit) {
                    Log.d(TAG, "setSubtitleUri: url=$url")
                    subtitleSelectionJob?.cancel()
                    val loadGeneration = externalSubtitleLoadGeneration + 1
                    externalSubtitleLoadGeneration = loadGeneration
                    externalSubtitleCues = emptyList()
                    lastExternalSubtitleText = null
                    playerViewRef?.subtitleView?.setCues(emptyList())
                    subtitleSelectionJob = coroutineScope.launch {
                        var resolvedMime = MimeTypes.TEXT_VTT
                        val loadedCues = runCatching {
                            withContext(Dispatchers.IO) {
                                resolvedMime = resolveSubtitleMimeType(url)
                                parseExternalSubtitleCues(
                                    text = fetchExternalSubtitleText(url),
                                    sourceUrl = url,
                                    mimeType = resolvedMime,
                                )
                            }
                        }
                        if (externalSubtitleLoadGeneration != loadGeneration) {
                            return@launch
                        }
                        val cues = loadedCues.getOrNull().orEmpty()
                        if (loadedCues.isFailure || cues.isEmpty()) {
                            Log.e(TAG, "setSubtitleUri: failed to load external subtitle", loadedCues.exceptionOrNull())
                            onLoaded(false)
                            return@launch
                        }
                        selectedExternalSubtitleMimeType = resolvedMime
                        externalSubtitleCues = cues
                        lastExternalSubtitleText = null
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .build()
                        syncExternalSubtitleOverlay(exoPlayer.currentPosition)
                        Log.d(TAG, "setSubtitleUri: loaded ${cues.size} cues without media reload")
                        onLoaded(true)
                    }
                }

                override fun clearExternalSubtitle() {
                    Log.d(TAG, "clearExternalSubtitle called")
                    subtitleSelectionJob?.cancel()
                    selectedExternalSubtitleMimeType = null
                    clearExternalSubtitleOverlay()
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .build()
                    Log.d(TAG, "clearExternalSubtitle: done")
                }

                override fun clearExternalSubtitleAndSelect(trackIndex: Int) {
                    Log.d(TAG, "clearExternalSubtitleAndSelect: trackIndex=$trackIndex")
                    subtitleSelectionJob?.cancel()
                    selectedExternalSubtitleMimeType = null
                    clearExternalSubtitleOverlay()
                    selectSubtitleTrack(trackIndex)
                    Log.d(TAG, "clearExternalSubtitleAndSelect: done, selected=$trackIndex")
                }

                override fun applySubtitleStyle(style: SubtitleStyleState) {
                    currentSubtitleStyle = style
                    playerViewRef?.applySubtitleStyle(style)
                }

                override fun setSubtitleDelayMs(delayMs: Int) {
                    subtitleDelayMs = delayMs.coerceIn(SUBTITLE_DELAY_MIN_MS, SUBTITLE_DELAY_MAX_MS)
                }
            }
        )
    }

    LaunchedEffect(exoPlayer) {
        while (isActive) {
            syncExternalSubtitleOverlay(exoPlayer.currentPosition)
            latestOnSnapshot.value(currentSnapshotForUi())
            delay(250L)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = useNativeController
                layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                player = exoPlayer
                keepScreenOn = exoPlayer.shouldKeepPlayerScreenOn()
                this.resizeMode = resizeMode.toExoResizeMode()
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                playerViewRef = this
                applyVideoZoom(currentVideoZoomState)
                syncLibassOverlay(
                    player = exoPlayer,
                    enabled = useLibass,
                    renderType = effectiveLibassRenderType,
                )
                applySubtitleStyle(currentSubtitleStyle)
            }
        },
        update = { playerView ->
            playerView.player = exoPlayer
            playerView.useController = useNativeController
            playerView.resizeMode = resizeMode.toExoResizeMode()
            playerViewRef = playerView
            playerView.applyVideoZoom(currentVideoZoomState)
            playerView.syncLibassOverlay(
                player = exoPlayer,
                enabled = useLibass,
                renderType = effectiveLibassRenderType,
            )
            playerView.applySubtitleStyle(currentSubtitleStyle)
        },
    )
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun buildPlaybackMediaItem(
    url: String,
    mimeType: String?,
    externalSubtitles: List<com.nuvio.app.features.streams.StreamSubtitle> = emptyList(),
): MediaItem {
    val builder = MediaItem.Builder().setUri(url)
    if (!mimeType.isNullOrBlank()) {
        builder.setMimeType(mimeType)
    }
    if (externalSubtitles.isNotEmpty()) {
        builder.setSubtitleConfigurations(
            externalSubtitles.map { subtitle ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                    .setMimeType(resolveSubtitleMimeType(subtitle.url))
                    .setLanguage(subtitle.language)
                    .setLabel(subtitle.name ?: subtitle.language)
                    .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                    .build()
            },
        )
    }
    return builder.build()
}

private fun PlaybackException.toPlayerErrorMessage(): String {
    val fallback = runBlocking { getString(Res.string.player_unable_to_play_stream) }
    val baseMessage = localizedMessage
        ?.takeIf { it.isNotBlank() }
        ?: fallback
    val causeMessage = generateSequence(cause) { it.cause }
        .mapNotNull { it.localizedMessage?.takeIf(String::isNotBlank) }
        .firstOrNull { !it.equals(baseMessage, ignoreCase = true) }
    return if (causeMessage == null) {
        baseMessage
    } else {
        "$baseMessage: $causeMessage"
    }
}

private fun ExoPlayer.snapshot(): PlayerPlaybackSnapshot =
    PlayerPlaybackSnapshot(
        isLoading = playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING,
        isPlaying = isPlaying,
        isEnded = playbackState == Player.STATE_ENDED,
        durationMs = duration.coerceAtLeast(0L),
        positionMs = currentPosition.coerceAtLeast(0L),
        bufferedPositionMs = bufferedPosition.coerceAtLeast(0L),
        playbackSpeed = playbackParameters.speed,
    )

private fun ExoPlayer.shouldKeepPlayerScreenOn(): Boolean =
    playerError == null &&
        playWhenReady &&
        playbackState in setOf(Player.STATE_BUFFERING, Player.STATE_READY)

private fun PlayerPlaybackSnapshot.keepLoadingUntilFirstFrame(
    hasRenderedFirstFrame: Boolean,
): PlayerPlaybackSnapshot =
    if ((hasRenderedFirstFrame && durationMs > 0L) || isEnded) {
        this
    } else {
        copy(isLoading = true)
    }

private data class TrackSelectionSnapshot(
    val trackType: Int,
    val index: Int,
    val id: String?,
    val language: String?,
    val label: String?,
    val sampleMimeType: String?,
    val codecs: String?,
    val channelCount: Int,
    val roleFlags: Int,
)

private fun ExoPlayer.captureSelectedTrack(trackType: Int): TrackSelectionSnapshot? {
    var idx = 0
    for (group in currentTracks.groups) {
        if (group.type != trackType) continue
        if (group.isSelected) {
            val format = group.mediaTrackGroup.getFormat(0)
            return TrackSelectionSnapshot(
                trackType = trackType,
                index = idx,
                id = format.id,
                language = format.language,
                label = format.label,
                sampleMimeType = format.sampleMimeType,
                codecs = format.codecs,
                channelCount = format.channelCount,
                roleFlags = format.roleFlags,
            )
        }
        idx++
    }
    return null
}

private fun ExoPlayer.restoreTrackSelection(selection: TrackSelectionSnapshot): Boolean {
    selection.id?.takeIf { it.isNotBlank() }?.let { id ->
        val restored = selectTrackByPredicate(selection.trackType, "id=$id") { _, format ->
            format.id == id
        }
        if (restored) {
            return true
        }
    }

    selection.label?.takeIf { it.isNotBlank() }?.let { label ->
        val restored = selectTrackByPredicate(selection.trackType, "label=$label") { _, format ->
            format.label.equals(label, ignoreCase = true) &&
                (selection.language.isNullOrBlank() ||
                    format.language.equals(selection.language, ignoreCase = true))
        }
        if (restored) {
            return true
        }
    }

    val technicalMatchIndexes = mutableListOf<Int>()
    var idx = 0
    for (group in currentTracks.groups) {
        if (group.type != selection.trackType) continue
        val format = group.mediaTrackGroup.getFormat(0)
        if (
            !selection.language.isNullOrBlank() &&
            format.language.equals(selection.language, ignoreCase = true) &&
            format.sampleMimeType == selection.sampleMimeType &&
            format.codecs == selection.codecs &&
            format.channelCount == selection.channelCount &&
            format.roleFlags == selection.roleFlags
        ) {
            technicalMatchIndexes.add(idx)
        }
        idx++
    }
    if (technicalMatchIndexes.size == 1) {
        return selectTrackByIndex(selection.trackType, technicalMatchIndexes.first())
    }

    return selectTrackByIndex(selection.trackType, selection.index)
}

internal fun shouldProbePlaybackMimeType(
    errorCode: Int,
    causeText: String?,
    probeAttempted: Boolean,
): Boolean =
    !probeAttempted &&
        (
            errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ||
                errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                causeText?.contains("UnrecognizedInputFormatException") == true
        )

private fun PlaybackException.isDecoderFailure(): Boolean =
    errorCode in setOf(
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
    )

private fun PlayerResizeMode.toExoResizeMode(): Int =
    when (this) {
        PlayerResizeMode.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        PlayerResizeMode.Fill -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        PlayerResizeMode.Zoom -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }

private fun PlayerView.applyVideoZoom(state: PlayerVideoZoomState) {
    val normalized = state.normalized()
    val scale = java.lang.Math.pow(2.0, normalized.zoom.toDouble()).toFloat()
    allowZoomedVideoToDrawIntoGutters()
    scaleX = 1f
    scaleY = 1f
    translationX = 0f
    translationY = 0f
    resetSubtitleLayerTransforms()

    val videoSurface = videoSurfaceView ?: return
    videoSurface.applyCenteredVideoZoom(scale)
}

private fun View.applyCenteredVideoZoom(scale: Float) {
    fun applyTransform() {
        scaleX = scale
        scaleY = scale
        pivotX = width / 2f
        pivotY = height / 2f
        translationX = 0f
        translationY = 0f
    }

    if (width > 0 && height > 0) {
        applyTransform()
    } else {
        post { applyTransform() }
    }

    addOnLayoutChangeListener(
        object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int,
            ) {
                v.removeOnLayoutChangeListener(this)
                applyTransform()
            }
        }
    )
}

private fun PlayerView.resetSubtitleLayerTransforms() {
    subtitleView?.resetZoomTransform()
    findViewById<View>(R.id.libass_overlay_container)?.resetZoomTransform()
    findViewById<View>(R.id.libass_overlay_container_gl)?.resetZoomTransform()
}

private fun View.resetZoomTransform() {
    scaleX = 1f
    scaleY = 1f
    translationX = 0f
    translationY = 0f
    pivotX = width / 2f
    pivotY = height / 2f
}

private fun PlayerView.allowZoomedVideoToDrawIntoGutters() {
    disableChildClipping()
    findViewById<View>(androidx.media3.ui.R.id.exo_content_frame)?.disableChildClipping()
    videoSurfaceView?.parentChain()
        ?.forEach { parent -> parent.disableChildClipping() }
}

private fun View.disableChildClipping() {
    (this as? ViewGroup)?.let { group ->
        group.clipChildren = false
        group.clipToPadding = false
    }
}

private fun View.parentChain(): Sequence<View> =
    generateSequence(parent as? View) { view -> view.parent as? View }

private fun PlayerView.syncLibassOverlay(
    player: ExoPlayer,
    enabled: Boolean,
    renderType: LibassRenderType,
) {
    val containerId = if (renderType == LibassRenderType.OVERLAY_OPEN_GL) {
        R.id.libass_overlay_container_gl
    } else {
        R.id.libass_overlay_container
    }
    val overlayContainer = findViewById<android.widget.FrameLayout>(containerId) ?: return
    val needsOverlay = enabled && renderType.usesOverlaySubtitleView()
    val boundPlayer = getTag(R.id.libass_overlay_bound_player) as? ExoPlayer
    val hasOverlayChild = overlayContainer.hasAssOverlayChild()

    if (!needsOverlay) {
        if (hasOverlayChild) {
            overlayContainer.removeAssOverlayChildren()
        }
        if (boundPlayer != null) {
            setTag(R.id.libass_overlay_bound_player, null)
        }
        return
    }

    val assHandler = player.getAssHandlerCompat() ?: return
    if (boundPlayer === player && hasOverlayChild) {
        return
    }

    overlayContainer.removeAssOverlayChildren()
    val assSubtitleView = AssSubtitleView(overlayContainer.context, assHandler)
    overlayContainer.addView(
        assSubtitleView,
        android.widget.FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    )
    setTag(R.id.libass_overlay_bound_player, player)
}

private fun LibassRenderType.usesOverlaySubtitleView(): Boolean =
    this == LibassRenderType.OVERLAY_CANVAS || this == LibassRenderType.OVERLAY_OPEN_GL

private fun LibassRenderType.toZoomIndependentRenderType(): LibassRenderType =
    when (this) {
        LibassRenderType.EFFECTS_CANVAS -> LibassRenderType.OVERLAY_CANVAS
        LibassRenderType.EFFECTS_OPEN_GL -> LibassRenderType.OVERLAY_OPEN_GL
        else -> this
    }

private fun android.widget.FrameLayout.hasAssOverlayChild(): Boolean {
    for (index in 0 until childCount) {
        if (getChildAt(index) is AssSubtitleView) {
            return true
        }
    }
    return false
}

private fun android.widget.FrameLayout.removeAssOverlayChildren() {
    for (index in childCount - 1 downTo 0) {
        if (getChildAt(index) is AssSubtitleView) {
            removeViewAt(index)
        }
    }
}

private fun PlayerView.applySubtitleStyle(style: SubtitleStyleState) {
    subtitleView?.apply {
        val baseBottomPaddingFraction = SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION * 2f / 3f
        val offsetFraction = (style.bottomOffset / 1000f).coerceIn(0f, 0.2f)
        val bottomPaddingFraction = (baseBottomPaddingFraction + offsetFraction).coerceIn(0f, 0.4f)

        setApplyEmbeddedStyles(false)
        setApplyEmbeddedFontSizes(false)
        setBottomPaddingFraction(bottomPaddingFraction)
        setStyle(
            CaptionStyleCompat(
                style.textColor.toArgb(),
                style.backgroundColor.toArgb(),
                android.graphics.Color.TRANSPARENT,
                if (style.outlineEnabled) CaptionStyleCompat.EDGE_TYPE_OUTLINE else CaptionStyleCompat.EDGE_TYPE_NONE,
                style.outlineColor.toArgb(),
                if (style.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT,
            )
        )
        setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSizeSp.toFloat())
    }
}

private fun ExoPlayer.extractAudioTracks(): List<AudioTrack> {
    val tracks = mutableListOf<AudioTrack>()
    var idx = 0
    for (group in currentTracks.groups) {
        if (group.type != C.TRACK_TYPE_AUDIO) continue
        val format = group.mediaTrackGroup.getFormat(0)
        val channelLabel = when {
            format.channelCount == 1 -> "Mono"
            format.channelCount == 2 -> "Stereo"
            format.channelCount == 6 -> "5.1"
            format.channelCount == 8 -> "7.1"
            format.channelCount > 0 -> "${format.channelCount}ch"
            else -> null
        }
        val mime = format.sampleMimeType?.lowercase()
        val codecLabel = when {
            mime == null -> null
            mime.contains("eac3-joc") -> "Dolby Atmos"
            mime.contains("truehd") && format.channelCount >= 8 -> "Dolby Atmos"
            mime.contains("truehd") -> "Dolby TrueHD"
            mime.contains("eac3") -> "Dolby Digital Plus"
            mime.contains("ac3") -> "Dolby Digital"
            mime.contains("opus") -> "Opus"
            mime.contains("aac") -> "AAC"
            mime.contains("dts-hd") -> "DTS-HD"
            mime.contains("dts") -> "DTS"
            else -> null
        }
        val resolvedLanguage = format.language?.let { lang -> Locale(lang).displayLanguage.takeIf { name -> name.isNotBlank() && name != lang } }
        val baseName = format.label?.takeIf { it.isNotBlank() }
            ?: resolvedLanguage
            ?: format.language
            ?: runBlocking { getString(Res.string.compose_player_track_number, idx + 1) }
        val suffix = listOfNotNull(channelLabel, codecLabel)
            .joinToString(" ")
            .let { if (it.isNotBlank()) " ($it)" else "" }
        tracks.add(
            AudioTrack(
                index = idx,
                id = format.id ?: idx.toString(),
                label = "$baseName$suffix",
                language = format.language,
                isSelected = group.isSelected,
            )
        )
        idx++
    }
    return tracks
}

private fun ExoPlayer.extractSubtitleTracks(): List<SubtitleTrack> {
    val tracks = mutableListOf<SubtitleTrack>()
    var idx = 0
    for (group in currentTracks.groups) {
        if (group.type != C.TRACK_TYPE_TEXT) continue
        val format = group.mediaTrackGroup.getFormat(0)
        val hasForcedSelectionFlag = (format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0
        tracks.add(
            SubtitleTrack(
                index = idx,
                id = format.id ?: idx.toString(),
                label = format.label ?: "",
                language = format.language,
                isSelected = group.isSelected,
                isForced = inferForcedSubtitleTrack(
                    label = format.label,
                    language = format.language,
                    trackId = format.id,
                    hasForcedSelectionFlag = hasForcedSelectionFlag,
                ),
            )
        )
        idx++
    }
    return tracks
}

private fun ExoPlayer.selectTrackByIndex(trackType: Int, targetIndex: Int): Boolean {
    return selectTrackByPredicate(trackType, "index=$targetIndex") { idx, _ ->
        idx == targetIndex
    }
}

private fun ExoPlayer.selectTrackByPredicate(
    trackType: Int,
    targetDescription: String,
    predicate: (index: Int, format: Format) -> Boolean,
): Boolean {
    val typeName = if (trackType == C.TRACK_TYPE_AUDIO) "AUDIO" else "TEXT"
    Log.d(TAG, "selectTrack: type=$typeName target=$targetDescription")
    var idx = 0
    for (group in currentTracks.groups) {
        if (group.type != trackType) continue
        val format = group.mediaTrackGroup.getFormat(0)
        if (!predicate(idx, format)) {
            idx++
            continue
        }
        Log.d(TAG, "selectTrack: found group at idx=$idx, format.id=${format.id}, lang=${format.language}, label=${format.label}")
        trackSelectionParameters = trackSelectionParameters
            .buildUpon()
            .setOverrideForType(
                TrackSelectionOverride(group.mediaTrackGroup, listOf(0))
            )
            .build()
        Log.d(TAG, "selectTrack: override applied")
        return true
    }
    Log.w(TAG, "selectTrack: no group found for type=$typeName target=$targetDescription (total groups scanned=$idx)")
    return false
}

private fun ExoPlayer.logCurrentTracks(context: String) {
    Log.d(TAG, "--- logCurrentTracks ($context) ---")
    Log.d(TAG, "  textDisabled=${trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)}")
    for (group in currentTracks.groups) {
        val typeName = when (group.type) {
            C.TRACK_TYPE_AUDIO -> "AUDIO"
            C.TRACK_TYPE_TEXT -> "TEXT"
            C.TRACK_TYPE_VIDEO -> "VIDEO"
            else -> "OTHER(${group.type})"
        }
        if (group.type != C.TRACK_TYPE_TEXT && group.type != C.TRACK_TYPE_AUDIO) continue
        val format = group.mediaTrackGroup.getFormat(0)
        Log.d(TAG, "  group type=$typeName id=${format.id} lang=${format.language} label=${format.label} selected=${group.isSelected} supported=${group.isSupported}")
    }
    Log.d(TAG, "--- end logCurrentTracks ---")
}

@androidx.annotation.OptIn(UnstableApi::class)
private class SubtitleOffsetRenderersFactory(
    context: Context,
    private val subtitleDelayUsProvider: () -> Long,
    private val shouldNormalizeCuePositionProvider: () -> Boolean,
) : DefaultRenderersFactory(context) {
    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: android.os.Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        val normalizingOutput = CueNormalizingTextOutput(
            delegate = output,
            shouldNormalizeCuePositionProvider = shouldNormalizeCuePositionProvider,
        )
        val startIndex = out.size
        super.buildTextRenderers(context, normalizingOutput, outputLooper, extensionRendererMode, out)
        for (index in startIndex until out.size) {
            out[index] = SubtitleOffsetRenderer(
                baseRenderer = out[index],
                subtitleDelayUsProvider = subtitleDelayUsProvider,
            )
        }
    }
}

private class CueNormalizingTextOutput(
    private val delegate: TextOutput,
    private val shouldNormalizeCuePositionProvider: () -> Boolean,
) : TextOutput {
    override fun onCues(cueGroup: CueGroup) {
        val processed = cueGroup.cues.map(::processCue)
        delegate.onCues(CueGroup(processed, cueGroup.presentationTimeUs))
    }

    @Deprecated("Uses the deprecated Media3 callback for text outputs.")
    override fun onCues(cues: List<Cue>) {
        delegate.onCues(cues.map(::processCue))
    }

    private fun processCue(cue: Cue): Cue {
        var processed = fixRtlCueText(cue)
        if (shouldNormalizeCuePositionProvider()) {
            processed = normalizeCuePosition(processed)
        }
        return processed
    }

    private fun normalizeCuePosition(cue: Cue): Cue {
        if (cue.bitmap != null || cue.verticalType != Cue.TYPE_UNSET || cue.line == Cue.DIMEN_UNSET) {
            return cue
        }
        return cue.buildUpon()
            .setLine(Cue.DIMEN_UNSET, Cue.TYPE_UNSET)
            .setLineAnchor(Cue.TYPE_UNSET)
            .build()
    }

    private fun fixRtlCueText(cue: Cue): Cue {
        val text = cue.text ?: return cue
        if (!containsRtlChars(text)) return cue
        val original = text.toString()
        val fixed = original.split('\n').joinToString("\n") { line ->
            moveLeadingRtlPunctuationToEnd(line)
        }
        if (fixed == original) return cue
        return cue.buildUpon().setText(SpannableString(fixed)).build()
    }

    private fun moveLeadingRtlPunctuationToEnd(line: String): String {
        if (line.isEmpty()) return line
        var end = 0
        while (end < line.length && line[end] in RTL_PUNCTUATION) end++
        if (end == 0) return line
        return line.substring(end) + line.substring(0, end)
    }

    private fun containsRtlChars(text: CharSequence): Boolean {
        for (char in text) {
            val directionality = Character.getDirectionality(char)
            if (
                directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
                directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
            ) {
                return true
            }
        }
        return false
    }

    companion object {
        private val RTL_PUNCTUATION = setOf('.', ',', '?', '!', '-', ':', ';', '…', ')', '(')
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private class SubtitleOffsetRenderer(
    baseRenderer: Renderer,
    private val subtitleDelayUsProvider: () -> Long,
) : ForwardingRenderer(baseRenderer) {
    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        val adjustedPositionUs = (positionUs - subtitleDelayUsProvider()).coerceAtLeast(0L)
        super.render(adjustedPositionUs, elapsedRealtimeUs)
    }
}

private fun resolveSubtitleMimeType(url: String): String {
    probeSubtitleHeaders(url)?.let { (contentType, contentDisposition) ->
        mapSubtitleMime(contentType)?.let { return it }
        filenameFromContentDisposition(contentDisposition)?.let(::guessSubtitleMime)?.let { return it }
    }
    return guessSubtitleMime(url)
}

internal class SubtitleRequestHeaderDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val externalSubtitles: List<com.nuvio.app.features.streams.StreamSubtitle>,
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        SubtitleRequestHeaderDataSource(
            upstream = upstreamFactory.createDataSource(),
            externalSubtitles = externalSubtitles,
        )
}

private class SubtitleRequestHeaderDataSource(
    private val upstream: DataSource,
    private val externalSubtitles: List<com.nuvio.app.features.streams.StreamSubtitle>,
) : DataSource {
    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val subtitleHeaders = externalSubtitles
            .firstOrNull { it.url == dataSpec.uri.toString() }
            ?.headers
            .orEmpty()
        if (subtitleHeaders.isEmpty()) {
            return upstream.open(dataSpec)
        }

        val mergedHeaders = dataSpec.httpRequestHeaders.toMutableMap()
        subtitleHeaders.forEach { (key, value) ->
            mergedHeaders[key] = value
        }
        return upstream.open(dataSpec.buildUpon().setHttpRequestHeaders(mergedHeaders).build())
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        upstream.read(buffer, offset, length)

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        upstream.close()
    }
}

private fun probeSubtitleHeaders(url: String): Pair<String?, String?>? {
    val methods = listOf("HEAD", "GET")
    methods.forEach { method ->
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 5_000
                readTimeout = 5_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "*/*")
            }
            try {
                connection.responseCode
                connection.contentType to connection.getHeaderField("Content-Disposition")
            } finally {
                connection.disconnect()
            }
        }.getOrNull()?.let { return it }
    }
    return null
}

private fun mapSubtitleMime(contentType: String?): String? {
    val normalized = contentType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?: return null

    return when (normalized) {
        "application/x-subrip",
        "application/srt",
        "text/srt",
        "text/plain" -> MimeTypes.APPLICATION_SUBRIP
        "text/vtt",
        "application/vtt" -> MimeTypes.TEXT_VTT
        "text/x-ssa",
        "text/ssa",
        "text/ass",
        "application/x-ssa" -> MimeTypes.TEXT_SSA
        "application/ttml+xml",
        "text/xml",
        "application/xml" -> MimeTypes.APPLICATION_TTML
        else -> null
    }
}

private fun filenameFromContentDisposition(contentDisposition: String?): String? =
    contentDisposition
        ?.substringAfter("filename=", missingDelimiterValue = "")
        ?.trim()
        ?.trim('"')
        ?.takeIf { it.isNotEmpty() }

private fun guessSubtitleMime(url: String): String {
    val lower = url.lowercase()
    return when {
        lower.contains(".srt") -> MimeTypes.APPLICATION_SUBRIP
        lower.contains(".vtt") || lower.contains(".webvtt") -> MimeTypes.TEXT_VTT
        lower.contains(".ass") || lower.contains(".ssa") -> MimeTypes.TEXT_SSA
        lower.contains(".ttml") || lower.contains(".dfxp") || lower.contains(".xml") -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.TEXT_VTT
    }
}

private data class TimedExternalSubtitleCue(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
)

private fun fetchExternalSubtitleText(url: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = 15_000
        instanceFollowRedirects = true
        setRequestProperty("Accept", "*/*")
    }
    return try {
        val contentLength = connection.contentLengthLong
        if (contentLength > MaxExternalSubtitleBytes) {
            error("Subtitle file is too large: $contentLength bytes")
        }

        connection.inputStream.use { input ->
            val output = ByteArrayOutputStream(
                contentLength
                    .takeIf { it in 1..MaxExternalSubtitleBytes.toLong() }
                    ?.toInt()
                    ?: 8_192
            )
            val buffer = ByteArray(8_192)
            var totalBytes = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                totalBytes += read
                if (totalBytes > MaxExternalSubtitleBytes) {
                    error("Subtitle file exceeded $MaxExternalSubtitleBytes bytes")
                }
                output.write(buffer, 0, read)
            }
            output.toString(Charsets.UTF_8.name())
        }
    } finally {
        connection.disconnect()
    }
}

private fun parseExternalSubtitleCues(
    text: String,
    sourceUrl: String,
    mimeType: String,
): List<TimedExternalSubtitleCue> {
    val normalized = text
        .removePrefix("\uFEFF")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
    if (normalized.isBlank()) return emptyList()

    return when {
        mimeType == MimeTypes.TEXT_SSA ||
            sourceUrl.endsWith(".ass", ignoreCase = true) ||
            sourceUrl.endsWith(".ssa", ignoreCase = true) -> parseSsaExternalSubtitleCues(normalized)
        mimeType == MimeTypes.TEXT_VTT ||
            sourceUrl.endsWith(".vtt", ignoreCase = true) ||
            normalized.startsWith("WEBVTT") -> parseWebVttExternalSubtitleCues(normalized)
        else -> parseSrtExternalSubtitleCues(normalized)
    }.sortedBy { it.startTimeMs }
}

private fun parseSrtExternalSubtitleCues(text: String): List<TimedExternalSubtitleCue> =
    text.split(Regex("\n{2,}")).mapNotNull { block ->
        val lines = block.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val timingIndex = lines.indexOfFirst { it.contains("-->") }
        if (timingIndex < 0) return@mapNotNull null
        val (start, end) = parseExternalCueTiming(lines[timingIndex]) ?: return@mapNotNull null
        val body = lines.drop(timingIndex + 1)
            .joinToString("\n")
            .cleanExternalSubtitleText()
        if (body.isBlank()) null else TimedExternalSubtitleCue(start, end, body)
    }

private fun parseWebVttExternalSubtitleCues(text: String): List<TimedExternalSubtitleCue> =
    text.lines()
        .dropWhile { it.trim().isEmpty() || it.trim().startsWith("WEBVTT") }
        .joinToString("\n")
        .split(Regex("\n{2,}"))
        .mapNotNull { block ->
            val lines = block.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("NOTE") }
            val timingIndex = lines.indexOfFirst { it.contains("-->") }
            if (timingIndex < 0) return@mapNotNull null
            val (start, end) = parseExternalCueTiming(lines[timingIndex]) ?: return@mapNotNull null
            val body = lines.drop(timingIndex + 1)
                .joinToString("\n")
                .cleanExternalSubtitleText()
            if (body.isBlank()) null else TimedExternalSubtitleCue(start, end, body)
        }

private fun parseSsaExternalSubtitleCues(text: String): List<TimedExternalSubtitleCue> =
    text.lines().mapNotNull { line ->
        if (!line.startsWith("Dialogue:", ignoreCase = true)) return@mapNotNull null
        val parts = line.substringAfter(':').split(',', limit = 10)
        if (parts.size < 10) return@mapNotNull null
        val start = parseExternalSubtitleTimestamp(parts[1].trim()) ?: return@mapNotNull null
        val end = parseExternalSubtitleTimestamp(parts[2].trim()) ?: return@mapNotNull null
        val body = parts[9]
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            .replace(Regex("\\{[^}]*}"), "")
            .cleanExternalSubtitleText()
        if (body.isBlank()) null else TimedExternalSubtitleCue(start, end.coerceAtLeast(start + 1), body)
    }

private fun parseExternalCueTiming(timingLine: String): Pair<Long, Long>? {
    val startPart = timingLine.substringBefore("-->").trim()
    val endPart = timingLine.substringAfter("-->", missingDelimiterValue = "").trim()
        .substringBefore(' ')
    val start = parseExternalSubtitleTimestamp(startPart) ?: return null
    val end = parseExternalSubtitleTimestamp(endPart) ?: return null
    return start to end.coerceAtLeast(start + 1)
}

private fun parseExternalSubtitleTimestamp(raw: String): Long? {
    val cleaned = raw.substringBefore(' ').replace(',', '.')
    val parts = cleaned.split(':')
    if (parts.size !in 2..3) return null

    val secondsPart = parts.last()
    val seconds = secondsPart.substringBefore('.').toLongOrNull() ?: return null
    val millis = secondsPart.substringAfter('.', "")
        .take(3)
        .padEnd(3, '0')
        .toLongOrNull()
        ?: 0L
    val minutes = parts[parts.size - 2].toLongOrNull() ?: return null
    val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L

    return maxOf(0L, hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + millis)
}

private fun String.cleanExternalSubtitleText(): String =
    replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .lines()
        .joinToString("\n") { line -> line.replace(Regex("\\s+"), " ").trim() }
        .trim()
