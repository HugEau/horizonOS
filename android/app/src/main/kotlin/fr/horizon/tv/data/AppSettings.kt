package fr.horizon.tv.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.UUID

@Serializable
enum class WeatherStyle(val label: String) {
    CURRENT("Actuelle"),
    HOURLY("Heure par heure"),
    DAILY("7 jours"),
    DETAILS("Détails"),
}

@Serializable
enum class WidgetTone(val label: String, val top: Long, val bottom: Long) {
    AUTO("Auto (selon la météo)", 0, 0),
    OCEAN("Océan", 0xFF2B6CB0, 0xFF1A365D),
    SUNSET("Couchant", 0xFFF6875A, 0xFF7B2D5B),
    FOREST("Forêt", 0xFF38A169, 0xFF1C4532),
    GRAPHITE("Graphite", 0xFF4A5568, 0xFF171923),
}

@Serializable
data class WeatherWidgetConfig(
    val id: String = UUID.randomUUID().toString(),
    val city: String,
    val lat: Double,
    val lon: Double,
    val style: WeatherStyle = WeatherStyle.CURRENT,
    val tone: WidgetTone = WidgetTone.AUTO,
)

@Serializable
data class NasSource(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Ex. smb://192.168.1.20/films, nfs://nas/volume1/video, ftp://nas, sftp://nas, http://nas:8080/ */
    val url: String,
    val user: String = "",
    val password: String = "",
)

@Serializable
data class NewsSource(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val builtin: Boolean = false,
)

@Serializable
data class AppSettings(
    val fahrenheit: Boolean = false,
    val weather: List<WeatherWidgetConfig> = listOf(
        WeatherWidgetConfig(id = "honfleur-now", city = "Honfleur", lat = 49.4189, lon = 0.2333),
        WeatherWidgetConfig(
            id = "honfleur-hourly", city = "Honfleur", lat = 49.4189, lon = 0.2333,
            style = WeatherStyle.HOURLY,
        ),
        WeatherWidgetConfig(
            id = "honfleur-week", city = "Honfleur", lat = 49.4189, lon = 0.2333,
            style = WeatherStyle.DAILY,
        ),
    ),
    val news: List<NewsSource> = defaultNews(),
    /** Lien de la page Facebook « Tu sais que tu es d'Honfleur » (à coller une fois). */
    val facebookUrl: String = "",
    /** Flux RSS issu d'un pont (RSS-Bridge, FetchRSS…) pour afficher la page Facebook dans les actus. */
    val facebookRssUrl: String = "",
    val nas: List<NasSource> = emptyList(),
    /** Paquets épinglés dans le dock ; vide = sélection par défaut. */
    val pinnedApps: List<String> = emptyList(),
)

private const val GN = "https://news.google.com/rss/search?hl=fr&gl=FR&ceid=FR:fr&q="

fun defaultNews() = listOf(
    NewsSource("honfleur-infos", "Honfleur-Infos", "https://www.honfleur-infos.com/feed/", builtin = true),
    NewsSource(
        "pays-auge", "Le Pays d'Auge",
        GN + "(Honfleur+OR+%22Pays+d%27Auge%22)+site:actu.fr+when:14d", builtin = true,
    ),
    NewsSource("presse-regionale", "Presse régionale", GN + "Honfleur+when:7d", builtin = true),
)

object SettingsStore {
    lateinit var prefs: SharedPreferences
        private set
    private val _state = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> = _state

    fun init(context: Context) {
        prefs = context.getSharedPreferences("horizon", Context.MODE_PRIVATE)
        prefs.getString("settings", null)?.let {
            runCatching { _state.value = Http.json.decodeFromString<AppSettings>(it) }
        }
    }

    fun update(block: (AppSettings) -> AppSettings) {
        _state.value = block(_state.value)
        prefs.edit().putString("settings", Http.json.encodeToString(_state.value)).apply()
    }
}
