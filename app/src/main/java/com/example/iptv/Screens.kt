package com.example.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage

@Composable
fun Initial(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase(), color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun Logo(url: String, name: String) {
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface)) {
        if (url.startsWith("http")) {
            SubcomposeAsyncImage(
                model = url, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(3.dp),
                loading = { Initial(name) }, error = { Initial(name) }
            )
        } else Initial(name)
    }
}

@Composable
fun ItemRow(title: String, sub: String, logo: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(shape)
            .background(if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .border(if (focused) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Logo(logo, title)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ItemList(
    list: List<Channel>, group: String?, query: String,
    onGroup: (String) -> Unit, onPick: (Channel) -> Unit, modifier: Modifier = Modifier
) {
    val groups = remember(list) { list.groupBy { it.group.ifBlank { "Autres" } } }
    val found = remember(list, query) {
        if (query.isBlank()) emptyList() else list.filter { it.name.contains(query, true) }
    }
    LazyColumn(modifier) {
        if (query.isNotBlank()) {
                        itemsIndexed(found, key = { i, c -> "$i${c.url}" }) { _, c -> ItemRow(c.name, c.group, c.logo) { onPick(c) } }
        } else if (group == null) {
            val gl = groups.entries.toList()
            itemsIndexed(gl, key = { _, e -> e.key }) { _, e -> ItemRow(e.key, "${e.value.size}", "") { onGroup(e.key) } }
        } else {
            val l = groups[group] ?: emptyList()
            itemsIndexed(l, key = { i, c -> "$i${c.url}" }) { _, c -> ItemRow(c.name, "", c.logo) { onPick(c) } }
        }
    }
}

@Composable
fun Player(url: String) {
    val ctx = LocalContext.current
    val player = remember { ExoPlayer.Builder(ctx).build() }
    LaunchedEffect(url) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player; keepScreenOn = true } },
        modifier = Modifier.fillMaxSize()
    )
}