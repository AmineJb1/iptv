package com.example.iptv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

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