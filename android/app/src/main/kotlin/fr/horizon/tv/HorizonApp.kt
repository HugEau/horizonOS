package fr.horizon.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import fr.horizon.tv.data.Http
import fr.horizon.tv.data.SettingsStore

class HorizonApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Http.init(cacheDir)
        SettingsStore.init(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(Http.client)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()
}
