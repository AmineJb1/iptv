package com.example.iptv

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

val Light = lightColorScheme(
    primary = Color(0xFFD97706), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3B3), onPrimaryContainer = Color(0xFF3B2300),
    background = Color.White, surface = Color.White, onSurface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xEEFFFFFF), onSurfaceVariant = Color(0xFF5F6368)
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
    MaterialTheme(colorScheme = Light) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White, Color(0xFFFFF3DC))))) {
            if (bg != null) {
                Image(bg, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.5f)))
            }
            Surface(Modifier.fillMaxSize(), color = Color.Transparent) { content() }
        }
    }
}
