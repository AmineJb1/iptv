package com.example.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun Initial(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase(), color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun Logo(url: String, name: String) {
    val ctx = LocalContext.current
    var ok by remember(url) { mutableStateOf(false) }
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)) {
        if (!ok) Initial(name)
        if (url.startsWith("http")) {
            AsyncImage(
                model = remember(url) { ImageRequest.Builder(ctx).data(url).size(128).crossfade(true).build() },
                contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                onSuccess = { ok = true }
            )
        }
    }
}

@Composable
fun ItemRow(title: String, sub: String, logo: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(shape)
            .background(if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .border(if (focused) 3.dp else 1.dp, if (focused) MaterialTheme.colorScheme.primary else Color(0x22000000), shape)
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