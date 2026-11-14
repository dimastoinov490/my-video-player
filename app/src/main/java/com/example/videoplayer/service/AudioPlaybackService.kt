package com.example.videoplayer.service

import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.json.JSONArray
import org.json.JSONObject

/**
 * Owns audio playback independently of the Activity and WebView.
 *
 * Media3 publishes the active session to Android's media controls. The last
 * queue, item, position and play intent are also checkpointed so a newly
 * created service can restore the listening session after process recreation.
 */
@UnstableApi
class AudioPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val checkpointHandler = Handler(Looper.getMainLooper())

    private val checkpointRunnable = object : Runnable {
        override fun run() {
            val current = player ?: return
            savePlaybackCheckpoint(current)
            if (current.isPlaying) {
                checkpointHandler.postDelayed(this, CHECKPOINT_INTERVAL_MS)
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            // Save immediately on queue, item, playback-state and play-intent changes.
            savePlaybackCheckpoint(player)
            scheduleCheckpoint(player)
        }
    }

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player = exoPlayer
        exoPlayer.addListener(playerListener)
        restorePlaybackCheckpoint(exoPlayer)
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        player?.let { current ->
            checkpointHandler.removeCallbacks(checkpointRunnable)
            savePlaybackCheckpoint(current)
            current.removeListener(playerListener)
        }
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun scheduleCheckpoint(current: Player) {
        checkpointHandler.removeCallbacks(checkpointRunnable)
        if (current.isPlaying) {
            checkpointHandler.postDelayed(checkpointRunnable, CHECKPOINT_INTERVAL_MS)
        }
    }

    private fun savePlaybackCheckpoint(current: Player) {
        runCatching {
            val queue = JSONArray()
            for (index in 0 until current.mediaItemCount.coerceAtMost(MAX_SAVED_QUEUE_SIZE)) {
                val item = current.getMediaItemAt(index)
                val metadata = item.mediaMetadata
                queue.put(JSONObject().apply {
                    put("uri", item.localConfiguration?.uri?.toString().orEmpty())
                    put("title", metadata.title?.toString().orEmpty())
                    put("artist", metadata.artist?.toString().orEmpty())
                    put("album", metadata.albumTitle?.toString().orEmpty())
                })
            }

            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            if (queue.length() == 0) {
                prefs.edit().remove(KEY_QUEUE).remove(KEY_INDEX)
                    .remove(KEY_POSITION).remove(KEY_PLAY_WHEN_READY).apply()
                return
            }

            prefs.edit()
                .putString(KEY_QUEUE, queue.toString())
                .putInt(KEY_INDEX, current.currentMediaItemIndex.coerceAtLeast(0))
                .putLong(KEY_POSITION, current.currentPosition.coerceAtLeast(0L))
                .putBoolean(KEY_PLAY_WHEN_READY, current.playWhenReady && current.playbackState != Player.STATE_ENDED)
                .apply()
        }
    }

    private fun restorePlaybackCheckpoint(target: ExoPlayer) {
        runCatching {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val rawQueue = prefs.getString(KEY_QUEUE, null) ?: return
            val savedQueue = JSONArray(rawQueue)
            val restoredItems = ArrayList<MediaItem>(savedQueue.length())
            for (index in 0 until savedQueue.length()) {
                val entry = savedQueue.optJSONObject(index) ?: continue
                val uri = entry.optString("uri").trim()
                if (uri.isBlank()) continue
                val metadata = MediaMetadata.Builder()
                    .setTitle(entry.optString("title").takeIf { it.isNotBlank() })
                    .setArtist(entry.optString("artist").takeIf { it.isNotBlank() })
                    .setAlbumTitle(entry.optString("album").takeIf { it.isNotBlank() })
                    .build()
                restoredItems.add(
                    MediaItem.Builder()
                        .setUri(uri)
                        .setMediaMetadata(metadata)
                        .build()
                )
            }
            if (restoredItems.isEmpty()) return

            val index = prefs.getInt(KEY_INDEX, 0).coerceIn(0, restoredItems.lastIndex)
            val position = prefs.getLong(KEY_POSITION, 0L).coerceAtLeast(0L)
            val shouldResume = prefs.getBoolean(KEY_PLAY_WHEN_READY, false)
            target.setMediaItems(restoredItems, index, position)
            target.prepare()
            target.playWhenReady = shouldResume
        }
    }

    private companion object {
        const val PREFS_NAME = "nexora_audio_session"
        const val KEY_QUEUE = "queue"
        const val KEY_INDEX = "current_index"
        const val KEY_POSITION = "position_ms"
        const val KEY_PLAY_WHEN_READY = "play_when_ready"
        const val CHECKPOINT_INTERVAL_MS = 2_000L
        const val MAX_SAVED_QUEUE_SIZE = 500
    }
}
