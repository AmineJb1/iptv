package com.example.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
                    cache.writeText(l.joinToString("\n") { "${it.name}\t${it.group}\t${it.url}" })
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
                cache.readLines().mapNotNull { l -> l.split("\t").takeIf { it.size == 3 }?.let { Channel(it[0], it[1], it[2]) } }
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
                else -> OutlinedTextField(mac, { mac = it }, f, singleLine = true, label = { Text("Adresse MAC (à donner à votre fournisseur)") })
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
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Direct", "Films", "Séries").forEachIndexed { i, t ->
                    if (section == i) Button({ pickSection(i) }) { Text(t) } else OutlinedButton({ pickSection(i) }) { Text(t) }
                }
            }
            Text(group ?: "Bouquets", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp), singleLine = true,
                label = { Text("Rechercher") })
            ItemList(current, group, query, { group = it }, { pick(it) }, Modifier.weight(1f))
        }
    }
}