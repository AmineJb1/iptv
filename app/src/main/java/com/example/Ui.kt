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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    Box(
        modifier.clip(shape).background(if (selected) c.primary else c.surfaceVariant)
            .border(if (focused) 2.dp else 0.dp, Color.White, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }.padding(vertical = 10.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.White, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun MainUi(
    showLogin: Boolean, mode: Int, onMode: (Int) -> Unit,
    m3u: String, onM3u: (String) -> Unit, user: String, onUser: (String) -> Unit,
    pass: String, onPass: (String) -> Unit, mac: String, onMac: (String) -> Unit,
    status: String, onConnect: () -> Unit, onAccount: () -> Unit,
    section: Int, onSection: (Int) -> Unit, group: String?, onGroup: (String?) -> Unit,
    query: String, onQuery: (String) -> Unit, current: List<Channel>, onPick: (Channel) -> Unit
) {
    val c = MaterialTheme.colorScheme
    val field = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text("MEDIA+", color = c.primary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(6.dp))
                Text("AFRIQUE", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Light)
            }
            if (!showLogin) Pill("Compte", false) { onAccount() }
        }
        Spacer(Modifier.height(14.dp))
        if (showLogin) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("M3U", "Identifiants", "MAC").forEachIndexed { i, t ->
                    Pill(t, mode == i, Modifier.weight(1f)) { onMode(i) }
                }
            }
            val f = Modifier.fillMaxWidth().padding(top = 10.dp)
            when (mode) {
                0 -> OutlinedTextField(m3u, onM3u, f, singleLine = true, shape = field, label = { Text("URL de la playlist M3U") })
                1 -> {
                    OutlinedTextField(user, onUser, f, singleLine = true, shape = field, label = { Text("Nom d'utilisateur") })
                    OutlinedTextField(pass, onPass, f, singleLine = true, shape = field, label = { Text("Mot de passe") },
                        visualTransformation = PasswordVisualTransformation())
                }
                else -> OutlinedTextField(mac, onMac, f, singleLine = true, shape = field,
                    label = { Text("Adresse MAC (à donner à votre fournisseur)") })
            }
            Button({ onConnect() }, Modifier.fillMaxWidth().padding(top = 14.dp), shape = field) { Text("Se connecter") }
            if (status.isNotEmpty()) Text(status, Modifier.padding(top = 10.dp), color = c.onSurfaceVariant)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Direct", "Films", "Séries").forEachIndexed { i, t ->
                    Pill(t, section == i, Modifier.weight(1f)) { onSection(i) }
                }
            }
            if (group != null) TextButton({ onGroup(null) }) { Text("‹ $group", color = c.primary) }
            else Text("BOUQUETS", Modifier.padding(top = 12.dp, bottom = 2.dp), color = c.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge)
            if (status.isNotEmpty()) Text(status, color = c.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(query, onQuery, Modifier.fillMaxWidth().padding(vertical = 8.dp), singleLine = true,
                shape = field, label = { Text("Rechercher") })
            ItemList(current, group, query, { onGroup(it) }, onPick, Modifier.weight(1f))
        }
    }
}