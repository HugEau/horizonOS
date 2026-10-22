package fr.horizon.tv.nas

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import fr.horizon.tv.data.Http
import fr.horizon.tv.data.NasSource
import fr.horizon.tv.data.SettingsStore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.MediaBrowser
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object Vlc {
    @Volatile private var lib: LibVLC? = null

    fun get(ctx: Context): LibVLC = lib ?: synchronized(this) {
        lib ?: LibVLC(
            ctx.applicationContext,
            listOf("--network-caching=3500", "--file-caching=3000", "--audio-time-stretch", "--avcodec-skiploopfilter=1"),
        ).also { lib = it }
    }
}

data class NasEntry(val name: String, val url: String, val isDir: Boolean)

@Serializable
data class Resume(val url: String, val title: String, val pos: Long, val dur: Long, val ts: Long)

/** Dossier en cours de navigation : sert à enchaîner l'épisode suivant dans le lecteur. */
object NasQueue {
    var files: List<NasEntry> = emptyList()
}

object NasRepo {
    private val VIDEO = setOf(
        "mkv", "mp4", "avi", "mov", "m4v", "wmv", "ts", "m2ts", "mts", "mpg", "mpeg", "flv", "webm", "vob", "3gp", "divx", "ogv", "iso",
    )

    /** Retire d'éventuels identifiants de l'URL : on ne les garde ni dans l'historique ni dans la navigation. */
    fun clean(url: String): String {
        val u = Uri.parse(url)
        val info = u.userInfo ?: return url
        return url.replaceFirst("$info@", "")
    }

    private fun sourceFor(url: String): NasSource? {
        val host = Uri.parse(url).host?.lowercase() ?: return null
        return SettingsStore.state.value.nas.firstOrNull { Uri.parse(it.url).host?.lowercase() == host }
    }

    /** URL avec identifiants (si la source en a). */
    fun authed(url: String): String {
        val s = sourceFor(url) ?: return url
        if (s.user.isBlank() || Uri.parse(url).userInfo != null) return url
        val cred = Uri.encode(s.user) + if (s.password.isNotEmpty()) ":" + Uri.encode(s.password) else ""
        return url.replaceFirst("://", "://$cred@")
    }

    fun prettyName(raw: String, isDir: Boolean): String {
        if (isDir) return raw
        return raw.substringBeforeLast('.').replace('.', ' ').replace('_', ' ').trim()
    }

    suspend fun list(ctx: Context, url: String): List<NasEntry> = withTimeout(25_000) {
        suspendCancellableCoroutine { cont ->
            val main = Handler(Looper.getMainLooper())
            main.post {
                val found = ArrayList<NasEntry>()
                var browser: MediaBrowser? = null
                var done = false
                fun finish(block: () -> Unit) {
                    if (done) return
                    done = true
                    block()
                    main.post { browser?.release() }
                }
                browser = MediaBrowser(Vlc.get(ctx), object : MediaBrowser.EventListener {
                    override fun onMediaAdded(index: Int, media: IMedia) {
                        val u = clean(media.uri.toString())
                        val raw = media.getMeta(IMedia.Meta.Title)?.takeIf { it.isNotBlank() }
                            ?: Uri.decode(u.trimEnd('/').substringAfterLast('/'))
                        val isDir = media.type == IMedia.Type.Directory
                        val name = raw.trimEnd('/')
                        if (name.startsWith(".") || name.startsWith("@") || name.startsWith("#")) return
                        if (isDir || name.substringAfterLast('.', "").lowercase() in VIDEO) found += NasEntry(name, u, isDir)
                    }
                    override fun onMediaRemoved(index: Int, media: IMedia) {}
                    override fun onBrowseEnd() = finish {
                        cont.resume(
                            found.sortedWith(compareByDescending<NasEntry> { it.isDir }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }),
                        )
                    }
                }, main)
                cont.invokeOnCancellation { main.post { done = true; browser?.release() } }
                runCatching { browser.browse(Uri.parse(authed(url)), 0) }
                    .onFailure { finish { cont.resumeWithException(it) } }
            }
        }
    }

    // --- Reprise de lecture ---

    private val prefs get() = SettingsStore.prefs

    fun resumes(): List<Resume> = runCatching {
        Http.json.decodeFromString<List<Resume>>(prefs.getString("resume", "[]")!!)
    }.getOrDefault(emptyList()).sortedByDescending { it.ts }

    fun resumeFor(url: String): Resume? = resumes().firstOrNull { it.url == clean(url) }

    fun saveResume(url: String, title: String, pos: Long, dur: Long) {
        val key = clean(url)
        val rest = resumes().filter { it.url != key }
        val finished = dur > 0 && pos > dur * 0.95
        val list = if (finished || pos < 30_000) rest else listOf(Resume(key, title, pos, dur, System.currentTimeMillis())) + rest
        prefs.edit().putString("resume", Http.json.encodeToString(list.take(30))).apply()
    }
}
