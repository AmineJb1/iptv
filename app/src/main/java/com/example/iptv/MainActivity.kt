package com.example.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Config {
    const val SERVER = "http://vpn.mediafrique.xyz"
    const val PORTAL = "http://vpn.mediafrique.xyz/c/"
}

data class Channel(val name: String, val group: String, val url: String)

fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
fun fixUrl(u: String): String = u.trim().let { if (it.startsWith("http")) it else "http://$it" }

fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 10000; c.readTimeout = 30000
    headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
    return c.inputStream.bufferedReader().readText()
}

fun parseM3u(text: String): List<Channel> {
    val out = ArrayList<Channel>()
    var name = ""; var group = ""
    for (raw in text.lineSequence()) {
        val l = raw.trim()
        if (l.startsWith("#EXTINF")) {
            name = l.substringAfterLast(',', "").trim()
            group = Regex("group-title=\"([^\"]*)\"").find(l)?.groupValues?.get(1) ?: ""
        } else if (l.isNotEmpty() && !l.startsWith("#")) {
            out.add(Channel(name.ifEmpty { l }, group, l)); name = ""; group = ""
        }
    }
    return out
}

fun loadXtream(server: String, user: String, pass: String): List<Channel> {
    val base = fixUrl(server).trimEnd('/')
    val api = "$base/player_api.php?username=${enc(user)}&password=${enc(pass)}"
    val cats = HashMap<String, String>()
    JSONArray(httpGet("$api&action=get_live_categories")).let { a ->
        for (i in 0 until a.length()) a.getJSONObject(i).let { cats[it.optString("category_id")] = it.optString("category_name") }
    }
    val a = JSONArray(httpGet("$api&action=get_live_streams"))
    return List(a.length()) { i ->
        val o = a.getJSONObject(i)
        Channel(o.optString("name"), cats[o.optString("category_id")] ?: "",
            "$base/live/${enc(user)}/${enc(pass)}/${o.optString("stream_id")}.m3u8")
    }
}

class Stalker(portal: String, val mac: String) {
    private val load: String
    private var token: String? = null
    init {
        val p = fixUrl(portal).trimEnd('/')
        load = if (p.endsWith("load.php")) p else p.removeSuffix("/c") + "/server/load.php"
    }
    private fun h() = mapOf(
        "Cookie" to "mac=$mac; stb_lang=en; timezone=Europe/Paris",
        "Authorization" to "Bearer ${token ?: ""}",
        "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
        "X-User-Agent" to "Model: MAG250; Link: WiFi")
    private fun js(q: String) = JSONObject(httpGet("$load?$q&JsHttpRequest=1-xml", h())).getJSONObject("js")

    fun connect() {
        token = js("type=stb&action=handshake&token=").getString("token")
        try { js("type=stb&action=get_profile&hd=1&stb_type=MAG250&client_type=STB&auth_second_step=1&not_valid_token=0") } catch (_: Exception) {}
    }
    fun channels(): List<Channel> {
        connect()
        val g = HashMap<String, String>()
        try {
            val a = JSONObject(httpGet("$load?type=itv&action=get_genres&JsHttpRequest=1-xml", h())).getJSONArray("js")
            for (i in 0 until a.length()) a.getJSONObject(i).let { g[it.optString("id")] = it.optString("title") }
        } catch (_: Exception) {}
        val d = js("type=itv&action=get_all_channels").getJSONArray("data")
        return List(d.length()) { i ->
            val o = d.getJSONObject(i)
            Channel(o.optString("name"), g[o.optString("tv_genre_id")] ?: "", o.optString("cmd"))
        }
    }
    fun resolve(cmd: String): String {
        if (token == null) connect()
        val r = js("type=itv&action=create_link&cmd=${enc(cmd)}&series=&forced_storage=undefined&disable_ad=0&download=0").getString("cmd")
        return r.trim().removePrefix("ffmpeg").trim()
    }
}
var stalker: Stalker? = null

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { App() } } }
    }
}

fun randomMac(): String = "00:1A:79:" + List(3) { "%02X".format((0..255).random()) }.joinToString(":")

@Composable
fun App() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("p", 0) }
    val cache = remember { File(ctx.cacheDir, "channels.tsv") }
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(prefs.getInt("mode", 1)) }
    var m3u by remember { mutableStateOf(prefs.getString("m3u", "") ?: "") }
    val server = Config.SERVER
    var user by remember { mutableStateOf(prefs.getString("user", "") ?: "") }
    var pass by remember { mutableStateOf(prefs.getString("pass", "") ?: "") }
    val portal = Config.PORTAL
    var mac by remember { mutableStateOf(prefs.getString("mac", null) ?: randomMac().also { prefs.edit().putString("mac", it).apply() }) }
    var channels by remember { mutableStateOf(emptyList<Channel>()) }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf<String?>(null) }
    var showLogin by remember { mutableStateOf(true) }

    fun connect() {
        prefs.edit().putInt("mode", mode).putString("m3u", m3u).putString("user", user)
            .putString("pass", pass).putString("mac", mac).apply()
        status = "Connexion…"
        scope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    val l = when (mode) {
                        0 -> parseM3u(httpGet(fixUrl(m3u)))
                        1 -> loadXtream(server, user, pass)
                        else -> Stalker(portal, mac.trim().uppercase()).also { stalker = it }.channels()
                    }
                    cache.writeText(l.joinToString("\n") { "${it.name}\t${it.group}\t${it.url}" })
                    l
                }
                channels = list; status = "${list.size} chaînes"; showLogin = false
            } catch (e: Exception) { status = "Erreur : ${e.message}" }
        }
    }

    LaunchedEffect(Unit) {
        if (cache.exists()) {
            channels = withContext(Dispatchers.IO) {
                cache.readLines().mapNotNull { l -> l.split("\t").takeIf { it.size == 3 }?.let { Channel(it[0], it[1], it[2]) } }
            }
            if (mode == 2 && stalker == null) stalker = Stalker(portal, mac.trim().uppercase())
            if (channels.isNotEmpty()) { showLogin = false; status = "${channels.size} chaînes (cache)" }
        }
    }

    playing?.let { BackHandler { playing = null }; Player(it); return }
    if (!showLogin) BackHandler { showLogin = true }

    val shown = remember(channels, query) {
        if (query.isBlank()) channels else channels.filter { it.name.contains(query, true) || it.group.contains(query, true) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (showLogin) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("M3U", "Identifiants", "Adresse MAC").forEachIndexed { i, t ->
                    if (mode == i) Button({ mode = i }) { Text(t) } else OutlinedButton({ mode = i }) { Text(t) }
                }
            }
            val f = Modifier.fillMaxWidth().padding(top = 8.dp)
            when (mode) {
                0 -> OutlinedTextField(m3u, { m3u = it }, f, singleLine = true, label = { Text("URL de la playlist M3U") })
                1 -> {
                    OutlinedTextField(user, { user = it }, f, singleLine = true, label = { Text("Nom d'utilisateur") })
                    OutlinedTextField(pass, { pass = it }, f, singleLine = true, label = { Text("Mot de passe") },
                        visualTransformation = PasswordVisualTransformation())
                }
                else -> {
                    OutlinedTextField(mac, { mac = it }, f, singleLine = true, label = { Text("Adresse MAC (à donner à votre fournisseur)") })
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button({ connect() }) { Text("Se connecter") }
                Spacer(Modifier.width(12.dp)); Text(status)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton({ showLogin = true }) { Text("Compte") }
                Spacer(Modifier.width(12.dp)); Text(status)
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp), singleLine = true,
                label = { Text("Rechercher") })
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(shown, key = { i, c -> "$i${c.url}" }) { _, c ->
                    var focused by remember { mutableStateOf(false) }
                    Column(
                        Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
                            .background(if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                            .clickable {
                                scope.launch {
                                    try {
                                        playing = if (mode == 2) withContext(Dispatchers.IO) { stalker!!.resolve(c.url) } else c.url
                                    } catch (e: Exception) { status = "Erreur : ${e.message}" }
                                }
                            }.padding(12.dp)
                    ) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium)
                        if (c.group.isNotEmpty()) Text(c.group, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun Player(url: String) {
    val ctx = LocalContext.current
    val player = remember(url) 
        ExoPlayer.Builder(ctx).build().apply { setMediaItem(MediaItem.fromUri(url)); prepare(); playWhenReady = true }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(factory = { PlayerView(it).apply { this.player = player; keepScreenOn = true } }, modifier = Modifier.fillMaxSize())
}
