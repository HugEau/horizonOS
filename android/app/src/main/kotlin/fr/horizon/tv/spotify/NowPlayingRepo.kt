package fr.horizon.tv.spotify

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NowPlaying(
    val pkg: String,
    val title: String,
    val artist: String,
    val album: String,
    val art: Bitmap?,
    val playing: Boolean,
    private val position: Long,
    private val stamp: Long,
    val durationMs: Long,
    val controller: MediaController,
) {
    val isSpotify get() = pkg.startsWith("com.spotify")

    fun positionNow(): Long =
        if (playing) (position + (SystemClock.elapsedRealtime() - stamp)).coerceAtMost(durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
        else position
}

/** Observe la session média active (Spotify en priorité) et expose son état à l'interface. */
object NowPlayingRepo {
    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state
    private val _hasAccess = MutableStateFlow(false)
    val hasAccess: StateFlow<Boolean> = _hasAccess

    private var msm: MediaSessionManager? = null
    private var component: ComponentName? = null
    private val tracked = HashMap<MediaController, MediaController.Callback>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { track(it.orEmpty()) }

    fun hasListenerAccess(ctx: Context) =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    /** À appeler à chaque retour au premier plan : l'autorisation peut avoir été accordée entre-temps. */
    fun start(ctx: Context) {
        val granted = hasListenerAccess(ctx)
        _hasAccess.value = granted
        if (!granted) return
        val manager = msm ?: (ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager).also { msm = it }
        val cn = component ?: ComponentName(ctx, MediaListenerService::class.java).also { component = it }
        runCatching {
            manager.removeOnActiveSessionsChangedListener(sessionsListener)
            manager.addOnActiveSessionsChangedListener(sessionsListener, cn)
            track(manager.getActiveSessions(cn))
        }.onFailure { _hasAccess.value = false }
    }

    private fun track(sessions: List<MediaController>) {
        tracked.keys.filter { c -> sessions.none { it.sessionToken == c.sessionToken } }.forEach {
            tracked.remove(it)?.let(it::unregisterCallback)
        }
        sessions.forEach { c ->
            if (tracked.keys.none { it.sessionToken == c.sessionToken }) {
                val cb = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
                    override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
                    override fun onSessionDestroyed() = refresh()
                }
                c.registerCallback(cb)
                tracked[c] = cb
            }
        }
        refresh()
    }

    private fun refresh() {
        val candidates = tracked.keys.filter { it.metadata != null }
        val best = candidates.sortedWith(
            compareByDescending<MediaController> { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                .thenByDescending { it.packageName.startsWith("com.spotify") },
        ).firstOrNull()
        _state.value = best?.let { c ->
            val m = c.metadata!!
            val ps = c.playbackState
            NowPlaying(
                pkg = c.packageName,
                title = m.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                artist = (m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty(),
                album = m.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
                art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
                playing = ps?.state == PlaybackState.STATE_PLAYING,
                position = ps?.position?.takeIf { it >= 0 } ?: 0L,
                stamp = ps?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
                durationMs = m.getLong(MediaMetadata.METADATA_KEY_DURATION),
                controller = c,
            )
        }
    }
}
