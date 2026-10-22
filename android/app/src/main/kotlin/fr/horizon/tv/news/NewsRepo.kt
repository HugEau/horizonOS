package fr.horizon.tv.news

import android.text.Html
import android.util.Xml
import fr.horizon.tv.data.Http
import fr.horizon.tv.data.NewsSource
import fr.horizon.tv.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class NewsItem(
    val id: String,
    val title: String,
    val link: String,
    val source: String,
    val sourceId: String,
    val publishedMs: Long,
    val summary: String,
    /** Texte complet quand le flux le fournit (vide pour Google Actualités). */
    val body: String,
    val imageUrl: String?,
)

object NewsRepo {
    private val _items = MutableStateFlow<List<NewsItem>>(emptyList())
    val items: StateFlow<List<NewsItem>> = _items
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private var lastRefresh = 0L

    suspend fun refresh(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - lastRefresh < 10 * 60_000L && _items.value.isNotEmpty()) return
        _loading.value = true
        try {
            val s = SettingsStore.state.value
            val sources = s.news.filter { it.enabled } +
                (if (s.facebookRssUrl.isNotBlank()) listOf(NewsSource("facebook", "Tu sais que tu es d'Honfleur", s.facebookRssUrl.trim())) else emptyList())
            val all = coroutineScope {
                sources.map { src -> async(Dispatchers.IO) { runCatching { fetch(src) }.getOrDefault(emptyList()) } }.awaitAll()
            }.flatten()
            val seen = HashSet<String>()
            _items.value = all.sortedByDescending { it.publishedMs }
                .filter { seen.add(it.title.lowercase().filter(Char::isLetterOrDigit).take(60)) }
                .take(60)
            lastRefresh = System.currentTimeMillis()
        } finally {
            _loading.value = false
        }
        enrichImages()
    }

    private suspend fun fetch(src: NewsSource): List<NewsItem> {
        val xml = Http.get(src.url)
        return parse(xml, src).take(30)
    }

    private fun parse(xml: String, src: NewsSource): List<NewsItem> {
        val p = Xml.newPullParser()
        p.setInput(StringReader(xml))
        val out = ArrayList<NewsItem>()
        var cur: MutableMap<String, String>? = null
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val n = p.name
                    if (n == "item" || n == "entry") cur = HashMap()
                    else if (cur != null) {
                        when (n) {
                            "link" -> {
                                val href = p.getAttributeValue(null, "href")
                                if (href != null) { if (p.getAttributeValue(null, "rel") in listOf(null, "alternate")) cur["link"] = href }
                                else cur["link"] = p.nextText()
                            }
                            "enclosure", "media:content", "media:thumbnail" -> {
                                val type = p.getAttributeValue(null, "type").orEmpty()
                                val medium = p.getAttributeValue(null, "medium").orEmpty()
                                if (cur["image"] == null && (n == "media:thumbnail" || type.startsWith("image") || medium == "image"))
                                    p.getAttributeValue(null, "url")?.let { cur["image"] = it }
                            }
                            "title", "pubDate", "published", "updated", "dc:date", "description", "summary",
                            "content:encoded", "content", "source" -> cur[n] = runCatching { p.nextText() }.getOrDefault("")
                        }
                    }
                }
                XmlPullParser.END_TAG -> if ((p.name == "item" || p.name == "entry") && cur != null) {
                    toItem(cur, src)?.let(out::add)
                    cur = null
                }
            }
            event = p.next()
        }
        return out
    }

    private fun toItem(m: Map<String, String>, src: NewsSource): NewsItem? {
        val link = m["link"]?.trim().orEmpty()
        var title = text(m["title"].orEmpty())
        if (link.isEmpty() || title.isEmpty()) return null
        var source = src.name
        // Google Actualités : « Titre - Média ». On garde le média comme source.
        val gSource = m["source"]?.let(::text)
        if (gSource != null && gSource.isNotBlank()) {
            title = title.removeSuffix(" - $gSource")
            source = if (src.id == "pays-auge" || src.id == "honfleur-infos") src.name else gSource
        }
        val html = m["content:encoded"] ?: m["content"] ?: m["description"] ?: m["summary"].orEmpty()
        val isGoogle = link.contains("news.google.com")
        val summaryText = if (isGoogle) "" else text(m["description"] ?: m["summary"] ?: html)
        val body = if (isGoogle) "" else paragraphs(m["content:encoded"] ?: m["content"] ?: "")
        val image = m["image"] ?: Regex("""<img[^>]+src=["']([^"']+)["']""").find(html)?.groupValues?.get(1)
        return NewsItem(
            id = link,
            title = title,
            link = link,
            source = source,
            sourceId = src.id,
            publishedMs = date(m["pubDate"] ?: m["published"] ?: m["updated"] ?: m["dc:date"]),
            summary = summaryText.removeSuffix("[…]").trim().take(400),
            body = body,
            imageUrl = image,
        )
    }

    private fun text(html: String) =
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().replace('\uFFFC', ' ').replace(Regex("\\s+"), " ").trim()

    private fun paragraphs(html: String): String {
        if (html.isBlank()) return ""
        val withBreaks = html.replace(Regex("(?i)</p>|<br\\s*/?>|</h[1-6]>|</li>"), "\n\n")
        return Html.fromHtml(withBreaks, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace('\uFFFC', ' ').lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")
    }

    private fun date(s: String?): Long {
        val v = s?.trim() ?: return System.currentTimeMillis()
        return runCatching { ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(v).toInstant().toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())
    }

    /** Beaucoup de flux n'ont pas d'image : on lit la balise og:image de la page (articles directs seulement). */
    private suspend fun enrichImages() {
        val missing = _items.value.filter { it.imageUrl == null && !it.link.contains("news.google.com") }.take(24)
        if (missing.isEmpty()) return
        val gate = Semaphore(4)
        val found = coroutineScope {
            missing.map { item ->
                async(Dispatchers.IO) {
                    gate.withPermit { item.id to runCatching { ogImage(item.link) }.getOrNull() }
                }
            }.awaitAll().toMap()
        }
        _items.value = _items.value.map { if (it.imageUrl == null && found[it.id] != null) it.copy(imageUrl = found[it.id]) else it }
    }

    private suspend fun ogImage(url: String): String? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", Http.USER_AGENT).build()
        Http.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@use null
            val head = r.body?.source()?.let { src ->
                src.request(96 * 1024)
                src.buffer.snapshot().utf8()
            }.orEmpty()
            Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""").find(head)?.groupValues?.get(1)
                ?: Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']""").find(head)?.groupValues?.get(1)
        }
    }
}

fun relativeTime(ms: Long): String {
    val min = (System.currentTimeMillis() - ms) / 60_000
    return when {
        min < 2 -> "à l'instant"
        min < 60 -> "il y a $min min"
        min < 60 * 24 -> "il y a ${min / 60} h"
        min < 60 * 24 * 7 -> "il y a ${min / (60 * 24)} j"
        else -> java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM", java.util.Locale.FRENCH))
    }
}
