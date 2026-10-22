package fr.horizon.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object Http {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

    lateinit var client: OkHttpClient
        private set

    fun init(cacheDir: File) {
        client = OkHttpClient.Builder()
            .cache(Cache(File(cacheDir, "http"), 50L * 1024 * 1024))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 12; Horizon TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(req).execute().use {
            check(it.isSuccessful) { "HTTP ${it.code} pour $url" }
            it.body?.string().orEmpty()
        }
    }
}
