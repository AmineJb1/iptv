package com.example.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AfricaTheme { App() } }
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
    var user by remember { mutableStateOf(prefs.getString("user", "") ?: "") }
    var pass by remember { mutableStateOf(prefs.getString("pass", "") ?: "") }
    var mac by remember { mutableStateOf(prefs.getString("mac", null) ?: randomMac().also { prefs.edit().putString("mac", it).apply() }) }
    var channels by remember { mutableStateOf(emptyList<Channel>()) }
    var movies by remember { mutableStateOf<List<Channel>?>(null) }
    var series by remember { mutableStateOf<List<Channel>?>(null) }
    var episodes by remember { mutableStateOf<List<Channel>?>(null) }
    var section by remember { mutableStateOf(0) }
    var group by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf<String?>(null) }
    var showLogin by remember { mutableStateOf(true) }

    fun xt() = Xtream(Config.SERVER, user, pass)

    fun fetch(block: suspend () -> Unit) {
        status = "Chargement…"
        scope.launch {
            try { block(); status = "" } catch (e: Exception) { status = "Erreur : ${e.message}" }
        }
    }

    fun connect() {
        prefs.edit().putInt("mode", mode).putString("m3u", m3u).putString("user", user)
            .putString("pass", pass).putString("mac", mac).apply()
        status = "Connexion…"
        scope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    val l = when (mode) {
                        0 -> parseM3u(httpGet(fixUrl(m3u)))
                        1 -> xt().live()
                        else -> Stalker(Config.PORTAL, mac.trim().uppercase()).also { stalker = it }.channels()
                    }
                    cache.writeText(l.joinToString("\n") { "${it.name}\t${it.group}\t${it.url}\t${it.logo}" })
                    l
                }
                channels = list; movies = null; series = null; episodes = null
                section = 0; group = null; query = ""
                status = "${list.size} chaînes"; showLogin = false
            } catch (e: Exception) { status = "Erreur : ${e.message}" }
        }
    }

    fun pickSection(s: Int) {
        section = s; group = null; query = ""; episodes = null; status = ""
        if (s == 0) return
        if (mode != 1) { status = "Films et séries : mode Identifiants uniquement"; return }
        if (s == 1 && movies == null) fetch { movies = withContext(Dispatchers.IO) { xt().movies() } }
        if (s == 2 && series == null) fetch { series = withContext(Dispatchers.IO) { xt().series() } }
    }

    fun pick(c: Channel) {
        if (section == 2 && episodes == null) {
            fetch { episodes = withContext(Dispatchers.IO) { xt().episodes(c.url) }; group = null; query = "" }
        } else {
            scope.launch {
                try {
                    playing = if (mode == 2 && section == 0) withContext(Dispatchers.IO) { stalker!!.resolve(c.url) } else c.url
                } catch (e: Exception) { status = "Erreur : ${e.message}" }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (cache.exists()) {
            channels = withContext(Dispatchers.IO) {
                cache.readLines().mapNotNull { l -> l.split("\t").takeIf { it.size >= 3 }?.let { Channel(it[0], it[1], it[2], it.getOrElse(3) { "" }) } }
            }
            if (mode == 2 && stalker == null) stalker = Stalker(Config.PORTAL, mac.trim().uppercase())
            if (channels.isNotEmpty()) { showLogin = false; status = "${channels.size} chaînes (cache)" }
        }
    }

    playing?.let { BackHandler { playing = null }; Player(it); return }

    BackHandler(enabled = !showLogin) {
        when {
            query.isNotEmpty() -> query = ""
            episodes != null -> { episodes = null; group = null }
            group != null -> group = null
            else -> showLogin = true
        }
    }

    val current = when (section) {
        0 -> channels
        1 -> movies ?: emptyList()
        else -> episodes ?: series ?: emptyList()
    }

    MainUi(
        showLogin, mode, { mode = it }, m3u, { m3u = it }, user, { user = it }, pass, { pass = it }, mac, { mac = it },
        status, { connect() }, { showLogin = true }, section, { pickSection(it) }, group, { group = it },
        query, { query = it }, current, { pick(it) }
    )
}