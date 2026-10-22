package fr.horizon.tv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.ui.graphics.vector.ImageVector
import fr.horizon.tv.news.NewsItem

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Accueil", Icons.Filled.Home),
    YOUTUBE("YouTube", Icons.Filled.PlayCircle),
    MUSIC("Musique", Icons.Filled.LibraryMusic),
    FILMS("Films", Icons.Filled.Movie),
    NEWS("Actus", Icons.Filled.Newspaper),
}

enum class SettingsSection(val label: String) {
    WEATHER("Météo"), NEWS("Actualités"), NAS("NAS & VLC"), APPS("Applications"), MUSIC("Spotify"), GENERAL("Général"),
}

sealed interface Screen {
    data class Article(val item: NewsItem) : Screen
    data class Web(val url: String, val title: String) : Screen
    data object Facebook : Screen
    data class YouTube(val videoUrl: String, val title: String) : Screen
    data class NasBrowse(val sourceId: String, val url: String, val title: String) : Screen
    data class NasPlay(val url: String, val title: String) : Screen
    data class Settings(val section: SettingsSection = SettingsSection.GENERAL) : Screen
    data object AllApps : Screen
    data object Inputs : Screen
}

object Router {
    var tab by mutableStateOf(Tab.HOME)
    val stack = mutableStateListOf<Screen>()

    val top: Screen? get() = stack.lastOrNull()

    fun push(s: Screen) { stack.add(s) }
    fun pop(): Boolean = stack.isNotEmpty().also { if (it) stack.removeAt(stack.lastIndex) }
    fun home() { stack.clear(); tab = Tab.HOME }
}
