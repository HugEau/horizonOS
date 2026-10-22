package fr.horizon.tv.youtube

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import fr.horizon.tv.data.Http
import fr.horizon.tv.news.Chip
import fr.horizon.tv.ui.ChoiceDialog
import fr.horizon.tv.ui.FocusCard
import fr.horizon.tv.ui.HColors
import fr.horizon.tv.ui.HText
import fr.horizon.tv.ui.HType
import fr.horizon.tv.ui.PivotScroll
import fr.horizon.tv.ui.PlayerChrome
import fr.horizon.tv.ui.RemoteImage
import fr.horizon.tv.ui.Router
import fr.horizon.tv.ui.Screen
import fr.horizon.tv.ui.Spinner
import fr.horizon.tv.ui.TvButton
import fr.horizon.tv.ui.TvTextField
import kotlinx.coroutines.delay
import androidx.compose.ui.text.input.ImeAction

private class Feed(val key: String, val label: String, val query: String?)

private val PRESETS = listOf(
    Feed("trending", "Tendances", null),
    Feed("honfleur", "Honfleur", "Honfleur Normandie"),
    Feed("normandie", "Normandie", "Normandie reportage"),
    Feed("musique", "Musique", "musique officiel clip"),
    Feed("actus", "Actualités", "actualités France"),
    Feed("cuisine", "Cuisine", "recette cuisine facile"),
)

private object YtCache {
    val map = HashMap<String, List<YtVideo>>()
}

private fun fmtViews(v: Long) = when {
    v < 0 -> ""
    v >= 1_000_000 -> "%.1f M vues".format(v / 1_000_000.0)
    v >= 1_000 -> "${v / 1_000} k vues"
    else -> "$v vues"
}

@Composable
fun YouTubeTab() {
    var feed by remember { mutableStateOf(PRESETS[0]) }
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val key = submitted?.let { "q:$it" } ?: feed.key

    val result by produceState<Result<List<YtVideo>>?>(YtCache.map[key]?.let { Result.success(it) }, key, retry) {
        YtCache.map[key]?.let { value = Result.success(it); return@produceState }
        value = null
        value = runCatching {
            val q = submitted ?: feed.query
            if (q == null) {
                // Si le kiosque Tendances n'est plus fourni par YouTube, on bascule sur une recherche.
                runCatching { YouTubeRepo.trending() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: YouTubeRepo.search("populaire France")
            } else YouTubeRepo.search(q)
        }.onSuccess { YtCache.map[key] = it }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 48.dp, vertical = 12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                HText("YouTube", HType.Title)
                HText("Sans publicité", HType.Caption, color = HColors.Accent)
            }
            TvTextField(
                query, { query = it }, "Rechercher", Modifier.width(380.dp), placeholder = "Que veux-tu regarder ?",
                imeAction = ImeAction.Search, onDone = { if (query.isNotBlank()) submitted = query.trim() },
            )
            Spacer(Modifier.weight(1f))
        }
        Row(Modifier.padding(horizontal = 48.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PRESETS.forEach { p -> Chip(p.label, submitted == null && feed === p) { submitted = null; feed = p } }
            if (submitted != null) Chip("« $submitted »", true) {}
        }
        val r = result
        when {
            r == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { Spinner(Modifier.width(56.dp).height(56.dp)) }
            r.isFailure -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HText("Impossible de charger YouTube", HType.Title)
                    HText(r.exceptionOrNull()?.message.orEmpty().take(120), HType.Caption)
                    TvButton("Réessayer", { YtCache.map.remove(key); retry++ })
                }
            }
            else -> PivotScroll(startPadding = 80.dp) {
                LazyVerticalGrid(
                    GridCells.Adaptive(250.dp),
                    contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 60.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    items(r.getOrThrow(), key = { it.url }) { v -> VideoCard(v) { Router.push(Screen.YouTube(v.url, v.title)) } }
                }
            }
        }
    }
}

@Composable
fun VideoCard(v: YtVideo, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FocusCard(onClick, Modifier.fillMaxWidth().aspectRatio(16f / 9f), shape = RoundedCornerShape(14.dp), focusedScale = 1.06f) {
            v.thumb?.let { RemoteImage(it, Modifier.fillMaxSize()) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), startY = 200f)))
            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(if (v.live) Color(0xFFE53935) else Color.Black.copy(alpha = 0.75f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                HText(if (v.live) "EN DIRECT" else fr.horizon.tv.ui.formatTime(v.durationSec * 1000), HType.Caption.copy(fontSize = 11.sp), color = Color.White)
            }
        }
        Column(Modifier.padding(horizontal = 4.dp)) {
            HText(v.title, HType.Label.copy(color = Color.White, fontSize = 14.sp, lineHeight = 18.sp), maxLines = 2)
            HText(listOf(v.channel, fmtViews(v.views), v.uploaded.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "), HType.Caption, maxLines = 1)
        }
    }
}

private sealed interface Load {
    data object Loading : Load
    class Error(val message: String) : Load
    class Ready(val s: YtStreams) : Load
}

private const val UA_IOS = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
private const val UA_ANDROID = "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip"
private const val UA_VR = "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
private const val UA_WEB = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"

/** Les liens YouTube ne répondent qu'avec le User-Agent du client qui les a générés (paramètre c=). */
private fun dataSourceFactory() = ResolvingDataSource.Factory(
    OkHttpDataSource.Factory(Http.client.newBuilder().cache(null).build()),
) { spec: DataSpec ->
    val ua = when (spec.uri.getQueryParameter("c")) {
        "IOS" -> UA_IOS
        "ANDROID" -> UA_ANDROID
        "ANDROID_VR" -> UA_VR
        else -> UA_WEB
    }
    spec.withRequestHeaders(spec.httpRequestHeaders + ("User-Agent" to ua))
}

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@Composable
fun YouTubePlayerScreen(videoUrl: String, initialTitle: String) {
    val ctx = LocalContext.current
    var url by remember { mutableStateOf(videoUrl) }
    var title by remember { mutableStateOf(initialTitle) }
    var load by remember { mutableStateOf<Load>(Load.Loading) }
    var heightPref by remember { mutableIntStateOf(1080) }
    var dialog by remember { mutableIntStateOf(0) } // 0 aucun, 1 qualité, 2 vitesse
    var retryKey by remember { mutableIntStateOf(0) }
    var speed by remember { mutableStateOf(1f) }

    val player = remember { ExoPlayer.Builder(ctx).setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build() }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                ended = state == Player.STATE_ENDED
            }
            override fun onPlayerError(error: PlaybackException) { playbackError = error.errorCodeName }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }

    LaunchedEffect(url, retryKey) {
        load = Load.Loading
        playbackError = null
        load = try { Load.Ready(YouTubeRepo.resolve(url)) } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Load.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    val streams = (load as? Load.Ready)?.s
    val chosen = remember(streams, heightPref) {
        streams?.qualities?.let { q -> q.firstOrNull { it.height <= heightPref } ?: q.lastOrNull() }
    }
    LaunchedEffect(streams, chosen) {
        val s = streams ?: return@LaunchedEffect
        val keep = player.currentPosition.takeIf { player.mediaItemCount > 0 } ?: 0L
        val factory = dataSourceFactory()
        fun prog(u: String): MediaSource = ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(u))
        val source: MediaSource? = when {
            s.hlsUrl != null -> HlsMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(s.hlsUrl))
            chosen != null && s.audioUrl != null -> MergingMediaSource(prog(chosen.videoUrl), prog(s.audioUrl))
            s.progressive.isNotEmpty() -> prog(s.progressive.first().videoUrl)
            else -> null
        }
        if (source == null) { load = Load.Error("Aucun flux lisible pour cette vidéo"); return@LaunchedEffect }
        title = s.title.ifBlank { title }
        player.setMediaSource(source)
        player.prepare()
        if (keep > 0) player.seekTo(keep)
        player.playWhenReady = true
    }
    LaunchedEffect(speed) { player.playbackParameters = PlaybackParameters(speed) }
    LaunchedEffect(Unit) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            delay(500)
        }
    }

    fun next(): Boolean {
        val n = streams?.related?.firstOrNull { it.url != url } ?: return false
        url = n.url; title = n.title; ended = false
        return true
    }
    LaunchedEffect(ended) { if (ended) next() }

    when (val l = load) {
        is Load.Loading -> Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Spinner(Modifier.width(56.dp).height(56.dp))
                HText(title, HType.Label, maxLines = 1)
            }
        }
        is Load.Error -> ErrorPane(l.message, url, onRetry = { retryKey++ }, onClose = { Router.pop() })
        is Load.Ready -> {
            if (playbackError != null) {
                ErrorPane("Lecture impossible (${playbackError})", url, onRetry = { retryKey++ }, onClose = { Router.pop() })
            } else Box(Modifier.fillMaxSize()) {
                PlayerChrome(
                    title = title,
                    subtitle = l.s.channel + (chosen?.let { "  ·  ${it.label}" } ?: ""),
                    positionMs = position, durationMs = duration, playing = playing, buffering = buffering,
                    onTogglePlay = { if (player.isPlaying) player.pause() else player.play() },
                    onSeekBy = { d -> player.seekTo((player.currentPosition + d).coerceIn(0, if (player.duration > 0) player.duration else Long.MAX_VALUE)); position = player.currentPosition },
                    actions = {
                        if (l.s.qualities.isNotEmpty()) TvButton("Qualité", { dialog = 1 }, icon = Icons.Filled.HighQuality)
                        TvButton("Vitesse ${if (speed == 1f) "" else "×$speed"}".trim(), { dialog = 2 }, icon = Icons.Filled.Speed)
                        if (l.s.related.isNotEmpty()) TvButton("Suivant", { next() }, icon = Icons.Filled.SkipNext)
                    },
                    video = {
                        AndroidView(
                            factory = { c ->
                                PlayerView(c).apply {
                                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    setKeepContentOnPlayerReset(true)
                                    keepScreenOn = true
                                    isFocusable = false
                                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                    this.player = player
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                )
                when (dialog) {
                    1 -> {
                        val q = l.s.qualities
                        ChoiceDialog("Qualité", q.map { it.label }, q.indexOfFirst { it === chosen }, { heightPref = q[it].height; dialog = 0 }, { dialog = 0 })
                    }
                    2 -> ChoiceDialog("Vitesse de lecture", SPEEDS.map { if (it == 1f) "Normale" else "×$it" }, SPEEDS.indexOf(speed), { speed = SPEEDS[it]; dialog = 0 }, { dialog = 0 })
                }
            }
        }
    }
}

@Composable
private fun ErrorPane(message: String, url: String, onRetry: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize().background(Color(0xFF0A0D18)), Alignment.Center) {
        Column(Modifier.width(640.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HText("Cette vidéo ne se lance pas", HType.Display.copy(fontSize = 32.sp))
            HText(
                "YouTube change régulièrement ses protocoles : le module d'extraction (NewPipeExtractor) doit alors être mis à jour. $message",
                HType.Body, color = HColors.TextDim, maxLines = 5,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Réessayer", onRetry, primary = true)
                TvButton("Ouvrir dans l'app YouTube", {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                })
                TvButton("Retour", onClose)
            }
        }
    }
}
