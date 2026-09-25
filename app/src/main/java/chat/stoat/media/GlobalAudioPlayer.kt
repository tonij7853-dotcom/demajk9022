package chat.stoat.media

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object GlobalAudioPlayer {
    private var exoPlayer: ExoPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    var currentUrl by mutableStateOf<String?>(null)
        private set
    var currentTitle by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var currentPosition by mutableLongStateOf(0L)
        private set
    var duration by mutableLongStateOf(0L)
        private set
    var progressFraction by mutableFloatStateOf(0f)
        private set

    private fun getOrCreatePlayer(context: Context): ExoPlayer {
        val existing = exoPlayer
        if (existing != null) return existing

        return ExoPlayer.Builder(context.applicationContext).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    this@GlobalAudioPlayer.isPlaying = playing
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    this@GlobalAudioPlayer.isLoading = (playbackState == Player.STATE_BUFFERING)
                    if (playbackState == Player.STATE_READY) {
                        val dur = (this@apply.duration).coerceAtLeast(0L)
                        this@GlobalAudioPlayer.duration = dur
                    } else if (playbackState == Player.STATE_ENDED) {
                        this@GlobalAudioPlayer.isPlaying = false
                        this@GlobalAudioPlayer.currentPosition = 0L
                        this@GlobalAudioPlayer.progressFraction = 0f
                        this@apply.seekTo(0)
                        this@apply.pause()
                    }
                }
            })
            exoPlayer = this
        }
    }

    fun play(context: Context, url: String, title: String) {
        val player = getOrCreatePlayer(context)
        if (currentUrl == url) {
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
            return
        }

        currentUrl = url
        currentTitle = title
        currentPosition = 0L
        duration = 0L
        progressFraction = 0f

        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()

        startProgressUpdates()
    }

    fun togglePlay() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        val target = positionMs.coerceIn(0L, duration.coerceAtLeast(0L))
        player.seekTo(target)
        currentPosition = target
        if (duration > 0) {
            progressFraction = (target.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        }
    }

    fun seekToFraction(fraction: Float) {
        if (duration > 0) {
            seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
        }
    }

    fun stop() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        currentUrl = null
        currentTitle = null
        isPlaying = false
        isLoading = false
        currentPosition = 0L
        duration = 0L
        progressFraction = 0f
        progressJob?.cancel()
        progressJob = null
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val player = exoPlayer
                if (player != null && currentUrl != null) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    currentPosition = pos
                    val dur = player.duration.coerceAtLeast(0L)
                    if (dur > 0) {
                        duration = dur
                        progressFraction = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    }
                }
                delay(150)
            }
        }
    }

    fun formatTime(timeMs: Long): String {
        val seconds = (timeMs / 1000).coerceAtLeast(0L)
        val minutes = seconds / 60
        val hours = minutes / 60

        return if (hours > 0) {
            val remainingMinutes = minutes % 60
            val remainingSeconds = seconds % 60
            "%02d:%02d:%02d".format(hours, remainingMinutes, remainingSeconds)
        } else {
            val remainingSeconds = seconds % 60
            "%02d:%02d".format(minutes, remainingSeconds)
        }
    }
}
