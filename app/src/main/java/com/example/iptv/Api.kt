package com.example.iptv

import org.json.JSONArray
import org.json.JSONObject
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
    c.connectTimeout = 10000; c.readTimeout = 60000
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

class Xtream(server: String, private val user: String, private val pass: String) {
    private val base = fixUrl(server).trimEnd('/')
    private val api = "$base/player_api.php?username=${enc(user)}&password=${enc(pass)}"
    private val auth = "${enc(user)}/${enc(pass)}"

    private fun load(catAction: String, listAction: String, build: (JSONObject, String) -> Channel): List<Channel> {
        val cats = HashMap<String, String>()
        val ca = JSONArray(httpGet("$api&action=$catAction"))
        for (i in 0 until ca.length()) ca.getJSONObject(i).let { cats[it.optString("category_id")] = it.optString("category_name") }
        val a = JSONArray(httpGet("$api&action=$listAction"))
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            build(o, cats[o.optString("category_id")] ?: "")
        }
    }

    fun live() = load("get_live_categories", "get_live_streams") { o, g ->
        Channel(o.optString("name"), g, "$base/live/$auth/${o.optString("stream_id")}.m3u8")
    }

    fun movies() = load("get_vod_categories", "get_vod_streams") { o, g ->
        val ext = o.optString("container_extension").ifEmpty { "mp4" }
        Channel(o.optString("name"), g, "$base/movie/$auth/${o.optString("stream_id")}.$ext")
    }

    fun series() = load("get_series_categories", "get_series") { o, g ->
        Channel(o.optString("name"), g, o.optString("series_id"))
    }

    fun episodes(seriesId: String): List<Channel> {
        val eps = JSONObject(httpGet("$api&action=get_series_info&series_id=$seriesId")).optJSONObject("episodes")
            ?: return emptyList()
        val out = ArrayList<Channel>()
        eps.keys().asSequence().sortedBy { it.toIntOrNull() ?: 0 }.forEach { s ->
            val arr = eps.optJSONArray(s) ?: return@forEach
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val ext = e.optString("container_extension").ifEmpty { "mp4" }
                val n = e.optString("episode_num").padStart(2, '0')
                out.add(Channel("E$n ${e.optString("title")}", "Saison $s", "$base/series/$auth/${e.optString("id")}.$ext"))
            }
        }
        return out
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