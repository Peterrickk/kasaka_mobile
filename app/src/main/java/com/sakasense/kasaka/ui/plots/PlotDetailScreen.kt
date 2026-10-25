package com.sakasense.kasaka.ui.plots

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sakasense.kasaka.LocalAppContainer
import com.sakasense.kasaka.data.PlotSummary
import com.sakasense.kasaka.data.SoilRepository
import com.sakasense.kasaka.data.local.ReadingEntity
import com.sakasense.kasaka.data.local.RecommendationEntity
import com.sakasense.kasaka.data.climate
import com.sakasense.kasaka.data.toSample
import com.sakasense.kasaka.domain.CropProfiles
import com.sakasense.kasaka.domain.Nutrient
import com.sakasense.kasaka.domain.SoilAnalyzer
import com.sakasense.kasaka.ui.components.ClimateRow
import com.sakasense.kasaka.ui.components.HealthRing
import com.sakasense.kasaka.ui.components.IconBadge
import com.sakasense.kasaka.ui.components.KCard
import com.sakasense.kasaka.ui.components.NutrientTile
import com.sakasense.kasaka.ui.components.Pill
import com.sakasense.kasaka.ui.components.ScoreBar
import com.sakasense.kasaka.ui.components.SectionHeader
import com.sakasense.kasaka.ui.components.StatusChip
import com.sakasense.kasaka.ui.components.TrendChart
import com.sakasense.kasaka.ui.components.nutrientColor
import com.sakasense.kasaka.ui.formatDateTime
import com.sakasense.kasaka.ui.formatPh
import com.sakasense.kasaka.ui.formatShortDate
import com.sakasense.kasaka.ui.relativeTime
import com.sakasense.kasaka.ui.theme.KasakaTheme
import com.sakasense.kasaka.ui.theme.ReadingStyle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class PlotDetailState(
    val loaded: Boolean = false,
    val summary: PlotSummary? = null,
    val days: Int = 14,
    val metric: Nutrient = Nutrient.N,
    val readings: List<ReadingEntity> = emptyList(),
    val history: List<RecommendationEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlotDetailViewModel(private val plotId: String, private val repo: SoilRepository) : ViewModel() {
    private val days = MutableStateFlow(14)
    private val metric = MutableStateFlow(Nutrient.N)

    val state = combine(
        repo.plot(plotId),
        days,
        metric,
        days.flatMapLatest { repo.readings(plotId, it) },
        repo.recommendations(plotId),
    ) { summary, d, m, readings, recs ->
        PlotDetailState(true, summary, d, m, readings, recs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlotDetailState())

    fun setDays(d: Int) { days.value = d }
    fun setMetric(m: Nutrient) { metric.value = m }
    fun setCrop(crop: String?) = viewModelScope.launch { repo.updatePlotCrop(plotId, crop) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { repo.deletePlot(plotId); onDone() }
    suspend fun csv() = repo.csvReport(plotId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlotDetailScreen(plotId: String, onBack: () -> Unit, onScan: (String) -> Unit, onOpenGuide: (String) -> Unit) {
    val repo = LocalAppContainer.current.soil
    val vm: PlotDetailViewModel = viewModel { PlotDetailViewModel(plotId, repo) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editCrop by remember { mutableStateOf(false) }
    val s = state.summary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s?.plot?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { scope.launch { shareReport(context, s?.plot?.name ?: "plot", vm.csv()) } }) {
                        Icon(Icons.Rounded.Share, "Share report")
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Change crop") }, onClick = { menu = false; editCrop = true })
                        DropdownMenuItem(text = { Text("Delete plot") }, onClick = { menu = false; confirmDelete = true })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (s != null) {
                Box(Modifier.background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(16.dp)) {
                    Button(onClick = { onScan(plotId) }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        Icon(Icons.Rounded.Sensors, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Take a new reading")
                    }
                }
            }
        },
    ) { padding ->
        if (s == null) {
            if (state.loaded) Text("This plot no longer exists.", Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        val crop = CropProfiles.find(s.plot.cropCurrent)
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Header(s) }

            val latest = s.latest
            if (latest == null) {
                item {
                    KCard(Modifier.fillMaxWidth()) {
                        Text("No readings yet", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Take a reading with your SakaSense device to see this plot's soil health and advice.",
                            style = MaterialTheme.typography.bodyMedium, color = KasakaTheme.colors.muted,
                        )
                    }
                }
                return@LazyColumn
            }

            item { SectionHeader("Latest reading") }
            item {
                val bands = SoilAnalyzer.bands(latest.toSample(), crop)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    bands.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { NutrientTile(it, Modifier.weight(1f)) }
                        }
                    }
                    ClimateRow(latest.climate())
                }
            }

            item { TrendsCard(state, crop, vm::setDays, vm::setMetric) }

            s.recommendation?.let { rec -> item { RecommendationCard(rec, onOpenGuide) } }

            s.device?.let { d ->
                item {
                    KCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.Sensors, KasakaTheme.colors.info)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.serialNumber, style = ReadingStyle.copy(fontSize = 14.sp))
                                Text("${d.model} · seen ${relativeTime(d.lastSeenAt)}", style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Pill(d.status.name.lowercase().replaceFirstChar { it.uppercase() }, if (d.status.name == "ONLINE") KasakaTheme.colors.healthy else KasakaTheme.colors.muted)
                                d.batteryPercent?.let {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                        Icon(Icons.Rounded.BatteryStd, null, Modifier.size(14.dp), tint = KasakaTheme.colors.muted)
                                        Text("$it%", style = MaterialTheme.typography.labelSmall, color = KasakaTheme.colors.muted)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val older = state.history.drop(1)
            if (older.isNotEmpty()) {
                item { SectionHeader("Recommendation history") }
                items(older, key = { it.id }) { rec ->
                    KCard(Modifier.fillMaxWidth(), onClick = { onOpenGuide(rec.guidebookCode) }, contentPadding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatShortDate(rec.timestamp), style = MaterialTheme.typography.labelMedium, color = KasakaTheme.colors.muted, modifier = Modifier.width(56.dp))
                            Text(rec.insight, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.width(8.dp))
                            Text("${rec.healthIndex}", style = ReadingStyle)
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this plot?") },
            text = { Text("All readings and recommendations for ${s?.plot?.name} will be removed from this phone. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (editCrop && s != null) {
        var pick by remember { mutableStateOf(s.plot.cropCurrent) }
        ModalBottomSheet(onDismissRequest = { editCrop = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CropPicker(pick) { pick = it }
                Text(
                    "Fertilizer advice is written for the current crop. Your next reading will use the new crop.",
                    style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted,
                )
                Button(onClick = { vm.setCrop(pick); editCrop = false }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save") }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun Header(s: PlotSummary) {
    KCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(CropProfiles.emoji(s.plot.cropCurrent), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(s.plot.cropCurrent ?: "Not planted", style = MaterialTheme.typography.titleLarge)
                Text(
                    listOfNotNull(s.plot.areaLabel.ifBlank { null }, s.farm?.name).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted,
                )
                Spacer(Modifier.height(10.dp))
                StatusChip(s.plot.status)
                s.latest?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${formatDateTime(it.timestamp)} · ${it.source.name.lowercase().replace('_', ' ')}",
                        style = MaterialTheme.typography.labelSmall, color = KasakaTheme.colors.muted,
                    )
                }
            }
            HealthRing(s.healthIndex, size = 120.dp)
        }
    }
}

@Composable
private fun TrendsCard(state: PlotDetailState, crop: com.sakasense.kasaka.domain.CropProfile?, onDays: (Int) -> Unit, onMetric: (Nutrient) -> Unit) {
    val m = state.metric
    val values = state.readings.map {
        when (m) {
            Nutrient.PH -> it.ph
            Nutrient.N -> it.n.toDouble()
            Nutrient.P -> it.p.toDouble()
            Nutrient.K -> it.k.toDouble()
        }
    }
    val target = when (m) {
        Nutrient.PH -> (crop?.phMin ?: 6.0)..(crop?.phMax ?: 7.0)
        Nutrient.N -> (crop?.nTarget ?: 80).let { it.toDouble()..it * 1.6 }
        Nutrient.P -> (crop?.pTarget ?: 50).let { it.toDouble()..it * 1.6 }
        Nutrient.K -> (crop?.kTarget ?: 80).let { it.toDouble()..it * 1.6 }
    }
    val fmt: (Double) -> String = if (m == Nutrient.PH) ::formatPh else { v -> v.toInt().toString() }

    KCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Trends", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            SingleChoiceSegmentedButtonRow {
                listOf(7, 14, 30).forEachIndexed { i, d ->
                    SegmentedButton(
                        selected = state.days == d, onClick = { onDays(d) },
                        shape = SegmentedButtonDefaults.itemShape(i, 3),
                        icon = {},
                    ) { Text("${d}d", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Nutrient.entries.forEach { n ->
                FilterChip(selected = n == m, onClick = { onMetric(n) }, label = { Text(n.short) })
            }
        }
        Spacer(Modifier.height(8.dp))
        TrendChart(
            values = values,
            color = nutrientColor(m),
            target = target,
            format = fmt,
            startLabel = state.readings.firstOrNull()?.let { formatShortDate(it.timestamp) } ?: "",
            endLabel = state.readings.lastOrNull()?.let { formatShortDate(it.timestamp) } ?: "",
            description = "${m.label} over the last ${state.days} days",
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(KasakaTheme.colors.healthy.copy(alpha = 0.25f)))
            Spacer(Modifier.width(6.dp))
            Text(
                "Target zone for ${crop?.name ?: "general crops"}" + if (values.isNotEmpty()) " · avg ${fmt(values.average())}" else "",
                style = MaterialTheme.typography.labelSmall, color = KasakaTheme.colors.muted,
            )
        }
    }
}

@Composable
fun RecommendationCard(rec: RecommendationEntity, onOpenGuide: (String) -> Unit) {
    KCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SakaSense AI", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Pill(if (rec.generatedOnDevice) "On device" else "In app", MaterialTheme.colorScheme.primary, dot = false)
        }
        Spacer(Modifier.height(8.dp))
        Text(rec.insight, style = MaterialTheme.typography.bodyLarge)

        Spacer(Modifier.height(16.dp))
        Text("Best crops for this soil", style = MaterialTheme.typography.labelLarge, color = KasakaTheme.colors.muted)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rec.rankedCrops.forEachIndexed { i, c ->
                ScoreBar(c.crop, c.score, leading = CropProfiles.emoji(c.crop), highlight = i == 0)
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = KasakaTheme.colors.border)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Science, KasakaTheme.colors.attention)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Fertilizer prescription", style = MaterialTheme.typography.labelMedium, color = KasakaTheme.colors.muted)
                Text(rec.fertilizerProduct, style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Fact("Amount", rec.fertilizerAmount, Modifier.weight(1f))
            Fact("When", rec.fertilizerTiming, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text(rec.fertilizerNotes, style = MaterialTheme.typography.bodyMedium, color = KasakaTheme.colors.muted)

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { onOpenGuide(rec.guidebookCode) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Rounded.MenuBook, null)
            Spacer(Modifier.width(8.dp))
            Text("Open Guidebook ${rec.guidebookCode}")
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(KasakaTheme.colors.surface2)
            .padding(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = KasakaTheme.colors.muted)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun shareReport(context: Context, plotName: String, csv: String) {
    val dir = File(context.cacheDir, "reports").apply { mkdirs() }
    val safe = plotName.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifBlank { "plot" }
    val file = File(dir, "sakasense-$safe.csv").apply { writeText(csv) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "SakaSense soil report — $plotName")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share soil report"))
}
