package chat.stoat.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

object GlobalAudioPlayer {
    private var exoPlayer: ExoPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    @OptIn(UnstableApi::class)
    private var simpleCache: SimpleCache? = null

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

    @OptIn(UnstableApi::class)
    private fun getCache(context: Context): SimpleCache {
        val existing = simpleCache
        if (existing != null) return existing
        return synchronized(this) {
            simpleCache ?: run {
                val cacheDir = File(context.cacheDir, "audio_cache")
                val evictor = LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024L) // 100MB
                val databaseProvider = StandaloneDatabaseProvider(context)
                SimpleCache(cacheDir, evictor, databaseProvider).also { simpleCache = it }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun getOrCreatePlayer(context: Context): ExoPlayer {
        val existing = exoPlayer
        if (existing != null) return existing

        val appContext = context.applicationContext

        // High performance load control for instant audio playback
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 2_500,
                /* maxBufferMs = */ 15_000,
                /* bufferForPlaybackMs = */ 100, // Starts immediately after 100ms buffered
                /* bufferForPlaybackAfterRebufferMs = */ 250
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .setUserAgent("Dismod/1.0 (Android)")

        val cache = try {
            getCache(appContext)
        } catch (e: Exception) {
            null
        }

        val dataSourceFactory = if (cache != null) {
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            httpDataSourceFactory
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        return ExoPlayer.Builder(appContext)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        this@GlobalAudioPlayer.isPlaying = playing
                        if (playing) {
                            this@GlobalAudioPlayer.isLoading = false
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        this@GlobalAudioPlayer.isLoading = (playbackState == Player.STATE_BUFFERING)
                        if (playbackState == Player.STATE_READY) {
                            val dur = (this@apply.duration).coerceAtLeast(0L)
                            this@GlobalAudioPlayer.duration = dur
                            this@GlobalAudioPlayer.isLoading = false
                        } else if (playbackState == Player.STATE_ENDED) {
                            this@GlobalAudioPlayer.isPlaying = false
                            this@GlobalAudioPlayer.isLoading = false
                            this@GlobalAudioPlayer.currentPosition = 0L
                            this@GlobalAudioPlayer.progressFraction = 0f
                            this@apply.seekTo(0)
                            this@apply.pause()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        this@GlobalAudioPlayer.isLoading = false
                        this@GlobalAudioPlayer.isPlaying = false
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
        isLoading = true

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
