package com.sakasense.kasaka.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sakasense.kasaka.LocalAppContainer
import com.sakasense.kasaka.data.PlotSummary
import com.sakasense.kasaka.data.SoilRepository
import com.sakasense.kasaka.data.local.DeviceStatus
import com.sakasense.kasaka.data.local.FarmEntity
import com.sakasense.kasaka.data.local.PlotStatus
import com.sakasense.kasaka.domain.CropProfiles
import com.sakasense.kasaka.ui.components.EmptyState
import com.sakasense.kasaka.ui.components.HealthRing
import com.sakasense.kasaka.ui.components.IconBadge
import com.sakasense.kasaka.ui.components.KCard
import com.sakasense.kasaka.ui.components.Pill
import com.sakasense.kasaka.ui.components.SectionHeader
import com.sakasense.kasaka.ui.components.StatusChip
import com.sakasense.kasaka.ui.components.statusColor
import com.sakasense.kasaka.ui.greeting
import com.sakasense.kasaka.ui.relativeTime
import com.sakasense.kasaka.ui.theme.KasakaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeState(
    val loaded: Boolean = false,
    val farms: List<FarmEntity> = emptyList(),
    val selectedFarmId: String? = null,
    val plots: List<PlotSummary> = emptyList(),
    val openAlerts: Int = 0,
    val devicesOnline: Int = 0,
    val devicesTotal: Int = 0,
) {
    val averageHealth: Int? = plots.mapNotNull { it.healthIndex }.takeIf { it.isNotEmpty() }?.average()?.toInt()
    val lastReadingAt: Long? = plots.mapNotNull { it.latest?.timestamp }.maxOrNull()
    fun count(status: PlotStatus) = plots.count { it.plot.status == status }
}

class HomeViewModel(repo: SoilRepository) : ViewModel() {
    private val selectedFarm = MutableStateFlow<String?>(null)

    val state = combine(repo.farms, repo.plotSummaries, repo.alerts, repo.devices, selectedFarm) { farms, plots, alerts, devices, sel ->
        HomeState(
            loaded = true,
            farms = farms,
            selectedFarmId = sel,
            plots = plots.filter { sel == null || it.plot.farmId == sel },
            openAlerts = alerts.count { it.resolvedAt == null },
            devicesOnline = devices.count { it.status == DeviceStatus.ONLINE },
            devicesTotal = devices.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun selectFarm(id: String?) { selectedFarm.value = id }
}

@Composable
fun HomeScreen(
    userName: String,
    onOpenPlot: (String) -> Unit,
    onScan: () -> Unit,
    onAlerts: () -> Unit,
    onPlots: () -> Unit,
) {
    val repo = LocalAppContainer.current.soil
    val vm: HomeViewModel = viewModel { HomeViewModel(repo) }
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting() + ",", style = MaterialTheme.typography.bodyMedium, color = KasakaTheme.colors.muted)
                    Text(
                        userName.substringBefore(" ").ifBlank { "Farmer" },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                IconButton(onClick = onAlerts) {
                    BadgedBox(badge = { if (state.openAlerts > 0) Badge { Text("${state.openAlerts}") } }) {
                        Icon(Icons.Rounded.Notifications, "Alerts")
                    }
                }
            }
        }

        if (state.farms.size > 1) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.selectedFarmId == null, onClick = { vm.selectFarm(null) }, label = { Text("All farms") })
                    state.farms.forEach { f ->
                        FilterChip(selected = state.selectedFarmId == f.id, onClick = { vm.selectFarm(f.id) }, label = { Text(f.name) })
                    }
                }
            }
        }

        if (state.loaded && state.plots.isEmpty()) {
            item {
                KCard(Modifier.fillMaxWidth()) {
                    EmptyState(
                        Icons.Rounded.Grass,
                        "Set up your first plot",
                        "Add a farm and a plot, then take a reading to get your soil health score and crop advice.",
                    ) { Button(onClick = onPlots) { Text("Add a plot") } }
                }
            }
            return@LazyColumn
        }

        item { HeroCard(state, onScan) }

        val needsAction = state.plots
            .filter { it.recommendation != null && it.plot.status != PlotStatus.HEALTHY }
            .sortedByDescending { it.plot.status.ordinal }
        val insights = needsAction.ifEmpty { state.plots.filter { it.recommendation != null }.take(2) }
        if (insights.isNotEmpty()) {
            item { SectionHeader("AI insights") }
            items(insights, key = { "insight-" + it.plot.id }) { InsightCard(it) { onOpenPlot(it.plot.id) } }
        }

        item { SectionHeader("Your plots", action = "See all", onAction = onPlots) }
        items(state.plots, key = { it.plot.id }) { PlotRow(it) { onOpenPlot(it.plot.id) } }

        item {
            KCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Sensors, MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Devices", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${state.devicesOnline} of ${state.devicesTotal} online",
                            style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(state: HomeState, onScan: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    KCard(
        Modifier.fillMaxWidth(),
        contentPadding = 0.dp,
    ) {
        Box(Modifier.background(Brush.linearGradient(listOf(primary.copy(alpha = 0.16f), primary.copy(alpha = 0.0f))))) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HealthRing(state.averageHealth, size = 124.dp, caption = "Avg. health")
                    Spacer(Modifier.width(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("${state.count(PlotStatus.HEALTHY)} healthy", statusColor(PlotStatus.HEALTHY))
                        Pill("${state.count(PlotStatus.NEEDS_ATTENTION)} need attention", statusColor(PlotStatus.NEEDS_ATTENTION))
                        Pill("${state.count(PlotStatus.ACTION_REQUIRED)} action required", statusColor(PlotStatus.ACTION_REQUIRED))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    state.lastReadingAt?.let { "Last reading ${relativeTime(it)}" } ?: "No readings yet",
                    style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Rounded.Sensors, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Take a soil reading")
                }
            }
        }
    }
}

@Composable
private fun InsightCard(s: PlotSummary, onClick: () -> Unit) {
    val rec = s.recommendation ?: return
    val color = statusColor(s.plot.status)
    KCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(Icons.Rounded.AutoAwesome, color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.plot.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("GB ${rec.guidebookCode}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(4.dp))
                Text(rec.insight, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${rec.fertilizerProduct} · ${rec.fertilizerTiming}",
                    style = MaterialTheme.typography.labelMedium, color = KasakaTheme.colors.muted,
                )
            }
        }
    }
}

@Composable
fun PlotRow(s: PlotSummary, onClick: () -> Unit) {
    KCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(statusColor(s.plot.status).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Text(CropProfiles.emoji(s.plot.cropCurrent), style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.plot.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(s.plot.cropCurrent, s.plot.areaLabel, s.latest?.let { relativeTime(it.timestamp) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted, maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                StatusChip(s.plot.status)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(s.healthIndex?.toString() ?: "—", style = com.sakasense.kasaka.ui.theme.ReadingStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize))
                Text("health", style = MaterialTheme.typography.labelSmall, color = KasakaTheme.colors.muted)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = KasakaTheme.colors.muted)
        }
    }
}
