package fr.horizon.tv.weather

import fr.horizon.tv.data.Http
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

data class HourPoint(val time: LocalDateTime, val tempC: Double, val code: Int, val rainProb: Int, val isDay: Boolean)
data class DayPoint(val date: LocalDate, val code: Int, val minC: Double, val maxC: Double, val rainProb: Int)

data class Weather(
    val tempC: Double,
    val feelsC: Double,
    val humidity: Int,
    val code: Int,
    val isDay: Boolean,
    val windKmh: Double,
    val windDir: Int,
    val hourly: List<HourPoint>,
    val daily: List<DayPoint>,
    val sunrise: String,
    val sunset: String,
    val uv: Double,
)

data class GeoPlace(val name: String, val region: String, val lat: Double, val lon: Double) {
    val label get() = if (region.isBlank()) name else "$name, $region"
}

enum class WxKind { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

fun wxKind(code: Int) = when (code) {
    0 -> WxKind.CLEAR
    1, 2 -> WxKind.PARTLY
    3 -> WxKind.CLOUDY
    45, 48 -> WxKind.FOG
    in 51..57 -> WxKind.DRIZZLE
    in 61..67, in 80..82 -> WxKind.RAIN
    in 71..77, 85, 86 -> WxKind.SNOW
    in 95..99 -> WxKind.STORM
    else -> WxKind.CLOUDY
}

fun wxLabel(code: Int) = when (code) {
    0 -> "Ciel dégagé"
    1 -> "Plutôt dégagé"
    2 -> "Éclaircies"
    3 -> "Couvert"
    45, 48 -> "Brouillard"
    51, 53, 55 -> "Bruine"
    56, 57 -> "Bruine verglaçante"
    61 -> "Pluie faible"
    63 -> "Pluie"
    65 -> "Forte pluie"
    66, 67 -> "Pluie verglaçante"
    71, 73, 75, 77 -> "Neige"
    80, 81, 82 -> "Averses"
    85, 86 -> "Averses de neige"
    95 -> "Orage"
    96, 99 -> "Orage et grêle"
    else -> "Nuageux"
}

object WeatherRepo {
    private class Entry(val at: Long, val weather: Weather)

    private val cache = ConcurrentHashMap<String, Entry>()
    private const val TTL_MS = 10 * 60 * 1000L

    suspend fun load(lat: Double, lon: Double, force: Boolean = false): Weather {
        val key = "%.3f,%.3f".format(lat, lon)
        cache[key]?.let { if (!force && System.currentTimeMillis() - it.at < TTL_MS) return it.weather }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,is_day" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max,uv_index_max" +
            "&timezone=auto&forecast_days=7"
        val w = parse(Json.parseToJsonElement(Http.get(url)).jsonObject)
        cache[key] = Entry(System.currentTimeMillis(), w)
        return w
    }

    private fun parse(root: JsonObject): Weather {
        val cur = root["current"]!!.jsonObject
        val h = root["hourly"]!!.jsonObject
        val d = root["daily"]!!.jsonObject
        fun JsonObject.arr(k: String): JsonArray = this[k]!!.jsonArray

        val now = LocalDateTime.parse(cur["time"]!!.jsonPrimitive.content)
        val ht = h.arr("time"); val hTemp = h.arr("temperature_2m"); val hCode = h.arr("weather_code")
        val hRain = h.arr("precipitation_probability"); val hDay = h.arr("is_day")
        val hours = ht.indices.map {
            HourPoint(
                LocalDateTime.parse(ht[it].jsonPrimitive.content),
                hTemp[it].jsonPrimitive.doubleOrNull ?: 0.0,
                hCode[it].jsonPrimitive.intOrNull ?: 0,
                hRain[it].jsonPrimitive.intOrNull ?: 0,
                (hDay[it].jsonPrimitive.intOrNull ?: 1) == 1,
            )
        }.filter { !it.time.isBefore(now.withMinute(0)) }.take(24)

        val dt = d.arr("time"); val dCode = d.arr("weather_code"); val dMax = d.arr("temperature_2m_max")
        val dMin = d.arr("temperature_2m_min"); val dRain = d.arr("precipitation_probability_max")
        val days = dt.indices.map {
            DayPoint(
                LocalDate.parse(dt[it].jsonPrimitive.content),
                dCode[it].jsonPrimitive.intOrNull ?: 0,
                dMin[it].jsonPrimitive.doubleOrNull ?: 0.0,
                dMax[it].jsonPrimitive.doubleOrNull ?: 0.0,
                dRain[it].jsonPrimitive.intOrNull ?: 0,
            )
        }
        fun hm(s: String?) = s?.substringAfter('T')?.take(5).orEmpty()
        return Weather(
            tempC = cur["temperature_2m"]!!.jsonPrimitive.double,
            feelsC = cur["apparent_temperature"]!!.jsonPrimitive.double,
            humidity = cur["relative_humidity_2m"]!!.jsonPrimitive.int,
            code = cur["weather_code"]!!.jsonPrimitive.int,
            isDay = cur["is_day"]!!.jsonPrimitive.int == 1,
            windKmh = cur["wind_speed_10m"]!!.jsonPrimitive.double,
            windDir = cur["wind_direction_10m"]!!.jsonPrimitive.int,
            hourly = hours,
            daily = days,
            sunrise = hm(d.arr("sunrise").firstOrNull()?.jsonPrimitive?.contentOrNull),
            sunset = hm(d.arr("sunset").firstOrNull()?.jsonPrimitive?.contentOrNull),
            uv = d.arr("uv_index_max").firstOrNull()?.jsonPrimitive?.doubleOrNull ?: 0.0,
        )
    }

    suspend fun searchPlaces(query: String): List<GeoPlace> {
        if (query.length < 2) return emptyList()
        val q = URLEncoder.encode(query, "UTF-8")
        val root = Json.parseToJsonElement(
            Http.get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=fr&format=json"),
        ).jsonObject
        return root["results"]?.jsonArray?.map {
            val o = it.jsonObject
            GeoPlace(
                o["name"]!!.jsonPrimitive.content,
                listOfNotNull(o["admin1"]?.jsonPrimitive?.contentOrNull, o["country"]?.jsonPrimitive?.contentOrNull)
                    .joinToString(", "),
                o["latitude"]!!.jsonPrimitive.double,
                o["longitude"]!!.jsonPrimitive.double,
            )
        }.orEmpty()
    }
}

fun windDirLabel(deg: Int): String =
    listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")[((deg + 22) / 45) % 8]
