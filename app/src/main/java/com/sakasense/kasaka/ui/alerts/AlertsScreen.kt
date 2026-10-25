package com.sakasense.kasaka.ui.alerts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakasense.kasaka.LocalAppContainer
import com.sakasense.kasaka.data.local.AlertType
import com.sakasense.kasaka.ui.components.EmptyState
import com.sakasense.kasaka.ui.components.IconBadge
import com.sakasense.kasaka.ui.components.KCard
import com.sakasense.kasaka.ui.relativeTime
import com.sakasense.kasaka.ui.theme.KasakaTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(onBack: () -> Unit, onOpenPlot: (String) -> Unit) {
    val repo = LocalAppContainer.current.soil
    val scope = rememberCoroutineScope()
    val alerts by repo.alerts.collectAsStateWithLifecycle(initialValue = emptyList())
    val plots by repo.plotSummaries.collectAsStateWithLifecycle(initialValue = emptyList())
    var filter by rememberSaveable { mutableStateOf<AlertType?>(null) }
    var showResolved by rememberSaveable { mutableStateOf(false) }
    val names = plots.associate { it.plot.id to it.plot.name }
    val shown = alerts.filter { (filter == null || it.type == filter) && (showResolved || it.resolvedAt == null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alerts") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null to "All", AlertType.DEFICIENCY to "Deficiency", AlertType.DEVICE_OFFLINE to "Device offline", AlertType.CHECK_IN_DUE to "Check-in due")
                        .forEach { (t, label) -> FilterChip(selected = filter == t, onClick = { filter = t }, label = { Text(label) }) }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show resolved", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = showResolved, onCheckedChange = { showResolved = it })
                }
            }
            if (shown.isEmpty()) {
                item { EmptyState(Icons.Rounded.NotificationsNone, "All clear", "No alerts right now. We'll flag deficiencies, offline devices and overdue check-ins here.") }
            }
            items(shown, key = { it.id }) { a ->
                val (icon, color) = when (a.type) {
                    AlertType.DEFICIENCY -> Icons.Rounded.WarningAmber to KasakaTheme.colors.action
                    AlertType.DEVICE_OFFLINE -> Icons.Rounded.CloudOff to KasakaTheme.colors.muted
                    AlertType.CHECK_IN_DUE -> Icons.Rounded.Schedule to KasakaTheme.colors.attention
                }
                val resolved = a.resolvedAt != null
                KCard(Modifier.fillMaxWidth(), onClick = { onOpenPlot(a.plotId) }, contentPadding = 14.dp) {
                    Row(verticalAlignment = Alignment.Top) {
                        IconBadge(icon, if (resolved) KasakaTheme.colors.muted else color)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                a.message, style = MaterialTheme.typography.titleSmall,
                                color = if (resolved) KasakaTheme.colors.muted else MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                listOfNotNull(names[a.plotId], relativeTime(a.triggeredAt), if (resolved) "resolved" else null).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted,
                            )
                        }
                        if (!resolved) {
                            TextButton(onClick = { scope.launch { repo.resolveAlert(a.id) } }) { Text("Resolve") }
                        }
                    }
                }
            }
        }
    }
}
