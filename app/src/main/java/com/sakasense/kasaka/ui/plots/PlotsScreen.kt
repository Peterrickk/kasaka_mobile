package com.sakasense.kasaka.ui.plots

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Agriculture
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakasense.kasaka.LocalAppContainer
import com.sakasense.kasaka.data.PlotSummary
import com.sakasense.kasaka.domain.CropProfiles
import com.sakasense.kasaka.ui.components.EmptyState
import com.sakasense.kasaka.ui.components.KCard
import com.sakasense.kasaka.ui.components.SectionHeader
import com.sakasense.kasaka.ui.components.statusColor
import com.sakasense.kasaka.ui.home.PlotRow
import com.sakasense.kasaka.ui.theme.KasakaTheme
import com.sakasense.kasaka.ui.theme.ReadingStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlotsScreen(onOpenPlot: (String) -> Unit) {
    val repo = LocalAppContainer.current.soil
    val scope = rememberCoroutineScope()
    val farms by repo.farms.collectAsStateWithLifecycle(initialValue = null)
    val plots by repo.plotSummaries.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedFarm by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddFarm by remember { mutableStateOf(false) }
    var showAddPlot by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val farmList = farms.orEmpty()
    LaunchedEffect(farmList) {
        if (selectedFarm == null || farmList.none { it.id == selectedFarm }) selectedFarm = farmList.firstOrNull()?.id
    }
    val farm = farmList.firstOrNull { it.id == selectedFarm }
    val farmPlots = plots.filter { it.plot.farmId == selectedFarm }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plots") },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Add farm") }, onClick = { menu = false; showAddFarm = true })
                        if (farm != null) {
                            DropdownMenuItem(
                                text = { Text("Delete “${farm.name}”") },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (farm != null) {
                ExtendedFloatingActionButton(
                    onClick = { showAddPlot = true },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Add plot") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        if (farms != null && farmList.isEmpty()) {
            EmptyState(
                Icons.Rounded.Agriculture, "No farms yet",
                "Start by adding your farm or garden. You can add plots (zones, beds or pots) inside it.",
                Modifier.padding(padding),
            ) { Button(onClick = { showAddFarm = true }) { Text("Add farm") } }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (farmList.size > 1) {
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            farmList.forEach { f ->
                                FilterChip(selected = f.id == selectedFarm, onClick = { selectedFarm = f.id }, label = { Text(f.name) })
                            }
                        }
                    }
                }
                if (farm != null) {
                    item {
                        Text(farm.location, style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted)
                    }
                }
                if (farmPlots.isNotEmpty()) {
                    item { SectionHeader("Farm layout") }
                    item { FarmLayout(farmPlots, onOpenPlot) }
                    item { SectionHeader("All plots") }
                    items(farmPlots, key = { it.plot.id }) { PlotRow(it) { onOpenPlot(it.plot.id) } }
                } else if (farm != null) {
                    item {
                        KCard(Modifier.fillMaxWidth()) {
                            EmptyState(Icons.Rounded.Add, "No plots in ${farm.name}", "Add a zone, bed or container to start tracking it.")
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && farm != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${farm.name}?") },
            text = { Text("This removes the farm, its ${farmPlots.size} plot(s) and all their readings from this phone. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; scope.launch { repo.deleteFarm(farm.id) } }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (showAddFarm) {
        AddFarmSheet(onDismiss = { showAddFarm = false }) { name, location, type ->
            scope.launch { selectedFarm = repo.addFarm(name, location, type); showAddFarm = false }
        }
    }
    if (showAddPlot && farm != null) {
        AddPlotSheet(onDismiss = { showAddPlot = false }) { name, crop, area ->
            scope.launch { repo.addPlot(farm.id, name, crop, area); showAddPlot = false }
        }
    }
}

/**
 * Bird's-eye map of the farm: plots placed on a 3-column grid and coloured by status,
 * like the web dashboard's farm view.
 */
@Composable
private fun FarmLayout(plots: List<PlotSummary>, onOpen: (String) -> Unit) {
    val rows = plots.maxOf { it.plot.layoutRow + it.plot.rowSpan - 1 }
    val gap = 8.dp
    KCard(Modifier.fillMaxWidth(), contentPadding = 10.dp, container = KasakaTheme.colors.soil.copy(alpha = 0.10f)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(rows * 84.dp + (rows - 1) * gap)) {
            val cell = (maxWidth - gap * 2) / 3
            val rowH = 84.dp
            plots.forEach { s ->
                val p = s.plot
                val color = statusColor(p.status)
                Surface(
                    onClick = { onOpen(p.id) },
                    modifier = Modifier
                        .offset(x = (cell + gap) * (p.layoutCol - 1), y = (rowH + gap) * (p.layoutRow - 1))
                        .size(width = cell * p.colSpan + gap * (p.colSpan - 1), height = rowH * p.rowSpan + gap * (p.rowSpan - 1)),
                    shape = MaterialTheme.shapes.medium,
                    color = color.copy(alpha = 0.16f),
                    border = BorderStroke(1.5.dp, color.copy(alpha = 0.7f)),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(CropProfiles.emoji(p.cropCurrent))
                        Spacer(Modifier.weight(1f))
                        Text(p.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.healthIndex?.toString() ?: "—", style = ReadingStyle, color = color)
                        }
                    }
                }
            }
        }
    }
}
