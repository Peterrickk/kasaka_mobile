package com.sakasense.kasaka.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakasense.kasaka.AppContainer
import com.sakasense.kasaka.data.DeviceVerdict
import com.sakasense.kasaka.data.PlotSummary
import com.sakasense.kasaka.data.local.FarmType
import com.sakasense.kasaka.data.local.ReadingSource
import com.sakasense.kasaka.data.local.RecommendationEntity
import com.sakasense.kasaka.data.toRecommendation
import com.sakasense.kasaka.device.ConnectedDevice
import com.sakasense.kasaka.device.FoundDevice
import com.sakasense.kasaka.domain.Climate
import com.sakasense.kasaka.domain.SoilAnalysis
import com.sakasense.kasaka.domain.SoilSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScanStep { CONNECT, READY, MEASURING, RESULT }

data class ScanUi(
    val step: ScanStep = ScanStep.CONNECT,
    val connectingTo: String? = null,
    val connected: ConnectedDevice? = null,
    val manual: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
    val selectedPlotId: String? = null,
    val analysis: SoilAnalysis? = null,
    val climate: Climate? = null,
    val recommendation: RecommendationEntity? = null,
    val savedPlotId: String? = null,
)

data class ScanState(
    val ui: ScanUi,
    val devices: List<FoundDevice>,
    val scanning: Boolean,
    val plots: List<PlotSummary>,
)

class ScanViewModel(private val c: AppContainer, presetPlotId: String?) : ViewModel() {
    private val ui = MutableStateFlow(ScanUi(selectedPlotId = presetPlotId))

    val state: StateFlow<ScanState> = combine(ui, c.devices.found, c.devices.scanning, c.soil.plotSummaries) { u, d, s, p ->
        ScanState(u, d, s, p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanState(ui.value, emptyList(), false, emptyList()))

    init {
        // Pre-select the only plot, so the common one-plot case needs no extra tap.
        viewModelScope.launch {
            val plots = c.soil.plotSummaries.first()
            ui.update { if (it.selectedPlotId == null && plots.size == 1) it.copy(selectedPlotId = plots.single().plot.id) else it }
        }
    }

    val devices get() = c.devices

    fun startScan() = c.devices.startScan(viewModelScope)

    fun selectPlot(id: String) = ui.update { it.copy(selectedPlotId = id, error = null) }

    fun connect(device: FoundDevice) {
        if (ui.value.connectingTo != null) return
        ui.update { it.copy(connectingTo = device.address, error = null) }
        viewModelScope.launch {
            runCatching { c.devices.connect(device) }
                .onSuccess { d -> ui.update { it.copy(step = ScanStep.READY, connected = d, connectingTo = null, manual = false) } }
                .onFailure { e -> ui.update { it.copy(connectingTo = null, error = e.message ?: "Could not connect") } }
        }
    }

    fun useManualEntry() = ui.update { it.copy(step = ScanStep.READY, manual = true, connected = null, error = null) }

    fun measure() {
        val plotId = ui.value.selectedPlotId ?: return ui.update { it.copy(error = "Choose which plot you are measuring first.") }
        ui.update { it.copy(step = ScanStep.MEASURING, progress = 0f, error = null) }
        viewModelScope.launch {
            runCatching {
                val payload = c.devices.measure(viewModelScope) { p -> ui.update { it.copy(progress = p) } }
                val serial = payload.sn ?: ui.value.connected?.serial
                val source = if (ui.value.connected?.device?.simulated == true) ReadingSource.DEMO else ReadingSource.BLUETOOTH
                save(
                    plotId, SoilSample(payload.ph, payload.n, payload.p, payload.k), Climate(payload.temp, payload.hum),
                    source, serial, payload.bat, DeviceVerdict(payload.crops, payload.gb),
                )
            }.onFailure { e ->
                ui.update { it.copy(step = ScanStep.READY, error = e.message ?: "Measurement failed") }
            }
        }
    }

    fun submitManual(ph: Double, n: Int, p: Int, k: Int, climate: Climate) {
        val plotId = ui.value.selectedPlotId ?: return ui.update { it.copy(error = "Choose which plot you are measuring first.") }
        viewModelScope.launch {
            runCatching { save(plotId, SoilSample(ph, n, p, k), climate, ReadingSource.MANUAL, null, null, null) }
                .onFailure { e -> ui.update { it.copy(error = e.message) } }
        }
    }

    private suspend fun save(plotId: String, sample: SoilSample, climate: Climate, source: ReadingSource, serial: String?, battery: Int?, verdict: DeviceVerdict?) {
        val analysis = c.soil.saveReading(plotId, sample, source, serial, battery, verdict, climate)
        val rec = analysis.toRecommendation(plotId, null, System.currentTimeMillis(), verdict)
        ui.update { it.copy(step = ScanStep.RESULT, analysis = analysis, climate = climate, recommendation = rec, savedPlotId = plotId, progress = 1f) }
    }

    fun measureAgain() = ui.update { it.copy(step = ScanStep.READY, analysis = null, climate = null, recommendation = null, error = null) }

    fun disconnect() {
        c.devices.disconnect()
        ui.update { ScanUi(selectedPlotId = it.selectedPlotId) }
    }

    fun dismissError() = ui.update { it.copy(error = null) }

    /** Creates a plot (and a farm, if there is none) right from the scan flow. */
    fun quickAddPlot(name: String, crop: String?, area: String) = viewModelScope.launch {
        val farmId = c.soil.farms.first().firstOrNull()?.id ?: c.soil.addFarm("My Farm", "", FarmType.RURAL_FIELD)
        val id = c.soil.addPlot(farmId, name, crop, area)
        selectPlot(id)
    }

    override fun onCleared() {
        c.devices.stopScan()
        c.devices.disconnect()
    }
}
