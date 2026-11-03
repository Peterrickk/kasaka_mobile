package com.sakasense.kasaka.ui.scan

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sakasense.kasaka.LocalAppContainer
import com.sakasense.kasaka.data.PlotSummary
import com.sakasense.kasaka.device.DevicePayload
import com.sakasense.kasaka.device.FoundDevice
import com.sakasense.kasaka.domain.Climate
import com.sakasense.kasaka.domain.CropProfiles
import com.sakasense.kasaka.ui.components.ClimateRow
import com.sakasense.kasaka.ui.components.HealthRing
import com.sakasense.kasaka.ui.components.IconBadge
import com.sakasense.kasaka.ui.components.KCard
import com.sakasense.kasaka.ui.components.NutrientTile
import com.sakasense.kasaka.ui.components.SectionHeader
import com.sakasense.kasaka.ui.components.StatusChip
import com.sakasense.kasaka.ui.plots.AddPlotSheet
import com.sakasense.kasaka.ui.formatPh
import com.sakasense.kasaka.ui.guidebook.OpenGuide
import com.sakasense.kasaka.ui.plots.RecommendationCard
import com.sakasense.kasaka.ui.theme.KasakaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    presetPlotId: String?,
    onBack: (() -> Unit)?,
    onOpenPlot: (String) -> Unit,
    onOpenGuide: OpenGuide,
) {
    val container = LocalAppContainer.current
    val vm: ScanViewModel = viewModel { ScanViewModel(container, presetPlotId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val ui = state.ui
    var bluetoothHint by remember { mutableStateOf<String?>(null) }

    val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (vm.devices.bluetoothEnabled) vm.startScan()
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) {
            if (vm.devices.bluetoothEnabled) vm.startScan() else enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            bluetoothHint = "Bluetooth permission was denied. You can still use the demo device or enter values manually."
        }
    }
    val scan: () -> Unit = {
        bluetoothHint = null
        when {
            !vm.devices.hasBluetooth -> bluetoothHint = "This phone has no Bluetooth LE. Use manual entry instead."
            !vm.devices.hasPermissions() -> permissions.launch(vm.devices.requiredPermissions)
            !vm.devices.bluetoothEnabled -> enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            else -> vm.startScan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (ui.step) {
                            ScanStep.CONNECT -> "Take a reading"
                            ScanStep.READY -> if (ui.manual) "Enter reading" else "Spot ${ui.spots.size + 1} of $SPOTS_PER_BED"
                            ScanStep.MEASURING -> "Measuring…"
                            ScanStep.RESULT -> "Result"
                        }
                    )
                },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                actions = {
                    if (ui.connected != null && ui.step != ScanStep.MEASURING) {
                        TextButton(onClick = vm::disconnect) {
                            Icon(Icons.Rounded.LinkOff, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Disconnect")
                        }
                    } else if (ui.manual && ui.step == ScanStep.READY) {
                        TextButton(onClick = vm::disconnect) { Text("Use device") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = ui.step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.padding(padding),
            label = "scanStep",
        ) { step ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                (ui.error ?: bluetoothHint)?.let { ErrorBanner(it) { vm.dismissError(); bluetoothHint = null } }
                when (step) {
                    ScanStep.CONNECT -> ConnectStep(state, scan, vm::connect, vm::useManualEntry)
                    ScanStep.READY -> ReadyStep(state, vm)
                    ScanStep.MEASURING -> MeasuringStep(ui.progress, ui.spots.size + 1)
                    ScanStep.RESULT -> ResultStep(state, onOpenPlot, onOpenGuide, vm::measureAgain)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ConnectStep(state: ScanState, onScan: () -> Unit, onConnect: (FoundDevice) -> Unit, onManual: () -> Unit) {
    KCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pulse(active = state.scanning)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Connect your SakaSense device", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Turn the device on and keep it within a few metres of your phone.",
                    style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = onScan, enabled = !state.scanning, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Icon(Icons.AutoMirrored.Rounded.BluetoothSearching, null)
            Spacer(Modifier.width(8.dp))
            Text(if (state.scanning) "Searching…" else "Search for devices")
        }
    }

    SectionHeader("Devices")
    state.devices.forEach { d ->
        val connecting = state.ui.connectingTo == d.address
        KCard(Modifier.fillMaxWidth(), onClick = { onConnect(d) }, contentPadding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(if (d.simulated) Icons.Rounded.Science else Icons.Rounded.Bluetooth, if (d.simulated) KasakaTheme.colors.attention else KasakaTheme.colors.info)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (d.simulated) "Simulated readings — try the app without hardware" else "Signal ${d.rssi ?: "?"} dBm · ${d.address}",
                        style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted,
                    )
                }
                if (connecting) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("Connect", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    KCard(Modifier.fillMaxWidth(), onClick = onManual, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Keyboard, KasakaTheme.colors.muted)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Enter values manually", style = MaterialTheme.typography.titleSmall)
                Text("Type the values shown on the device screen", style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted)
            }
        }
    }
}

@Composable
private fun ReadyStep(state: ScanState, vm: ScanViewModel) {
    val ui = state.ui
    var addPlot by remember { mutableStateOf(false) }

    ui.connected?.let { d ->
        KCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.CheckCircle, KasakaTheme.colors.healthy)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Connected to ${d.device.name}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(d.serial, d.firmware?.let { "fw $it" }, d.battery?.let { "$it% battery" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted,
                    )
                }
            }
        }
    }

    SectionHeader("Which plot are you measuring?")
    PlotPicker(state.plots, ui.selectedPlotId, vm::selectPlot) { addPlot = true }

    if (ui.manual) {
        ManualForm(enabled = ui.selectedPlotId != null, onSubmit = vm::submitManual)
    } else {
        KCard(Modifier.fillMaxWidth()) {
            Text("Take 5 readings in this bed", style = MaterialTheme.typography.titleSmall)
            Text(
                "One spot can mislead you. Kasaka averages the five spots for you (Guidebook page 4).",
                style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted,
            )
            Spacer(Modifier.height(8.dp))
            listOf(
                "Pick 5 spots in a zig-zag (W shape) across the bed, away from the edges, compost piles and fresh fertilizer.",
                "Clear away grass, mulch and stones. The soil should be moist, not dry and not flooded.",
                "Push the probe 10–15 cm deep with no air gaps, and keep it still until the numbers stop changing (up to 45 seconds).",
            ).forEachIndexed { i, t ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(22.dp))
                    Text(t, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (ui.spots.isNotEmpty()) SpotsCard(ui.spots)
        Button(onClick = vm::measure, enabled = ui.selectedPlotId != null, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Measure spot ${ui.spots.size + 1} of $SPOTS_PER_BED")
        }
        if (ui.spots.isNotEmpty()) {
            OutlinedButton(onClick = vm::finishSpots, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Use the average of ${ui.spots.size} ${if (ui.spots.size == 1) "spot" else "spots"}")
            }
        }
    }

    if (addPlot) {
        AddPlotSheet(onDismiss = { addPlot = false }) { name, crop, area ->
            vm.quickAddPlot(name, crop, area)
            addPlot = false
        }
    }
}

@Composable
private fun SpotsCard(spots: List<DevicePayload>) {
    KCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Text("Measured so far", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        spots.forEachIndexed { i, s ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text("Spot ${i + 1}", style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted, modifier = Modifier.width(64.dp))
                Text("pH ${formatPh(s.ph)} · N ${s.n} · P ${s.p} · K ${s.k}", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (spots.size > 1) {
            val avg = average(spots)
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = KasakaTheme.colors.border)
            Row {
                Text("Average", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
                Text("pH ${formatPh(avg.ph)} · N ${avg.n} · P ${avg.p} · K ${avg.k}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PlotPicker(plots: List<PlotSummary>, selected: String?, onSelect: (String) -> Unit, onAdd: () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        plots.forEach { p ->
            FilterChip(
                selected = p.plot.id == selected,
                onClick = { onSelect(p.plot.id) },
                label = { Text("${CropProfiles.emoji(p.plot.cropCurrent)} ${p.plot.name}") },
            )
        }
        FilterChip(selected = false, onClick = onAdd, label = { Text("+ New plot") })
    }
}

@Composable
private fun ManualForm(enabled: Boolean, onSubmit: (Double, Int, Int, Int, Climate) -> Unit) {
    var ph by rememberSaveable { mutableStateOf("") }
    var n by rememberSaveable { mutableStateOf("") }
    var p by rememberSaveable { mutableStateOf("") }
    var k by rememberSaveable { mutableStateOf("") }
    var temp by rememberSaveable { mutableStateOf("") }
    var hum by rememberSaveable { mutableStateOf("") }
    val phV = ph.replace(',', '.').toDoubleOrNull()
    val nV = n.toIntOrNull(); val pV = p.toIntOrNull(); val kV = k.toIntOrNull()
    val phOk = phV != null && phV in 3.0..10.0
    // Temperature and humidity are optional; when typed they must be plausible.
    val tempV = temp.replace(',', '.').toDoubleOrNull()
    val humV = hum.replace(',', '.').toDoubleOrNull()
    val tempOk = temp.isEmpty() || (tempV != null && tempV in -10.0..70.0)
    val humOk = hum.isEmpty() || (humV != null && humV in 0.0..100.0)
    val valid = phOk && tempOk && humOk && nV != null && pV != null && kV != null && listOf(nV, pV, kV).all { it in 0..2000 }

    KCard(Modifier.fillMaxWidth()) {
        Text("Values from the device screen", style = MaterialTheme.typography.titleSmall)
        Text(
            "Take 5 readings in a zig-zag across the bed, add each number up and divide by 5, then type the averages.",
            style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(ph, { ph = it }, "pH", "3–10", Modifier.weight(1f), decimal = true, isError = ph.isNotEmpty() && !phOk)
            NumberField(n, { n = it }, "N", "mg/kg", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(p, { p = it }, "P", "mg/kg", Modifier.weight(1f))
            NumberField(k, { k = it }, "K", "mg/kg", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(temp, { temp = it }, "Temperature", "°C · optional", Modifier.weight(1f), decimal = true, isError = !tempOk)
            NumberField(hum, { hum = it }, "Humidity", "% · optional", Modifier.weight(1f), decimal = true, isError = !humOk)
        }
    }
    Button(
        onClick = { onSubmit(phV!!, nV!!, pV!!, kV!!, Climate(tempV, humV)) },
        enabled = enabled && valid,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { Text("Analyze soil") }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, hint: String, modifier: Modifier, decimal: Boolean = false, isError: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.length <= 6) onChange(v.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }) },
        label = { Text(label) },
        supportingText = { Text(hint) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun MeasuringStep(progress: Float, spot: Int) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Pulse(active = true, size = 200.dp, dot = false)
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(28.dp))
        Text("Reading spot $spot of $SPOTS_PER_BED", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Keep the probe in the soil and the device still.",
            style = MaterialTheme.typography.bodyMedium, color = KasakaTheme.colors.muted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
    }
}

@Composable
private fun ResultStep(state: ScanState, onOpenPlot: (String) -> Unit, onOpenGuide: OpenGuide, onAgain: () -> Unit) {
    val a = state.ui.analysis ?: return
    val plotName = state.plots.firstOrNull { it.plot.id == state.ui.savedPlotId }?.plot?.name
    KCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HealthRing(a.healthIndex, size = 120.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                StatusChip(a.status)
                Spacer(Modifier.height(8.dp))
                Text("Saved to ${plotName ?: "plot"}", style = MaterialTheme.typography.titleSmall)
                Text("Analysed for ${a.targetCrop}", style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        a.bands.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { pair.forEach { NutrientTile(it, Modifier.weight(1f)) } }
        }
        ClimateRow(state.ui.climate)
    }
    state.ui.recommendation?.let { RecommendationCard(it, onOpenGuide) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onAgain, modifier = Modifier.weight(1f).height(52.dp)) { Text("Measure again") }
        Button(onClick = { state.ui.savedPlotId?.let(onOpenPlot) }, modifier = Modifier.weight(1f).height(52.dp)) { Text("View plot") }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    KCard(
        Modifier.fillMaxWidth(),
        container = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
        border = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
        contentPadding = 12.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(10.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

/** Concentric "signal" rings — the device-searching / measuring animation. */
@Composable
private fun Pulse(active: Boolean, size: androidx.compose.ui.unit.Dp = 56.dp, dot: Boolean = true) {
    val color = MaterialTheme.colorScheme.primary
    val t = rememberInfiniteTransition(label = "pulse")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Restart), label = "phase")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val max = this.size.minDimension / 2
            if (active) {
                repeat(3) { i ->
                    val f = (phase + i / 3f) % 1f
                    drawCircle(color.copy(alpha = (1 - f) * 0.5f), radius = max * (0.35f + 0.65f * f), style = Stroke(2.dp.toPx()))
                }
            }
            drawCircle(color.copy(alpha = 0.18f), radius = max * 0.35f)
        }
        if (dot) Box(Modifier.size(size * 0.22f).clip(CircleShape).background(color))
    }
}
