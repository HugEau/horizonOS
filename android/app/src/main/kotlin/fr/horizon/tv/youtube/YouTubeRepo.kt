package fr.horizon.tv.youtube

import fr.horizon.tv.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

private class NpDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val data = request.dataToSend()
        val body = when {
            data != null -> data.toRequestBody()
            method in listOf("POST", "PUT", "PATCH") -> ByteArray(0).toRequestBody()
            else -> null
        }
        val b = okhttp3.Request.Builder().url(request.url()).method(method, body)
        b.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0")
        request.headers().forEach { (k, values) ->
            b.removeHeader(k)
            values.forEach { b.addHeader(k, it) }
        }
        client.newCall(b.build()).execute().use { r ->
            if (r.code == 429) throw ReCaptchaException("YouTube demande une vérification (429)", request.url())
            return Response(r.code, r.message, r.headers.toMultimap(), r.body?.string(), r.request.url.toString())
        }
    }
}

data class YtVideo(
    val url: String,
    val title: String,
    val channel: String,
    val thumb: String?,
    val durationSec: Long,
    val views: Long,
    val uploaded: String?,
    val live: Boolean = false,
)

class YtQuality(val height: Int, val label: String, val videoUrl: String, val mime: String?)

class YtStreams(
    val title: String,
    val channel: String,
    val qualities: List<YtQuality>,
    val audioUrl: String?,
    /** Flux 360p/720p avec son intégré : sert de repli si aucun couple vidéo+audio n'est exploitable. */
    val progressive: List<YtQuality>,
    val hlsUrl: String?,
    val related: List<YtVideo>,
)

object YouTubeRepo {
    private var ready = false

    private fun ensureInit() {
        if (ready) return
        // Pas de cache HTTP : les URLs de lecture YouTube expirent.
        NewPipe.init(NpDownloader(Http.client.newBuilder().cache(null).build()), Localization("fr", "FR"), ContentCountry("FR"))
        ready = true
    }

    private fun StreamInfoItem.toVideo() = YtVideo(
        url = url,
        title = name,
        channel = uploaderName.orEmpty(),
        thumb = thumbnails.maxByOrNull { it.height }?.url,
        durationSec = duration,
        views = viewCount,
        uploaded = textualUploadDate,
        live = streamType == StreamType.LIVE_STREAM,
    )

    private fun List<InfoItem>.videos() = filterIsInstance<StreamInfoItem>().filter { !it.isShortFormContent }.map { it.toVideo() }

    suspend fun trending(): List<YtVideo> = withContext(Dispatchers.IO) {
        ensureInit()
        val kiosk = ServiceList.YouTube.kioskList.defaultKioskExtractor
        kiosk.fetchPage()
        kiosk.initialPage.items.videos()
    }

    suspend fun search(query: String): List<YtVideo> = withContext(Dispatchers.IO) {
        ensureInit()
        val ex = ServiceList.YouTube.getSearchExtractor(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        ex.fetchPage()
        ex.initialPage.items.videos()
    }

    suspend fun resolve(url: String): YtStreams = withContext(Dispatchers.IO) {
        ensureInit()
        val info = StreamInfo.getInfo(ServiceList.YouTube, url)
        val live = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
        fun q(h: Int, u: String, f: MediaFormat?) = YtQuality(h, if (h > 0) "${h}p" else "Auto", u, f?.mimeType)

        val videoOnly = info.videoOnlyStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
        // Par hauteur : on privilégie H.264 (décodage matériel partout) puis VP9 pour 1440p et plus.
        val byHeight = videoOnly.groupBy { it.height }.mapNotNull { (h, list) ->
            val pick = list.filter { it.format == MediaFormat.MPEG_4 }.maxByOrNull { it.bitrate }
                ?: list.maxByOrNull { it.bitrate }
            pick?.let { q(h, it.content, it.format) }
        }.sortedByDescending { it.height }

        val audio = info.audioStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
            .let { l -> l.filter { it.format == MediaFormat.M4A }.ifEmpty { l } }
            .maxByOrNull { it.averageBitrate }

        val progressive = info.videoStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
            .map { q(it.height, it.content, it.format) }.sortedByDescending { it.height }

        YtStreams(
            title = info.name,
            channel = info.uploaderName.orEmpty(),
            qualities = if (live) emptyList() else byHeight,
            audioUrl = audio?.content,
            progressive = progressive,
            hlsUrl = info.hlsUrl.takeIf { live && !it.isNullOrBlank() },
            related = info.relatedItems.videos(),
        )
    }
}
