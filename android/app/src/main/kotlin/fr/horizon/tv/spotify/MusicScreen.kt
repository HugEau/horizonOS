package fr.horizon.tv.spotify

import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import fr.horizon.tv.home.AppsRepo
import fr.horizon.tv.ui.FocusCard
import fr.horizon.tv.ui.HColors
import fr.horizon.tv.ui.HIcon
import fr.horizon.tv.ui.HText
import fr.horizon.tv.ui.HType
import fr.horizon.tv.ui.TvButton
import kotlinx.coroutines.delay

val SpotifyGreen = Color(0xFF1DB954)

/** Une petite bitmap agrandie avec filtrage bilinéaire donne un flou de pochette sans RenderEffect (API 31+). */
@Composable
fun BlurredArt(art: Bitmap, modifier: Modifier = Modifier) {
    val tiny = remember(art) { Bitmap.createScaledBitmap(art, 24, 24, true).asImageBitmap() }
    Image(tiny, null, modifier, contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low, alpha = 0.55f)
}

@Composable
private fun positionTicker(np: NowPlaying): State<Long> = produceState(np.positionNow(), np) {
    while (true) {
        value = np.positionNow()
        delay(500)
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun MusicTab() {
    val ctx = LocalContext.current
    val np by NowPlayingRepo.state.collectAsState()
    val access by NowPlayingRepo.hasAccess.collectAsState()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { NowPlayingRepo.start(ctx) }

    Box(Modifier.fillMaxSize()) {
        np?.art?.let { BlurredArt(it, Modifier.fillMaxSize()) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
        val current = np
        if (current != null) {
            Row(Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(56.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtSquare(current.art, Modifier.size(300.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HText(if (current.isSpotify) "SPOTIFY" else current.pkg.substringAfterLast('.').uppercase(), HType.Caption.copy(letterSpacing = 2.sp), color = SpotifyGreen)
                    HText(current.title, HType.Display, maxLines = 2)
                    HText(current.artist, HType.Title.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = HColors.TextDim, maxLines = 1)
                    if (current.album.isNotBlank()) HText(current.album, HType.Label, maxLines = 1)
                    val pos by positionTicker(current)
                    Progress(pos, current.durationMs, Modifier.padding(top = 14.dp).fillMaxWidth(0.9f))
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundControl(Icons.Filled.SkipPrevious, 54.dp) { current.controller.transportControls.skipToPrevious() }
                        RoundControl(if (current.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, 72.dp, Modifier.focusRequester(first), primary = true) {
                            if (current.playing) current.controller.transportControls.pause() else current.controller.transportControls.play()
                        }
                        RoundControl(Icons.Filled.SkipNext, 54.dp) { current.controller.transportControls.skipToNext() }
                        Box(Modifier.width(20.dp))
                        TvButton("Ouvrir ${if (current.isSpotify) "Spotify" else "l'app"}", {
                            if (current.isSpotify) AppsRepo.openSpotify(ctx)
                            else ctx.packageManager.getLaunchIntentForPackage(current.pkg)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        })
                    }
                }
            }
            LaunchedEffect(current.title) { runCatching { first.requestFocus() } }
        } else {
            Column(Modifier.align(Alignment.Center).width(720.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(SpotifyGreen), contentAlignment = Alignment.Center) {
                    HIcon(Icons.Filled.MusicNote, Modifier.size(54.dp), tint = Color.Black)
                }
                HText("Spotify", HType.Display)
                HText(
                    "Lance une musique dans Spotify sur la TV, ou envoie-la depuis ton téléphone avec Spotify Connect. " +
                        "Elle s'affiche ici et sur l'accueil, avec les commandes à la télécommande.",
                    HType.Body.copy(fontSize = 18.sp, lineHeight = 26.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center), color = HColors.TextDim,
                )
                TvButton("Ouvrir Spotify", { AppsRepo.openSpotify(ctx) }, icon = Icons.Filled.MusicNote, primary = true, modifier = Modifier.focusRequester(first))
                if (!access) {
                    HText("Pour afficher la lecture en cours, autorise Horizon à lire les sessions média :", HType.Label, Modifier.padding(top = 12.dp))
                    TvButton("Autoriser l'accès", { openListenerSettings(ctx) })
                    HText(
                        "Si l'écran n'existe pas sur ta TV : adb shell cmd notification allow_listener fr.horizon.tv/fr.horizon.tv.spotify.MediaListenerService",
                        HType.Caption, color = HColors.TextFaint,
                    )
                }
            }
            LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        }
    }
}

fun openListenerSettings(ctx: android.content.Context) {
    runCatching {
        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun ArtSquare(art: Bitmap?, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(HColors.GlassStrong), contentAlignment = Alignment.Center) {
        if (art != null) Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else HIcon(Icons.Filled.MusicNote, Modifier.size(80.dp), tint = HColors.TextFaint)
    }
}

@Composable
private fun Progress(pos: Long, dur: Long, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f))) {
            val f = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth(f).height(5.dp).background(Color.White))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HText(fmt(pos), HType.Caption)
            if (dur > 0) HText(fmt(dur), HType.Caption)
        }
    }
}

@Composable
fun RoundControl(icon: ImageVector, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    FocusCard(
        onClick, modifier.size(size), shape = CircleShape, focusedScale = 1.12f,
        background = if (primary) Color.White.copy(alpha = 0.92f) else HColors.GlassStrong,
    ) { focused ->
        Box(Modifier.fillMaxSize().background(if (focused && !primary) Color.White else Color.Transparent), contentAlignment = Alignment.Center) {
            HIcon(icon, Modifier.size(size * 0.5f), tint = if (primary || focused) HColors.Ink else Color.White)
        }
    }
}

/** Widget d'accueil « En lecture ». */
@Composable
fun NowPlayingWidget(np: NowPlaying, modifier: Modifier = Modifier) {
    FocusCard(
        onClick = { if (np.playing) np.controller.transportControls.pause() else np.controller.transportControls.play() },
        modifier = modifier.size(width = 330.dp, height = 176.dp),
        background = Color(0xFF1B1F2B),
    ) {
        Box(Modifier.fillMaxSize()) {
            np.art?.let { BlurredArt(it, Modifier.fillMaxSize()) }
            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtSquare(np.art, Modifier.size(104.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    HText(if (np.isSpotify) "SPOTIFY" else "EN LECTURE", HType.Caption.copy(letterSpacing = 1.5.sp), color = SpotifyGreen)
                    HText(np.title, HType.Title.copy(fontSize = 19.sp), maxLines = 2)
                    HText(np.artist, HType.Label, maxLines = 1)
                    HIcon(if (np.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, Modifier.padding(top = 6.dp).size(26.dp))
                }
            }
        }
    }
}
