package com.nuvio.app.features.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import com.nuvio.app.R

@Composable
actual fun PlatformSystemMediaControls(
    title: String?,
    subtitle: String?,
    controller: PlayerEngineController?,
    snapshot: PlayerPlaybackSnapshot,
    enabled: Boolean,
) {
    val context = LocalContext.current
    val session = remember(context) {
        AndroidPlayerSystemMediaControls(context.applicationContext)
    }
    val latestController = rememberUpdatedState(controller)

    DisposableEffect(session) {
        session.setControllerProvider { latestController.value }
        onDispose {
            session.release()
        }
    }

    LaunchedEffect(session, title, subtitle, controller, snapshot, enabled) {
        session.update(
            title = title,
            subtitle = subtitle,
            snapshot = snapshot,
            enabled = enabled && controller != null,
        )
    }
}

class PlayerMediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AndroidPlayerSystemMediaControls.dispatchAction(intent.action)
    }
}

private class AndroidPlayerSystemMediaControls(
    private val context: Context,
) {
    private val notificationManager = NotificationManagerCompat.from(context)
    private var controllerProvider: () -> PlayerEngineController? = { null }
    private var notificationSignature: String? = null

    private val session = MediaSession(context, "NuvioPlayer").apply {
        setSessionActivity(context.launchPendingIntent())
        setCallback(
            object : MediaSession.Callback() {
                override fun onPlay() {
                    controllerProvider()?.play()
                }

                override fun onPause() {
                    controllerProvider()?.pause()
                }

                override fun onSeekTo(pos: Long) {
                    controllerProvider()?.seekTo(pos)
                }

                override fun onFastForward() {
                    controllerProvider()?.seekBy(10_000L)
                }

                override fun onRewind() {
                    controllerProvider()?.seekBy(-10_000L)
                }
            }
        )
    }

    fun setControllerProvider(provider: () -> PlayerEngineController?) {
        controllerProvider = provider
        activeControls = this
    }

    fun update(
        title: String?,
        subtitle: String?,
        snapshot: PlayerPlaybackSnapshot,
        enabled: Boolean,
    ) {
        if (!enabled || snapshot.isEnded) {
            clear()
            return
        }

        activeControls = this
        ensureNotificationChannel()
        session.setMetadata(snapshot.toMediaMetadata(title, subtitle))
        session.setPlaybackState(snapshot.toPlaybackState())
        session.isActive = true

        val signature = listOf(
            title.orEmpty(),
            subtitle.orEmpty(),
            snapshot.isPlaying,
            snapshot.isLoading,
            snapshot.isEnded,
            snapshot.durationMs,
        ).joinToString("|")
        if (notificationSignature != signature) {
            notificationSignature = signature
            runCatching {
                notificationManager.notify(NotificationId, buildNotification(title, subtitle, snapshot))
            }
        }
    }

    fun release() {
        clear()
        if (activeControls === this) {
            activeControls = null
        }
        session.release()
    }

    private fun clear() {
        session.isActive = false
        session.setPlaybackState(null)
        notificationSignature = null
        notificationManager.cancel(NotificationId)
    }

    private fun buildNotification(
        title: String?,
        subtitle: String?,
        snapshot: PlayerPlaybackSnapshot,
    ): Notification {
        val playPauseAction = if (snapshot.isPlaying) {
            Notification.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Pause",
                actionPendingIntent(ActionPause),
            ).build()
        } else {
            Notification.Action.Builder(
                android.R.drawable.ic_media_play,
                "Play",
                actionPendingIntent(ActionPlay),
            ).build()
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, NotificationChannelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        return builder
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(title?.takeIf { it.isNotBlank() } ?: "Nuvio")
            .setContentText(subtitle?.takeIf { it.isNotBlank() } ?: "Now playing")
            .setContentIntent(context.launchPendingIntent())
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setOngoing(snapshot.isPlaying || snapshot.isLoading)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_rew,
                    "Rewind",
                    actionPendingIntent(ActionRewind),
                ).build()
            )
            .addAction(playPauseAction)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_ff,
                    "Forward",
                    actionPendingIntent(ActionForward),
                ).build()
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(NotificationChannelId)
        if (existing != null) return
        val channel = NotificationChannel(
            NotificationChannelId,
            "Playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Video playback controls"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(context, PlayerMediaButtonReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun Context.launchPendingIntent(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val NotificationChannelId = "nuvio_playback"
        private const val NotificationId = 4102
        private const val ActionPlay = "com.nuvio.app.player.PLAY"
        private const val ActionPause = "com.nuvio.app.player.PAUSE"
        private const val ActionForward = "com.nuvio.app.player.FORWARD"
        private const val ActionRewind = "com.nuvio.app.player.REWIND"

        private var activeControls: AndroidPlayerSystemMediaControls? = null

        fun dispatchAction(action: String?) {
            val controls = activeControls ?: return
            when (action) {
                ActionPlay -> controls.controllerProvider()?.play()
                ActionPause -> controls.controllerProvider()?.pause()
                ActionForward -> controls.controllerProvider()?.seekBy(10_000L)
                ActionRewind -> controls.controllerProvider()?.seekBy(-10_000L)
            }
        }
    }
}

private fun PlayerPlaybackSnapshot.toMediaMetadata(
    title: String?,
    subtitle: String?,
): MediaMetadata =
    MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, title?.takeIf { it.isNotBlank() } ?: "Nuvio")
        .putString(MediaMetadata.METADATA_KEY_ARTIST, subtitle?.takeIf { it.isNotBlank() } ?: "Nuvio")
        .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs.coerceAtLeast(0L))
        .build()

private fun PlayerPlaybackSnapshot.toPlaybackState(): PlaybackState {
    val state = when {
        isLoading -> PlaybackState.STATE_BUFFERING
        isPlaying -> PlaybackState.STATE_PLAYING
        else -> PlaybackState.STATE_PAUSED
    }
    return PlaybackState.Builder()
        .setActions(
            PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_FAST_FORWARD or
                PlaybackState.ACTION_REWIND
        )
        .setState(
            state,
            positionMs.coerceAtLeast(0L),
            playbackSpeed.takeIf { it > 0f } ?: 1f,
        )
        .setBufferedPosition(bufferedPositionMs.coerceAtLeast(0L))
        .build()
}
