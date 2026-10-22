package fr.horizon.tv.news

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import fr.horizon.tv.data.Http
import fr.horizon.tv.data.SettingsStore
import fr.horizon.tv.ui.FocusCard
import fr.horizon.tv.ui.HColors
import fr.horizon.tv.ui.HText
import fr.horizon.tv.ui.HType
import fr.horizon.tv.ui.PivotScroll
import fr.horizon.tv.ui.RemoteImage
import fr.horizon.tv.ui.Router
import fr.horizon.tv.ui.Screen
import fr.horizon.tv.ui.SettingsSection
import fr.horizon.tv.ui.TvButton
import fr.horizon.tv.ui.TvTextField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val FACEBOOK_SEARCH = "https://www.facebook.com/search/pages/?q=Tu%20sais%20que%20tu%20es%20d%27Honfleur"

private val FALLBACKS = listOf(
    0xFF3D5A98, 0xFF8E3B6B, 0xFF2F7A6F, 0xFFB5543A, 0xFF4B4F9E, 0xFF6B7A2F,
)

private fun fallbackBrush(seed: String): Brush {
    val c = Color(FALLBACKS[(seed.hashCode() and 0x7fffffff) % FALLBACKS.size])
    return Brush.linearGradient(listOf(c, c.copy(alpha = 1f).let { Color(it.red * 0.35f, it.green * 0.35f, it.blue * 0.35f) }))
}

@Composable
fun NewsCard(item: NewsItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusCard(onClick, modifier.size(width = 300.dp, height = 188.dp), shape = RoundedCornerShape(18.dp)) {
        Box(Modifier.fillMaxSize().background(fallbackBrush(item.source))) {
            item.imageUrl?.let { RemoteImage(it, Modifier.fillMaxSize()) }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)), startY = 120f),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HText(item.source.uppercase(), HType.Caption.copy(letterSpacing = 1.sp), color = HColors.Accent, maxLines = 1)
                HText(item.title, HType.Body.copy(fontSize = 16.sp, lineHeight = 20.sp), maxLines = 3, color = Color.White)
                HText(relativeTime(item.publishedMs), HType.Caption, maxLines = 1)
            }
        }
    }
}

/** « À la une » : grand bandeau qui défile tout seul (comme le Top Shelf de l'Apple TV). */
@Composable
fun NewsHero(items: List<NewsItem>, modifier: Modifier = Modifier, onClick: (NewsItem) -> Unit) {
    var index by remember { mutableStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(items.size, focused) {
        while (items.size > 1 && !focused) {
            delay(9_000)
            index = (index + 1) % items.size
        }
    }
    val item = items.getOrNull(index % items.size.coerceAtLeast(1)) ?: return
    FocusCard(
        onClick = { onClick(item) },
        modifier = modifier.fillMaxWidth().height(232.dp).onFocusChanged { focused = it.hasFocus },
        shape = RoundedCornerShape(26.dp),
        focusedScale = 1.02f,
    ) {
        androidx.compose.animation.Crossfade(item, animationSpec = androidx.compose.animation.core.tween(700), label = "hero") { it ->
            Box(Modifier.fillMaxSize().background(fallbackBrush(it.source))) {
                it.imageUrl?.let { u -> RemoteImage(u, Modifier.fillMaxSize()) }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.82f), Color.Black.copy(alpha = 0.25f), Color.Transparent)),
                    ),
                )
                Column(
                    Modifier.align(Alignment.CenterStart).padding(start = 36.dp).width(560.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HText("À LA UNE  ·  ${it.source.uppercase()}", HType.Caption.copy(letterSpacing = 1.5.sp), color = HColors.Accent)
                    HText(it.title, HType.Title.copy(fontSize = 30.sp, lineHeight = 36.sp), maxLines = 3)
                    HText(relativeTime(it.publishedMs), HType.Label)
                }
                Row(Modifier.align(Alignment.BottomEnd).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.take(8).forEachIndexed { i, _ ->
                        Box(Modifier.size(if (i == index) 18.dp else 7.dp, 7.dp).background(Color.White.copy(alpha = if (i == index) 0.95f else 0.4f), CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
fun NewsTab() {
    val items by NewsRepo.items.collectAsState()
    val loading by NewsRepo.loading.collectAsState()
    val settings by SettingsStore.state.collectAsState()
    var filter by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { NewsRepo.refresh() }
    val sources = items.map { it.sourceId to it.source }.distinct()
    val shown = if (filter == null) items else items.filter { it.sourceId == filter }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 48.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip("Tout", filter == null) { filter = null }
            sources.forEach { (id, name) -> Chip(name, filter == id) { filter = id } }
            Spacer(Modifier.weight(1f))
            TvButton(if (loading) "Actualisation…" else "Actualiser", { scope.launch { NewsRepo.refresh(true) } }, icon = Icons.Filled.Refresh)
            TvButton("Sources", { Router.push(Screen.Settings(SettingsSection.NEWS)) }, icon = Icons.Filled.Settings)
        }
        PivotScroll(startPadding = 90.dp) {
            LazyVerticalGrid(
                GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 60.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(span = { GridItemSpan(3) }) {
                    FacebookBanner(settings.facebookRssUrl.isNotBlank())
                }
                items(shown, key = { it.id }) { n ->
                    NewsCard(n, Modifier.fillMaxWidth().height(200.dp)) { openArticle(n) }
                }
            }
        }
    }
}

fun openArticle(n: NewsItem) {
    if (n.body.isNotBlank()) Router.push(Screen.Article(n)) else Router.push(Screen.Web(n.link, n.source))
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusCard(
        onClick, Modifier.height(38.dp), shape = CircleShape, focusedScale = 1.06f,
        background = if (selected) Color.White.copy(alpha = 0.92f) else HColors.Glass,
    ) {
        Box(Modifier.padding(horizontal = 18.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            HText(label, HType.Label, color = if (selected) HColors.Ink else Color.White, maxLines = 1)
        }
    }
}

@Composable
private fun FacebookBanner(connected: Boolean) {
    FocusCard(
        onClick = { Router.push(Screen.Facebook) },
        modifier = Modifier.fillMaxWidth().height(86.dp),
        shape = RoundedCornerShape(18.dp),
        focusedScale = 1.015f,
    ) {
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF1877F2), Color(0xFF0B3C8C))))) {
            Row(Modifier.align(Alignment.CenterStart).padding(horizontal = 26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HText("f", HType.Display.copy(fontSize = 44.sp))
                Column {
                    HText("Tu sais que tu es d'Honfleur", HType.Title)
                    HText(
                        if (connected) "Publications intégrées au fil d'actus · OK pour ouvrir la page"
                        else "Page Facebook · OK pour l'ouvrir ou scanner le QR code",
                        HType.Label, color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

@Composable
fun ArticleReader(item: NewsItem) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(20.dp)).background(fallbackBrush(item.source))) {
                item.imageUrl?.let { RemoteImage(it, Modifier.fillMaxSize()) }
            }
            HText(item.source.uppercase(), HType.Caption.copy(letterSpacing = 1.5.sp), color = HColors.Accent)
            HText(relativeTime(item.publishedMs), HType.Label)
            TvButton("Ouvrir la page", { Router.push(Screen.Web(item.link, item.source)) }, icon = Icons.Filled.OpenInBrowser, primary = true)
            TvButton("Fermer", { Router.pop() })
        }
        Column(
            Modifier.weight(1f).fillMaxHeight()
                .focusRequester(focus)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionDown -> { scope.launch { scroll.animateScrollBy(320f) }; true }
                        Key.DirectionUp -> if (scroll.value > 0) { scope.launch { scroll.animateScrollBy(-320f) }; true } else false
                        else -> false
                    }
                }
                .focusable()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HText(item.title, HType.Display.copy(fontSize = 34.sp, lineHeight = 40.sp))
            HText(item.body.ifBlank { item.summary }, HType.Body.copy(fontSize = 19.sp, lineHeight = 29.sp), color = Color.White.copy(alpha = 0.9f))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(url: String, title: String) {
    var web by remember { mutableStateOf<WebView?>(null) }
    BackHandler { if (web?.canGoBack() == true) web?.goBack() else Router.pop() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(AColor.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = Http.USER_AGENT
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    isFocusable = true
                    isFocusableInTouchMode = true
                    loadUrl(url)
                    web = this
                    requestFocus()
                }
            },
            onRelease = { it.stopLoading(); it.destroy() },
        )
    }
}

@Composable
fun FacebookScreen() {
    val settings by SettingsStore.state.collectAsState()
    val pageUrl = settings.facebookUrl.ifBlank { FACEBOOK_SEARCH }
    val qr = remember(pageUrl) { qrBitmap(pageUrl, 420) }
    var draft by remember(settings.facebookUrl) { mutableStateOf(settings.facebookUrl) }
    Row(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 48.dp), horizontalArrangement = Arrangement.spacedBy(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            HText("Tu sais que tu es d'Honfleur", HType.Display)
            HText(
                "Facebook ne propose pas de flux public : les publications ne peuvent pas être lues automatiquement sans compte. " +
                    "Scanne le QR code avec ton téléphone, ou ouvre la page ici.",
                HType.Body.copy(fontSize = 18.sp, lineHeight = 26.sp), color = HColors.TextDim,
            )
            TvTextField(draft, { draft = it }, "Lien de la page Facebook", placeholder = "https://www.facebook.com/…", imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                onDone = { SettingsStore.update { it.copy(facebookUrl = draft.trim()) } })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Ouvrir ici", { Router.push(Screen.Web(pageUrl, "Facebook")) }, icon = Icons.Filled.OpenInBrowser, primary = true)
                TvButton("Enregistrer le lien", { SettingsStore.update { it.copy(facebookUrl = draft.trim()) } })
                TvButton("Retour", { Router.pop() })
            }
            HText(
                "Astuce : un pont RSS (RSS-Bridge, FetchRSS…) permet d'intégrer la page au fil d'actus : Réglages › Actualités.",
                HType.Caption,
            )
        }
        Box(Modifier.size(260.dp).background(Color.White, RoundedCornerShape(20.dp)).padding(14.dp)) {
            Image(qr.asImageBitmap(), "QR code", Modifier.fillMaxSize())
        }
    }
}

private fun qrBitmap(text: String, size: Int): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(com.google.zxing.EncodeHintType.MARGIN to 0))
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (m[x, y]) AColor.BLACK else AColor.WHITE)
    return bmp
}
