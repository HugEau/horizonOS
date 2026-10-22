package fr.horizon.tv.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TvApp(val pkg: String, val label: String, val image: ImageBitmap, val isBanner: Boolean, val intent: Intent)

object AppsRepo {
    /** Applications que l'on épingle par défaut dans le dock, si elles sont installées. */
    val DEFAULT_PINNED = listOf(
        "com.netflix.ninja",
        "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus",
        "com.canal.android.canal",
        "tv.molotov.app",
        "com.apple.atve.androidtv.appletv",
        "fr.francetv.pluzz",
        "fr.tf1.mytf1",
        "com.google.android.youtube.tv",
    )

    const val SPOTIFY_TV = "com.spotify.tv.android"
    const val SPOTIFY_PHONE = "com.spotify.music"

    suspend fun load(ctx: Context): List<TvApp> = withContext(Dispatchers.Default) {
        val pm = ctx.packageManager
        fun query(category: String) = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
        val seen = HashSet<String>()
        (query(Intent.CATEGORY_LEANBACK_LAUNCHER) + query(Intent.CATEGORY_LAUNCHER))
            .filter { it.activityInfo.packageName != ctx.packageName && seen.add(it.activityInfo.packageName) }
            .mapNotNull { ri ->
                runCatching {
                    val pkg = ri.activityInfo.packageName
                    val banner = ri.activityInfo.loadBanner(pm)
                    val drawable = banner ?: ri.loadIcon(pm)
                    val bmp = if (banner != null) drawable.toBitmap(320, 180) else drawable.toBitmap(160, 160)
                    TvApp(
                        pkg, ri.loadLabel(pm).toString(), bmp.asImageBitmap(), banner != null,
                        (pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg))!!
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }

    fun isInstalled(ctx: Context, pkg: String): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    fun launch(ctx: Context, app: TvApp) {
        runCatching { ctx.startActivity(app.intent) }
    }

    /** Lance Spotify, ou ouvre sa fiche Play Store s'il n'est pas installé. */
    fun openSpotify(ctx: Context): Boolean {
        val pm = ctx.packageManager
        val intent = listOf(SPOTIFY_TV, SPOTIFY_PHONE).firstNotNullOfOrNull {
            pm.getLeanbackLaunchIntentForPackage(it) ?: pm.getLaunchIntentForPackage(it)
        }
        val target = intent ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$SPOTIFY_TV"))
        return runCatching { ctx.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess && intent != null
    }
}
