package fr.horizon.tv.nas

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import fr.horizon.tv.data.NasSource
import fr.horizon.tv.data.SettingsStore
import fr.horizon.tv.ui.ChoiceDialog
import fr.horizon.tv.ui.FocusCard
import fr.horizon.tv.ui.HColors
import fr.horizon.tv.ui.HIcon
import fr.horizon.tv.ui.HText
import fr.horizon.tv.ui.HType
import fr.horizon.tv.ui.PivotScroll
import fr.horizon.tv.ui.PlayerChrome
import fr.horizon.tv.ui.Router
import fr.horizon.tv.ui.Screen
import fr.horizon.tv.ui.SectionTitle
import fr.horizon.tv.ui.SettingsSection
import fr.horizon.tv.ui.Spinner
import fr.horizon.tv.ui.TvButton
import kotlinx.coroutines.delay
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

@Composable
fun FilmsTab() {
    val settings by SettingsStore.state.collectAsState()
    val resumes = remember { NasRepo.resumes() }
    if (settings.nas.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(Modifier.width(660.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(Color(0xFFFF8800)), Alignment.Center) { HIcon(Icons.Filled.Movie, Modifier.size(52.dp), tint = Color.White) }
                HText("Films & séries", HType.Display)
                HText(
                    "Branche ton NAS (SMB, NFS, FTP, SFTP ou HTTP) et lis tes films avec VLC, directement dans Horizon : " +
                        "tous les formats, sous-titres, pistes audio, reprise de lecture.",
                    HType.Body.copy(fontSize = 18.sp, lineHeight = 26.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center), color = HColors.TextDim,
                )
                TvButton("Ajouter mon NAS", { Router.push(Screen.Settings(SettingsSection.NAS)) }, icon = Icons.Filled.Storage, primary = true)
            }
        }
        return
    }
    PivotScroll(startPadding = 60.dp) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            item { SectionTitle("Mon NAS") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(settings.nas, key = { it.id }) { s -> NasSourceCard(s) }
                }
            }
            if (resumes.isNotEmpty()) {
                item { SectionTitle("Reprendre la lecture") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        items(resumes, key = { it.url }) { r -> ResumeCard(r) }
                    }
                }
            }
        }
    }
}

@Composable
fun NasSourceCard(s: NasSource, modifier: Modifier = Modifier) {
    FocusCard(
        onClick = { Router.push(Screen.NasBrowse(s.id, s.url, s.name)) },
        modifier = modifier.size(width = 290.dp, height = 170.dp),
        background = Color.Transparent,
    ) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFFF9F43), Color(0xFFC0392B))))) {
            HIcon(Icons.Filled.Storage, Modifier.align(Alignment.TopEnd).padding(18.dp).size(40.dp), tint = Color.White.copy(alpha = 0.9f))
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                HText(s.name, HType.Title, maxLines = 1)
                HText(Uri.parse(s.url).let { "${it.scheme?.uppercase()} · ${it.host}" }, HType.Label, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
            }
        }
    }
}

@Composable
fun ResumeCard(r: Resume, modifier: Modifier = Modifier) {
    FocusCard(
        onClick = { Router.push(Screen.NasPlay(r.url, r.title)) },
        modifier = modifier.size(width = 300.dp, height = 170.dp),
        background = Color.Transparent,
    ) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF3B4B7A), Color(0xFF151B33))))) {
            HIcon(Icons.Filled.Movie, Modifier.align(Alignment.TopEnd).padding(18.dp).size(34.dp), tint = Color.White.copy(alpha = 0.55f))
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HText(r.title, HType.Body.copy(fontSize = 17.sp, lineHeight = 21.sp), maxLines = 2)
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth((r.pos.toFloat() / r.dur.coerceAtLeast(1)).coerceIn(0f, 1f)).height(4.dp).background(HColors.Accent))
                }
                HText("Reste ${fr.horizon.tv.ui.formatTime((r.dur - r.pos).coerceAtLeast(0))}", HType.Caption)
            }
        }
    }
}

private sealed interface Listing {
    data object Loading : Listing
    class Failed(val message: String) : Listing
    class Ready(val items: List<NasEntry>) : Listing
}

@Composable
fun NasBrowseScreen(url: String, title: String) {
    val ctx = LocalContext.current
    var retry by remember { mutableIntStateOf(0) }
    val listing by produceState<Listing>(Listing.Loading, url, retry) {
        value = Listing.Loading
        value = try { Listing.Ready(NasRepo.list(ctx, url)) } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            Listing.Failed(if (e is kotlinx.coroutines.TimeoutCancellationException) "Le NAS ne répond pas (délai dépassé)" else e.message ?: "Erreur de connexion")
        }
    }
    Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
        Row(Modifier.padding(horizontal = 48.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            TvButton("Retour", { Router.pop() })
            Column {
                HText(title, HType.Title, maxLines = 1)
                HText(Uri.decode(NasRepo.clean(url)), HType.Caption, maxLines = 1)
            }
        }
        when (val l = listing) {
            Listing.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { Spinner(Modifier.size(56.dp)) }
            is Listing.Failed -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HText("Connexion impossible", HType.Title)
                    HText(l.message.take(140), HType.Caption)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvButton("Réessayer", { retry++ }, primary = true)
                        TvButton("Réglages NAS", { Router.push(Screen.Settings(SettingsSection.NAS)) }, icon = Icons.Filled.Settings)
                    }
                }
            }
            is Listing.Ready -> if (l.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { HText("Aucun film dans ce dossier", HType.Label) }
            } else {
                val files = remember(l) { l.items.filter { !it.isDir } }
                PivotScroll(startPadding = 60.dp) {
                    LazyVerticalGrid(
                        GridCells.Adaptive(230.dp),
                        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 60.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(l.items, key = { it.url }) { e ->
                            EntryCard(e) {
                                if (e.isDir) Router.push(Screen.NasBrowse("", e.url, e.name))
                                else { NasQueue.files = files; Router.push(Screen.NasPlay(e.url, NasRepo.prettyName(e.name, false))) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryCard(e: NasEntry, onClick: () -> Unit) {
    val resume = remember(e.url) { if (e.isDir) null else NasRepo.resumeFor(e.url) }
    FocusCard(onClick, Modifier.fillMaxWidth().height(132.dp), shape = RoundedCornerShape(18.dp), focusedScale = 1.06f) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            HIcon(if (e.isDir) Icons.Filled.Folder else Icons.Filled.Movie, Modifier.size(30.dp), tint = if (e.isDir) HColors.Accent else Color.White.copy(alpha = 0.85f))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HText(NasRepo.prettyName(e.name, e.isDir), HType.Label.copy(color = Color.White, fontSize = 15.sp, lineHeight = 19.sp), maxLines = 2)
                if (resume != null) Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth((resume.pos.toFloat() / resume.dur.coerceAtLeast(1)).coerceIn(0f, 1f)).height(3.dp).background(HColors.Accent))
                }
            }
        }
    }
}

@Composable
fun VlcPlayerScreen(url: String, initialTitle: String) {
    val ctx = LocalContext.current
    val lib = remember { Vlc.get(ctx) }
    val mp = remember { MediaPlayer(lib) }
    var current by remember { mutableStateOf(url) }
    var title by remember { mutableStateOf(initialTitle) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dialog by remember { mutableIntStateOf(0) } // 1 audio, 2 sous-titres, 3 vitesse
    var speed by remember { mutableStateOf(1f) }
    var error by remember { mutableStateOf(false) }
    var seekOnStart by remember { mutableLongStateOf(0L) }
    var ended by remember { mutableStateOf(false) }
    var retryKey by remember { mutableIntStateOf(0) }
    fun next(): Boolean {
        val q = NasQueue.files
        val i = q.indexOfFirst { it.url == NasRepo.clean(current) }
        val n = q.getOrNull(i + 1) ?: return false
        NasRepo.saveResume(current, title, 0, 0)
        current = n.url; title = NasRepo.prettyName(n.name, false)
        return true
    }

    DisposableEffect(mp) {
        mp.setEventListener { e ->
            when (e.type) {
                MediaPlayer.Event.Playing -> {
                    playing = true; buffering = false; error = false
                    if (seekOnStart > 0) { mp.time = seekOnStart; seekOnStart = 0 }
                }
                MediaPlayer.Event.Paused -> playing = false
                MediaPlayer.Event.Buffering -> buffering = e.buffering < 100f
                MediaPlayer.Event.EncounteredError -> { error = true; buffering = false }
                MediaPlayer.Event.EndReached -> { playing = false; ended = true }
                MediaPlayer.Event.Stopped -> playing = false
            }
        }
        onDispose {
            if (mp.length > 0) NasRepo.saveResume(current, title, mp.time, mp.length)
            mp.setEventListener(null)
            mp.stop()
            mp.detachViews()
            mp.release()
        }
    }

    LaunchedEffect(current, retryKey) {
        error = false; buffering = true; ended = false
        seekOnStart = NasRepo.resumeFor(current)?.pos?.minus(3_000)?.coerceAtLeast(0) ?: 0L
        val media = Media(lib, Uri.parse(NasRepo.authed(current))).apply { setHWDecoderEnabled(true, false) }
        mp.media = media
        media.release()
        mp.play()
    }
    LaunchedEffect(ended) { if (ended && !next()) Router.pop() }
    LaunchedEffect(speed) { mp.rate = speed }
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            position = mp.time.coerceAtLeast(0)
            duration = mp.length.coerceAtLeast(0)
            if (++tick % 20 == 0 && duration > 0 && mp.isPlaying) NasRepo.saveResume(current, title, position, duration)
            delay(500)
        }
    }

    if (error) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0A0D18)), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HText("Lecture impossible", HType.Display.copy(fontSize = 32.sp))
                HText("Le fichier est introuvable ou le NAS a refusé la connexion.", HType.Body, color = HColors.TextDim)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton("Réessayer", { retryKey++ }, primary = true)
                    TvButton("Retour", { Router.pop() })
                }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        PlayerChrome(
            title = title, subtitle = "VLC", positionMs = position, durationMs = duration, playing = playing, buffering = buffering,
            onTogglePlay = { if (mp.isPlaying) mp.pause() else mp.play() },
            onSeekBy = { d -> if (mp.isSeekable) { mp.time = (mp.time + d).coerceIn(0, mp.length.coerceAtLeast(1)); position = mp.time } },
            actions = {
                TvButton("Audio", { dialog = 1 }, icon = Icons.Filled.AudioFile)
                TvButton("Sous-titres", { dialog = 2 }, icon = Icons.Filled.Subtitles)
                TvButton("Vitesse ${if (speed == 1f) "" else "×$speed"}".trim(), { dialog = 3 }, icon = Icons.Filled.Speed)
                if (NasQueue.files.indexOfFirst { it.url == NasRepo.clean(current) }.let { it >= 0 && it < NasQueue.files.lastIndex })
                    TvButton("Suivant", { next() }, icon = Icons.Filled.SkipNext)
            },
            video = {
                AndroidView(
                    factory = { c ->
                        VLCVideoLayout(c).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            keepScreenOn = true
                            isFocusable = false
                            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                            mp.attachViews(this, null, true, false)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
        when (dialog) {
            1, 2 -> {
                val audio = dialog == 1
                val tracks = (if (audio) mp.audioTracks else mp.spuTracks)?.toList().orEmpty()
                val sel = tracks.indexOfFirst { it.id == if (audio) mp.audioTrack else mp.spuTrack }
                if (tracks.isEmpty()) { dialog = 0 }
                else ChoiceDialog(
                    if (audio) "Piste audio" else "Sous-titres",
                    tracks.map { if (it.id == -1) "Désactivé" else it.name },
                    sel,
                    { i -> if (audio) mp.audioTrack = tracks[i].id else mp.spuTrack = tracks[i].id; dialog = 0 },
                    { dialog = 0 },
                )
            }
            3 -> {
                val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                ChoiceDialog("Vitesse de lecture", speeds.map { if (it == 1f) "Normale" else "×$it" }, speeds.indexOf(speed), { speed = speeds[it]; dialog = 0 }, { dialog = 0 })
            }
        }
    }
}
