package com.sakasense.kasaka.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakasense.kasaka.BuildConfig
import com.sakasense.kasaka.LocalAppContainer
import com.sakasense.kasaka.data.Session
import com.sakasense.kasaka.data.ThemeMode
import com.sakasense.kasaka.ui.components.IconBadge
import com.sakasense.kasaka.ui.components.KCard
import com.sakasense.kasaka.ui.components.LogoBadge
import com.sakasense.kasaka.ui.components.Pill
import com.sakasense.kasaka.ui.relativeTime
import com.sakasense.kasaka.ui.theme.KasakaTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(session: Session) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val devices by c.soil.devices.collectAsStateWithLifecycle(initialValue = emptyList())
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val user = session.user
    val open: (String) -> Unit = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                KCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                user?.name?.split(" ")?.mapNotNull { it.firstOrNull()?.uppercase() }?.take(2)?.joinToString("") ?: "?",
                                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(user?.name.orEmpty(), style = MaterialTheme.typography.titleLarge)
                            Text(user?.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted)
                            if (!user?.contact.isNullOrBlank()) {
                                Text(user.contact, style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(user?.tier ?: "Free", MaterialTheme.colorScheme.primary)
                        if (user?.isDemo == true) Pill("Demo account", KasakaTheme.colors.attention)
                    }
                }
            }

            item {
                KCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.DarkMode, KasakaTheme.colors.info)
                        Spacer(Modifier.width(12.dp))
                        Text("Appearance", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = session.theme == m,
                                onClick = { scope.launch { c.session.setTheme(m) } },
                                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                            ) { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        }
                    }
                    Text(
                        "Light mode is easier to read in direct sunlight.",
                        style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item {
                KCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Sensors, MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("My devices", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("${devices.size}", style = MaterialTheme.typography.labelLarge, color = KasakaTheme.colors.muted)
                    }
                    if (devices.isEmpty()) {
                        Text(
                            "Devices appear here after your first Bluetooth reading.",
                            style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    devices.forEach { d ->
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(d.serialNumber, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "seen ${relativeTime(d.lastSeenAt)}" + (d.batteryPercent?.let { " · $it% battery" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted,
                                )
                            }
                            Pill(
                                d.status.name.lowercase().replaceFirstChar { it.uppercase() },
                                if (d.status.name == "ONLINE") KasakaTheme.colors.healthy else KasakaTheme.colors.muted,
                            )
                        }
                    }
                }
            }

            item {
                KCard(Modifier.fillMaxWidth(), contentPadding = 4.dp) {
                    LinkRow(Icons.Rounded.WorkspacePremium, "Shop SakaSense devices") { open("https://www.sakasense.com/products") }
                    Divider()
                    LinkRow(Icons.Rounded.Language, "Web dashboard") { open("https://www.sakasense.com/app") }
                    Divider()
                    LinkRow(Icons.Rounded.Phone, "Contact support · 0993 017 7122") { open("tel:09930177122") }
                    Divider()
                    LinkRow(Icons.Rounded.Shield, "Privacy policy") { open("https://www.sakasense.com/privacy") }
                    if (user?.isDemo == true) {
                        Divider()
                        LinkRow(Icons.Rounded.Refresh, "Reset demo data") { confirmReset = true }
                    }
                    Divider()
                    LinkRow(Icons.AutoMirrored.Rounded.Logout, "Sign out", tint = MaterialTheme.colorScheme.error) { confirmSignOut = true }
                }
            }

            item {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    LogoBadge(40.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("Kasaka ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = KasakaTheme.colors.muted)
                    Text("© 2026 SakaSense Venture Solutions · Tacloban City", style = MaterialTheme.typography.labelSmall, color = KasakaTheme.colors.muted)
                }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = {
                Text(
                    if (user?.isDemo == true) "The demo data will be cleared from this phone."
                    else "Your farms and readings stay on this phone and will be here when you sign back in."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    scope.launch {
                        if (user?.isDemo == true) c.soil.clearAll()
                        c.session.signOut()
                    }
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset demo data?") },
            text = { Text("Restores the sample farm and removes any readings you added.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; scope.launch { c.soil.seedDemo() } }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = KasakaTheme.colors.border)

@Composable
private fun LinkRow(icon: ImageVector, label: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (tint == MaterialTheme.colorScheme.onSurface) KasakaTheme.colors.muted else tint)
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = tint, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = KasakaTheme.colors.muted)
        }
    }
}
