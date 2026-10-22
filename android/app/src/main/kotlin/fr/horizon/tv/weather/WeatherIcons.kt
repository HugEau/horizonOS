package fr.horizon.tv.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Sun = Brush.linearGradient(listOf(Color(0xFFFFD66B), Color(0xFFFF9F43)))
private val CloudLight = Color(0xFFF4F7FB)
private val CloudDark = Color(0xFFB7C3D4)
private val Rain = Color(0xFF7CC4FF)

/** Icônes météo dessinées en vectoriel : nettes à toute taille, aucune ressource bitmap. */
@Composable
fun WeatherIcon(code: Int, isDay: Boolean, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val kind = wxKind(code)
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        when (kind) {
            WxKind.CLEAR -> if (isDay) sun(Offset(s / 2, s / 2), s * 0.26f, s) else moon(Offset(s / 2, s / 2), s * 0.30f)
            WxKind.PARTLY -> {
                if (isDay) sun(Offset(s * 0.38f, s * 0.38f), s * 0.20f, s * 0.8f)
                else moon(Offset(s * 0.38f, s * 0.38f), s * 0.22f)
                cloud(s * 0.14f, s * 0.38f, s * 0.78f, CloudLight)
            }
            WxKind.CLOUDY -> {
                cloud(s * 0.30f, s * 0.14f, s * 0.60f, CloudDark)
                cloud(s * 0.08f, s * 0.30f, s * 0.82f, CloudLight)
            }
            WxKind.FOG -> {
                cloud(s * 0.10f, s * 0.10f, s * 0.80f, CloudDark)
                for (i in 0..2) drawLine(
                    Color.White.copy(alpha = 0.8f), Offset(s * 0.2f, s * (0.68f + i * 0.1f)),
                    Offset(s * 0.8f, s * (0.68f + i * 0.1f)), s * 0.045f, StrokeCap.Round,
                )
            }
            WxKind.DRIZZLE, WxKind.RAIN -> {
                cloud(s * 0.10f, s * 0.08f, s * 0.80f, CloudDark)
                val n = if (kind == WxKind.RAIN) 3 else 2
                for (i in 0 until n) {
                    val x = s * (0.34f + i * 0.17f)
                    drawLine(Rain, Offset(x, s * 0.66f), Offset(x - s * 0.06f, s * 0.84f), s * 0.05f, StrokeCap.Round)
                }
            }
            WxKind.SNOW -> {
                cloud(s * 0.10f, s * 0.08f, s * 0.80f, CloudDark)
                for (i in 0..2) drawCircle(Color.White, s * 0.035f, Offset(s * (0.33f + i * 0.17f), s * (0.72f + (i % 2) * 0.09f)))
            }
            WxKind.STORM -> {
                cloud(s * 0.10f, s * 0.06f, s * 0.80f, Color(0xFF8E9AB0))
                val bolt = Path().apply {
                    moveTo(s * 0.54f, s * 0.54f); lineTo(s * 0.40f, s * 0.76f); lineTo(s * 0.50f, s * 0.76f)
                    lineTo(s * 0.44f, s * 0.94f); lineTo(s * 0.64f, s * 0.68f); lineTo(s * 0.53f, s * 0.68f); close()
                }
                drawPath(bolt, Color(0xFFFFD66B))
            }
        }
    }
}

private fun DrawScope.sun(c: Offset, r: Float, box: Float) {
    for (i in 0 until 8) {
        val a = i * PI / 4
        drawLine(
            Color(0xFFFFC857),
            Offset(c.x + cos(a).toFloat() * r * 1.35f, c.y + sin(a).toFloat() * r * 1.35f),
            Offset(c.x + cos(a).toFloat() * r * 1.75f, c.y + sin(a).toFloat() * r * 1.75f),
            box * 0.045f, StrokeCap.Round,
        )
    }
    drawCircle(Sun, r, c)
}

private fun DrawScope.moon(c: Offset, r: Float) {
    val full = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
    val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(c.x + r * 0.55f, c.y - r * 0.35f), r * 0.9f)) }
    drawPath(Path.combine(PathOperation.Difference, full, bite), Color(0xFFFFE9A8))
}

private fun DrawScope.cloud(x: Float, y: Float, w: Float, color: Color) {
    val h = w * 0.55f
    drawCircle(color, h * 0.34f, Offset(x + w * 0.30f, y + h * 0.55f))
    drawCircle(color, h * 0.46f, Offset(x + w * 0.52f, y + h * 0.38f))
    drawCircle(color, h * 0.32f, Offset(x + w * 0.76f, y + h * 0.58f))
    drawRoundRect(color, Offset(x + w * 0.10f, y + h * 0.55f), Size(w * 0.80f, h * 0.36f), androidx.compose.ui.geometry.CornerRadius(h * 0.18f))
}
