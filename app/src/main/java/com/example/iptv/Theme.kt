package com.example.iptv

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

val Cinema = darkColorScheme(
    primary = Color(0xFFE11D48), onPrimary = Color.White,
    primaryContainer = Color(0xFF3A0A14), onPrimaryContainer = Color.White,
    background = Color(0xFF0B0B0F), surface = Color(0xFF0B0B0F), onSurface = Color.White,
    surfaceVariant = Color(0xFF17171F), onSurfaceVariant = Color(0xFF9AA0AA)
)

fun loadBackground(ctx: Context): ImageBitmap? = try {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.assets.open("bg.jpg").use { BitmapFactory.decodeStream(it, null, o) }
    var s = 1
    while (o.outWidth / s > 1600) s *= 2
    val o2 = BitmapFactory.Options().apply { inSampleSize = s }
    ctx.assets.open("bg.jpg").use { BitmapFactory.decodeStream(it, null, o2) }?.asImageBitmap()
} catch (e: Exception) { null }

@Composable
fun AfricaTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val bg = remember { loadBackground(ctx) }
    MaterialTheme(colorScheme = Cinema) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF16161F), Color(0xFF050507))))) {
            if (bg != null) {
                Image(bg, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)))
            }
            Surface(Modifier.fillMaxSize(), color = Color.Transparent) { content() }
        }
    }
}