package fr.horizon.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Habillage commun des lecteurs (YouTube/Media3 et VLC) : OK = lecture/pause, gauche/droite = ±10 s
 * (plus vite en maintenant), bas = options. Les barres disparaissent seules après 4 s.
 */
@Composable
fun PlayerChrome(
    title: String,
    subtitle: String?,
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    buffering: Boolean,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    video: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(true) }
    var touched by remember { mutableLongStateOf(0L) }
    var actionsFocused by remember { mutableStateOf(false) }
    val root = remember { FocusRequester() }
    val actionsRequester = remember { FocusRequester() }

    fun poke() { visible = true; touched = System.nanoTime() }

    LaunchedEffect(touched, playing, actionsFocused) {
        if (playing && !actionsFocused) {
            delay(4_000)
            visible = false
        }
    }
    LaunchedEffect(Unit) { root.requestFocus() }

    Box(
        modifier.fillMaxSize().background(Color.Black)
            .focusRequester(root)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (actionsFocused) {
                    poke()
                    return@onPreviewKeyEvent if (e.key == Key.DirectionUp) { root.requestFocus(); true } else false
                }
                val repeat = e.nativeKeyEvent.repeatCount
                val step = when { repeat > 8 -> 60_000L; repeat > 3 -> 30_000L; else -> 10_000L }
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.Spacebar -> { onTogglePlay(); poke(); true }
                    Key.MediaPlay -> { if (!playing) onTogglePlay(); poke(); true }
                    Key.MediaPause -> { if (playing) onTogglePlay(); poke(); true }
                    Key.DirectionLeft, Key.MediaRewind -> { onSeekBy(-step); poke(); true }
                    Key.DirectionRight, Key.MediaFastForward -> { onSeekBy(step); poke(); true }
                    Key.DirectionDown -> {
                        poke()
                        runCatching { actionsRequester.requestFocus() }
                        true
                    }
                    Key.DirectionUp -> { poke(); true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        video()

        AnimatedVisibility(visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(300))) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(180.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))))
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height(260.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))),
                )
                Column(Modifier.align(Alignment.TopStart).padding(horizontal = 56.dp, vertical = 36.dp)) {
                    HText(title, HType.Title, maxLines = 1)
                    if (!subtitle.isNullOrBlank()) HText(subtitle, HType.Label, maxLines = 1)
                }
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 56.dp, vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        HIcon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, Modifier.size(30.dp))
                        HText(formatTime(positionMs), HType.Label, Modifier.width(64.dp))
                        Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                            val f = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                            Box(Modifier.fillMaxWidth(f).height(6.dp).background(Color.White))
                        }
                        HText(if (durationMs > 0) formatTime(durationMs) else "EN DIRECT", HType.Label, Modifier.width(72.dp))
                    }
                    Row(
                        Modifier.onFocusChanged { actionsFocused = it.hasFocus }.focusRequester(actionsRequester).focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        content = actions,
                    )
                }
            }
        }
        if (buffering) Spinner(Modifier.align(Alignment.Center).size(64.dp))
    }
}

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun Spinner(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "a")
    Canvas(modifier) {
        val w = size.minDimension * 0.1f
        drawArc(
            Color.White, startAngle = angle, sweepAngle = 270f, useCenter = false,
            topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
            style = Stroke(w, cap = StrokeCap.Round),
        )
    }
}

/** Liste de choix en surimpression (qualité, audio, sous-titres…). Retour ferme la liste. */
@Composable
fun ChoiceDialog(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(420.dp).glass(RoundedCornerShape(26.dp), Color(0xFF1A1E2C).copy(alpha = 0.94f)).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HText(title, HType.Section)
            LazyColumn(Modifier.height((options.size.coerceAtMost(7) * 52).dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(options) { i, label ->
                    FocusCard(
                        onClick = { onPick(i) },
                        modifier = Modifier.fillMaxWidth().height(44.dp).then(if (i == selected.coerceIn(0, options.lastIndex)) Modifier.focusRequester(focus) else Modifier),
                        shape = RoundedCornerShape(12.dp), focusedScale = 1.03f,
                    ) { focused ->
                        Row(
                            Modifier.fillMaxSize().background(if (focused) Color.White else Color.Transparent).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            HText(label, HType.Body, color = if (focused) HColors.Ink else Color.White, maxLines = 1)
                            if (i == selected) HText("●", HType.Body, color = if (focused) HColors.Ink else HColors.Accent)
                        }
                    }
                }
            }
        }
    }
}
