package fr.horizon.tv.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

private class Sky(val hour: Float, val top: Color, val bottom: Color)

// Palettes sombres pour que le texte blanc reste lisible toute la journée.
private val SKY = listOf(
    Sky(0f, Color(0xFF070B1E), Color(0xFF141C3C)),
    Sky(5f, Color(0xFF10163A), Color(0xFF3B2F63)),
    Sky(7f, Color(0xFF2E4A7D), Color(0xFFE08A6B)),
    Sky(10f, Color(0xFF163F73), Color(0xFF3F86C2)),
    Sky(16f, Color(0xFF16406F), Color(0xFF4C8CBF)),
    Sky(19f, Color(0xFF2B2358), Color(0xFFE8714F)),
    Sky(21f, Color(0xFF111736), Color(0xFF30284F)),
    Sky(24f, Color(0xFF070B1E), Color(0xFF141C3C)),
)

private fun skyAt(hour: Float): Pair<Color, Color> {
    val b = SKY.indexOfLast { it.hour <= hour }.coerceIn(0, SKY.size - 2)
    val a = SKY[b]
    val c = SKY[b + 1]
    val t = ((hour - a.hour) / (c.hour - a.hour)).coerceIn(0f, 1f)
    return lerp(a.top, c.top, t) to lerp(a.bottom, c.bottom, t)
}

/** Dégradé de ciel qui suit l'heure, avec deux lueurs lentes pour donner de la profondeur. */
@Composable
fun SkyBackdrop(modifier: Modifier = Modifier.fillMaxSize()) {
    val now by rememberNow()
    val (top, bottom) = skyAt(now.hour + now.minute / 60f)
    val drift = rememberInfiniteTransition(label = "drift")
    val t by drift.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(22_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "t",
    )
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(top, bottom)))
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * (0.25f + 0.2f * t), size.height * 0.25f),
                radius = size.width * 0.5f,
            ),
            radius = size.width * 0.5f,
            center = Offset(size.width * (0.25f + 0.2f * t), size.height * 0.25f),
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFFFB84D).copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * (0.85f - 0.2f * t), size.height * 0.85f),
                radius = size.width * 0.45f,
            ),
            radius = size.width * 0.45f,
            center = Offset(size.width * (0.85f - 0.2f * t), size.height * 0.85f),
        )
    }
}
