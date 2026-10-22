package fr.horizon.tv.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.horizon.tv.data.WeatherStyle
import fr.horizon.tv.data.WeatherWidgetConfig
import fr.horizon.tv.data.WidgetTone
import fr.horizon.tv.ui.FocusCard
import fr.horizon.tv.ui.HText
import fr.horizon.tv.ui.HType
import kotlinx.coroutines.delay
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

class WxState(val weather: Weather?, val failed: Boolean)

@Composable
fun rememberWeather(lat: Double, lon: Double): State<WxState> = produceState(WxState(null, false), lat, lon) {
    while (true) {
        value = try {
            WxState(WeatherRepo.load(lat, lon), false)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            WxState(value.weather, true)
        }
        delay(if (value.failed) 30_000 else 15 * 60_000L)
    }
}

fun fmtTemp(c: Double, fahrenheit: Boolean) = "${(if (fahrenheit) c * 9 / 5 + 32 else c).roundToInt()}°"

fun widgetWidth(style: WeatherStyle): Dp = when (style) {
    WeatherStyle.CURRENT -> 250.dp
    WeatherStyle.HOURLY -> 520.dp
    WeatherStyle.DAILY -> 520.dp
    WeatherStyle.DETAILS -> 330.dp
}

val WidgetHeight = 176.dp

private fun autoTone(w: Weather?): Pair<Color, Color> {
    if (w == null) return Color(0xFF3A4A66) to Color(0xFF1B2437)
    val day = w.isDay
    return when (wxKind(w.code)) {
        WxKind.CLEAR -> if (day) Color(0xFF3D84D6) to Color(0xFF1D4E8F) else Color(0xFF1B2347) to Color(0xFF0B1030)
        WxKind.PARTLY -> if (day) Color(0xFF4B86C4) to Color(0xFF2A5385) else Color(0xFF263256) to Color(0xFF111A38)
        WxKind.CLOUDY -> Color(0xFF6B7A90) to Color(0xFF37445A)
        WxKind.FOG -> Color(0xFF8593A3) to Color(0xFF4A5666)
        WxKind.DRIZZLE, WxKind.RAIN -> Color(0xFF4A6077) to Color(0xFF243344)
        WxKind.SNOW -> Color(0xFF8FA7C4) to Color(0xFF4E6585)
        WxKind.STORM -> Color(0xFF4B3F72) to Color(0xFF1E1936)
    }
}

@Composable
fun WeatherWidget(
    cfg: WeatherWidgetConfig,
    fahrenheit: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val st by rememberWeather(cfg.lat, cfg.lon)
    val w = st.weather
    val (top, bottom) = if (cfg.tone == WidgetTone.AUTO) autoTone(w) else Color(cfg.tone.top) to Color(cfg.tone.bottom)
    FocusCard(
        onClick = onClick,
        modifier = modifier.width(widgetWidth(cfg.style)).height(WidgetHeight),
        background = Color.Transparent,
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(top, bottom)))
                .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent))),
        ) {
            if (w == null) {
                HText(if (st.failed) "Météo indisponible" else "Chargement…", HType.Label, Modifier.align(Alignment.Center))
            } else when (cfg.style) {
                WeatherStyle.CURRENT -> Current(cfg, w, fahrenheit)
                WeatherStyle.HOURLY -> Hourly(cfg, w, fahrenheit)
                WeatherStyle.DAILY -> Daily(cfg, w, fahrenheit)
                WeatherStyle.DETAILS -> Details(cfg, w, fahrenheit)
            }
        }
    }
}

@Composable
private fun Header(cfg: WeatherWidgetConfig, w: Weather) {
    Column {
        HText(cfg.city, HType.Label.copy(fontSize = 15.sp), color = Color.White, maxLines = 1)
        HText(wxLabel(w.code), HType.Caption, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
    }
}

@Composable
private fun Current(cfg: WeatherWidgetConfig, w: Weather, f: Boolean) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Header(cfg, w)
            WeatherIcon(w.code, w.isDay, 52.dp)
        }
        Column {
            HText(fmtTemp(w.tempC, f), HType.Display.copy(fontSize = 54.sp), maxLines = 1)
            val today = w.daily.firstOrNull()
            if (today != null) {
                HText("Max ${fmtTemp(today.maxC, f)}  ·  Min ${fmtTemp(today.minC, f)}", HType.Caption, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun Hourly(cfg: WeatherWidgetConfig, w: Weather, f: Boolean) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Header(cfg, w)
            HText(fmtTemp(w.tempC, f), HType.Title, color = Color.White)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            w.hourly.filterIndexed { i, _ -> i % 2 == 0 }.take(8).forEachIndexed { i, h ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    HText(if (i == 0) "Auj." else "%02dh".format(h.time.hour), HType.Caption, color = Color.White.copy(alpha = 0.75f))
                    WeatherIcon(h.code, h.isDay, 30.dp)
                    HText(fmtTemp(h.tempC, f), HType.Label, color = Color.White)
                    HText(if (h.rainProb >= 20) "${h.rainProb}%" else " ", HType.Caption.copy(fontSize = 11.sp), color = Color(0xFF9AD0FF))
                }
            }
        }
    }
}

@Composable
private fun Daily(cfg: WeatherWidgetConfig, w: Weather, f: Boolean) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Header(cfg, w)
            HText("7 jours", HType.Caption, color = Color.White.copy(alpha = 0.75f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            w.daily.take(7).forEachIndexed { i, d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    HText(
                        if (i == 0) "Auj." else d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH).removeSuffix("."),
                        HType.Caption, color = Color.White.copy(alpha = 0.75f),
                    )
                    WeatherIcon(d.code, true, 30.dp)
                    HText(fmtTemp(d.maxC, f), HType.Label, color = Color.White)
                    HText(fmtTemp(d.minC, f), HType.Label, color = Color.White.copy(alpha = 0.55f))
                }
            }
        }
    }
}

@Composable
private fun Details(cfg: WeatherWidgetConfig, w: Weather, f: Boolean) {
    val cells = listOf(
        "Ressenti" to fmtTemp(w.feelsC, f),
        "Humidité" to "${w.humidity}%",
        "Vent" to "${w.windKmh.roundToInt()} km/h ${windDirLabel(w.windDir)}",
        "Indice UV" to "%.0f".format(w.uv),
        "Lever" to w.sunrise,
        "Coucher" to w.sunset,
    )
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Header(cfg, w)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in cells.chunked(2)) {
                Row(Modifier.fillMaxWidth()) {
                    for ((k, v) in row) {
                        Column(Modifier.width(145.dp)) {
                            HText(k, HType.Caption.copy(fontSize = 11.sp), color = Color.White.copy(alpha = 0.65f))
                            HText(v, HType.Label, color = Color.White, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
