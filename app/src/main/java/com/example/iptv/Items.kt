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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun Initial(name: String) {
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFE11D48), Color(0xFF7C1230)))),
        contentAlignment = Alignment.Center
    ) {
        Text(name.trim().take(1).uppercase(), color = Color.White,
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Logo(url: String, name: String) {
    val ctx = LocalContext.current
    var ok by remember(url) { mutableStateOf(false) }
    Box(Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF22222C))) {
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
    val shape = RoundedCornerShape(14.dp)
    val c = MaterialTheme.colorScheme
    val isCount = sub.isNotEmpty() && sub.all { it.isDigit() }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(shape)
            .background(if (focused) c.primaryContainer else c.surfaceVariant)
            .border(if (focused) 2.dp else 1.dp, if (focused) c.primary else Color(0x14FFFFFF), shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Logo(logo, title)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty() && !isCount) Text(sub, style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant, maxLines = 1)
        }
        if (isCount) Text(sub, color = Color.White, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(c.primary).padding(horizontal = 10.dp, vertical = 4.dp))
        Text("›", color = c.primary, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 10.dp))
    }
}