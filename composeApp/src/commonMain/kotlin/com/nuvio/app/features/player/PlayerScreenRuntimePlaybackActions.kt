package com.nuvio.app.features.player

import co.touchlab.kermit.Logger
import com.nuvio.app.features.tmdb.TmdbService
import com.nuvio.app.features.tracking.TrackingMediaReference
import com.nuvio.app.features.tracking.TrackingScrobbleAction
import com.nuvio.app.features.tracking.TrackingScrobbleCoordinator
import com.nuvio.app.features.tracking.TrackingScrobbleEvent
import com.nuvio.app.features.tracking.buildTrackingMediaReference
import com.nuvio.app.features.watchprogress.WatchProgressClock
import com.nuvio.app.features.watchprogress.WatchProgressPlaybackSession
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.buildPlaybackVideoId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val playbackSideEffectLog = Logger.withTag("PlayerRuntime")

private inline fun runPlaybackSideEffectSafely(
    operation: String,
    block: () -> Unit,
) {
    try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        playbackSideEffectLog.e(error) { "Playback side effect failed: $operation" }
    }
}

private suspend inline fun runPlaybackSuspendSideEffectSafely(
    operation: String,
    crossinline block: suspend () -> Unit,
) {
    try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        playbackSideEffectLog.e(error) { "Playback side effect failed: $operation" }
    }
}

internal fun PlayerScreenRuntime.applyVideoZoomState(state: PlayerVideoZoomState) {
    val normalized = state.normalized()
    videoZoom = normalized.zoom
    playerController?.setVideoZoom(normalized)
}

internal fun PlayerScreenRuntime.openVideoZoomModal() {
    showVideoZoomModal = true
    showAudioModal = false
    showSubtitleModal = false
    showSpeedModal = false
    showVideoSettingsModal = false
    controlsVisible = true
}

internal fun PlayerScreenRuntime.resetVideoZoom() {
    applyVideoZoomState(videoZoomState.copy(zoom = 0f))
}

internal fun PlayerScreenRuntime.setVideoZoomAsDefault() {
    RememberedVideoZoomRepository.saveZoom(videoZoomContentKey, videoZoomState.zoom)
}

internal val PlayerScreenRuntime.activePlaybackIdentity: String
    get() = activeTorrentInfoHash
        ?.let { hash -> "torrent:$hash:${activeTorrentFileIdx ?: -1}" }
        ?: activeSourceUrl

internal val PlayerScreenRuntime.playbackSession: WatchProgressPlaybackSession
    get() = WatchProgressPlaybackSession(
        profileId = profileId,
        contentType = contentType ?: parentMetaType,
        parentMetaId = parentMetaId,
        parentMetaType = parentMetaType,
        videoId = activeVideoId?.takeIf { it.isNotBlank() } ?: buildPlaybackVideoId(
            parentMetaId = parentMetaId,
            seasonNumber = activeSeasonNumber,
            episodeNumber = activeEpisodeNumber,
            fallbackVideoId = activeVideoId,
        ),
        title = title,
        logo = logo,
        poster = poster,
        background = background,
        seasonNumber = activeSeasonNumber,
        episodeNumber = activeEpisodeNumber,
        episodeTitle = activeEpisodeTitle,
        episodeThumbnail = activeEpisodeThumbnail,
        providerName = activeProviderName,
        providerAddonId = activeProviderAddonId,
        lastStreamTitle = activeStreamTitle,
        lastStreamSubtitle = activeStreamSubtitle,
        pauseDescription = activePauseDescription,
        lastSourceUrl = activeSourceUrl,
    )

internal fun PlayerScreenRuntime.resetIdentityStateIfNeeded() {
    val identity = activePlaybackIdentity
    if (lastResetPlaybackIdentity != identity) {
        lastResetPlaybackIdentity = identity
        shouldPlay = true
        initialLoadCompleted = false
        speedBoostRestoreSpeed = null
        isHoldToSpeedGestureActive = false
        liveGestureFeedback = null
        liveHorizontalSeekTargetMs = null
        initialSeekApplied = activeInitialPositionMs <= 0L &&
            (activeInitialProgressFraction == null || activeInitialProgressFraction!! <= 0f)
        lastProgressPersistEpochMs = 0L
        previousIsPlaying = false
        pendingSeekScrobbleRestart = false
        autoFetchedAddonSubtitlesForKey = null
        trackPreferenceRestoreApplied = false
        preferredAudioSelectionApplied = false
        appliedAudioPreferences = null
        preferredSubtitleSelectionApplied = false
        isUserExplicitAudioSelection = false
        isUserExplicitSubtitleSelection = false
        hasScannedTextTracksOnce = false
    }

    val videoIdentity = "$identity:$activeVideoId:$activeSeasonNumber:$activeEpisodeNumber"
    if (lastResetVideoIdentity != videoIdentity) {
        lastResetVideoIdentity = videoIdentity
        liveGestureFeedback = null
        liveHorizontalSeekTargetMs = null
        hasRequestedScrobbleStartForCurrentItem = false
        scrobbleStartRequestGeneration = 0L
        pendingSeekScrobbleRestart = false
        hasSentCompletionScrobbleForCurrentItem = false
        currentTrackingMedia = null
    }

    val zoomContentKey = videoZoomContentKey
    if (lastResetVideoZoomContentKey != zoomContentKey) {
        lastResetVideoZoomContentKey = zoomContentKey
        videoZoom = RememberedVideoZoomRepository.zoomFor(zoomContentKey) ?: 0f
    }
}

internal fun PlayerScreenRuntime.currentPlaybackProgressPercent(
    snapshot: PlayerPlaybackSnapshot = playbackSnapshot,
): Float {
    val duration = snapshot.durationMs.takeIf { it > 0L } ?: return 0f
    return ((snapshot.positionMs.toFloat() / duration.toFloat()) * 100f)
        .coerceIn(0f, 100f)
}

internal data class TrackingScrobbleItemInputs(
    val contentType: String,
    val parentMetaId: String,
    val videoId: String?,
    val title: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val episodeTitle: String?,
)

internal fun PlayerScreenRuntime.snapshotTrackingScrobbleItemInputs() = TrackingScrobbleItemInputs(
    contentType = contentType ?: parentMetaType,
    parentMetaId = parentMetaId,
    videoId = activeVideoId,
    title = title,
    seasonNumber = activeSeasonNumber,
    episodeNumber = activeEpisodeNumber,
    episodeTitle = activeEpisodeTitle,
)

private fun TrackingScrobbleItemInputs.buildMedia(): TrackingMediaReference =
    buildTrackingMediaReference(
        contentType = contentType,
        parentMetaId = parentMetaId,
        videoId = videoId,
        title = title,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        episodeTitle = episodeTitle,
    )

internal fun PlayerScreenRuntime.currentTrackingMedia(): TrackingMediaReference =
    snapshotTrackingScrobbleItemInputs().buildMedia()

internal fun PlayerScreenRuntime.emitTrackingScrobbleStart() {
    if (hasRequestedScrobbleStartForCurrentItem) return
    hasRequestedScrobbleStartForCurrentItem = true
    val requestGeneration = scrobbleStartRequestGeneration + 1L
    scrobbleStartRequestGeneration = requestGeneration

    scope.launch {
        try {
            val media = currentTrackingMedia()
            if (!media.hasResolvableIdentity) {
                hasRequestedScrobbleStartForCurrentItem = false
                return@launch
            }
            if (requestGeneration != scrobbleStartRequestGeneration || !hasRequestedScrobbleStartForCurrentItem) {
                return@launch
            }
            currentTrackingMedia = media
            TrackingScrobbleCoordinator.scrobble(
                profileId = profileId,
                action = TrackingScrobbleAction.START,
                event = TrackingScrobbleEvent(
                    media = media,
                    progressPercent = currentPlaybackProgressPercent().toDouble(),
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (requestGeneration == scrobbleStartRequestGeneration) {
                hasRequestedScrobbleStartForCurrentItem = false
            }
            playbackSideEffectLog.e(error) { "Tracking scrobble start failed" }
        }
    }
}

internal fun PlayerScreenRuntime.emitTrackingScrobblePause(progressPercent: Float? = null) {
    emitTrackingScrobbleTerminal(
        action = TrackingScrobbleAction.PAUSE,
        progressPercent = progressPercent,
    )
}

internal fun PlayerScreenRuntime.emitTrackingScrobbleStop(progressPercent: Float? = null) {
    emitTrackingScrobbleTerminal(
        action = TrackingScrobbleAction.STOP,
        progressPercent = progressPercent,
    )
}

private fun PlayerScreenRuntime.emitTrackingScrobbleTerminal(
    action: TrackingScrobbleAction,
    progressPercent: Float?,
) {
    val provided = progressPercent
    if (!hasRequestedScrobbleStartForCurrentItem && (provided ?: 0f) < 80f) return

    val percent = provided ?: currentPlaybackProgressPercent()
    val mediaSnapshot = currentTrackingMedia
    val inputsSnapshot = snapshotTrackingScrobbleItemInputs()
    scope.launch(NonCancellable) {
        val media = mediaSnapshot ?: inputsSnapshot.buildMedia()
        if (!media.hasResolvableIdentity) return@launch
        runPlaybackSuspendSideEffectSafely("tracking scrobble ${action.wireValue}") {
            TrackingScrobbleCoordinator.scrobble(
                profileId = profileId,
                action = action,
                event = TrackingScrobbleEvent(media = media, progressPercent = percent.toDouble()),
            )
        }
    }
    currentTrackingMedia = null
    hasRequestedScrobbleStartForCurrentItem = false
    pendingSeekScrobbleRestart = false
    scrobbleStartRequestGeneration += 1L
}

internal fun PlayerScreenRuntime.emitStopScrobbleForCurrentProgress() {
    val progressPercent = currentPlaybackProgressPercent()
    if (!shouldSendStopScrobble(hasRequestedScrobbleStartForCurrentItem, progressPercent)) {
        return
    }
    if (progressPercent < 80f) {
        emitTrackingScrobbleStop(progressPercent)
        return
    }

    if (progressPercent >= 80f && !hasSentCompletionScrobbleForCurrentItem) {
        hasSentCompletionScrobbleForCurrentItem = true
        emitTrackingScrobbleStop(progressPercent)
    }
}

internal fun shouldSendStopScrobble(
    hasActiveScrobble: Boolean,
    progressPercent: Float,
): Boolean = hasActiveScrobble || progressPercent >= 80f

internal fun shouldUpdateTrackingScrobbleAfterSeek(
    hasActiveScrobble: Boolean,
    progressPercent: Float,
): Boolean = hasActiveScrobble && progressPercent >= 1f && progressPercent < 80f

internal fun PlayerScreenRuntime.emitTrackingSeekScrobbleStart() {
    val mediaSnapshot = currentTrackingMedia
    val inputsSnapshot = snapshotTrackingScrobbleItemInputs()
    scope.launch {
        val media = mediaSnapshot ?: inputsSnapshot.buildMedia()
        if (!media.hasResolvableIdentity) return@launch
        runPlaybackSuspendSideEffectSafely("tracking seek start") {
            TrackingScrobbleCoordinator.scrobbleSeek(
                profileId = profileId,
                action = TrackingScrobbleAction.START,
                event = TrackingScrobbleEvent(
                    media = media,
                    progressPercent = currentPlaybackProgressPercent().toDouble(),
                ),
            )
        }
    }
}

internal fun PlayerScreenRuntime.tryShowParentalGuide() {
    if (!playerSettingsUiState.showParentalGuide) return
    if (!parentalGuideHasShown && parentalWarnings.isNotEmpty() && !playbackStartedForParentalGuide) {
        playbackStartedForParentalGuide = true
        showParentalGuide = true
        parentalGuideHasShown = true
    }
}

internal suspend fun PlayerScreenRuntime.resolveParentalGuideImdbId(): String? {
    val candidates = listOf(parentMetaId, activeVideoId)
    candidates.firstNotNullOfOrNull(::extractParentalGuideImdbId)?.let { return it }
    val tmdbId = candidates.firstNotNullOfOrNull(::extractParentalGuideTmdbId) ?: return null
    return TmdbService.tmdbToImdb(
        tmdbId = tmdbId,
        mediaType = contentType ?: parentMetaType,
    )
}

internal fun PlayerScreenRuntime.flushWatchProgress(
    scrobbleAction: TrackingScrobbleAction = TrackingScrobbleAction.STOP,
) {
    runPlaybackSideEffectSafely("tracking scrobble ${scrobbleAction.wireValue}") {
        when (scrobbleAction) {
            TrackingScrobbleAction.PAUSE -> emitTrackingScrobblePause()
            TrackingScrobbleAction.STOP -> emitStopScrobbleForCurrentProgress()
            TrackingScrobbleAction.START -> Unit
        }
    }
    runPlaybackSideEffectSafely("watch progress flush") {
        WatchProgressRepository.flushPlaybackProgress(
            session = playbackSession,
            snapshot = playbackSnapshot,
        )
    }
}

internal fun PlayerScreenRuntime.scheduleProgressSyncAfterSeek() {
    val shouldRestartScrobbleAfterSeek = shouldPlay || playbackSnapshot.isPlaying
    seekProgressSyncJob?.cancel()
    seekProgressSyncJob = scope.launch {
        delay(PlayerSeekProgressSyncDebounceMs)
        runPlaybackSideEffectSafely("seek progress sync") {
            WatchProgressRepository.upsertPlaybackProgress(
                session = playbackSession,
                snapshot = playbackSnapshot,
            )
        }

        val progressPercent = currentPlaybackProgressPercent()
        if (
            !shouldUpdateTrackingScrobbleAfterSeek(
                hasActiveScrobble = hasRequestedScrobbleStartForCurrentItem,
                progressPercent = progressPercent,
            )
        ) {
            return@launch
        }

        val media = currentTrackingMedia ?: currentTrackingMedia()
        if (!media.hasResolvableIdentity) return@launch
        val stopEvent = TrackingScrobbleEvent(
            media = media,
            progressPercent = progressPercent.toDouble(),
        )
        scope.launch {
            runPlaybackSuspendSideEffectSafely("tracking seek stop") {
                TrackingScrobbleCoordinator.scrobbleSeek(
                    profileId = profileId,
                    action = TrackingScrobbleAction.STOP,
                    event = stopEvent,
                )
            }
            if (!shouldRestartScrobbleAfterSeek || !shouldPlay || playbackSnapshot.isEnded) return@launch
            if (playbackSnapshot.isPlaying) {
                pendingSeekScrobbleRestart = false
                runPlaybackSuspendSideEffectSafely("tracking seek start") {
                    TrackingScrobbleCoordinator.scrobbleSeek(
                        profileId = profileId,
                        action = TrackingScrobbleAction.START,
                        event = stopEvent.copy(
                            progressPercent = currentPlaybackProgressPercent().toDouble(),
                        ),
                    )
                }
            } else {
                pendingSeekScrobbleRestart = true
            }
        }
    }
}

internal fun PlayerScreenRuntime.persistPlaybackProgressTick() {
    val now = WatchProgressClock.nowEpochMs()
    if (now - lastProgressPersistEpochMs < PlaybackProgressPersistIntervalMs) return
    lastProgressPersistEpochMs = now
    runPlaybackSideEffectSafely("playback progress tick") {
        WatchProgressRepository.upsertPlaybackProgress(
            session = playbackSession,
            snapshot = playbackSnapshot,
            syncRemote = false,
        )
    }
}
