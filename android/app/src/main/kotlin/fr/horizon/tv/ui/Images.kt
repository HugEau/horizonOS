package fr.horizon.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun RemoteImage(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val ctx = LocalContext.current
    val request = remember(url) { ImageRequest.Builder(ctx).data(url).crossfade(350).build() }
    AsyncImage(model = request, contentDescription = null, modifier = modifier, contentScale = contentScale)
}
