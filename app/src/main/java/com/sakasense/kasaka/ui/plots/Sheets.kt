package com.sakasense.kasaka.ui.plots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sakasense.kasaka.data.local.FarmType
import com.sakasense.kasaka.domain.CropProfiles
import com.sakasense.kasaka.ui.theme.KasakaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFarmSheet(onDismiss: () -> Unit, onSave: (String, String, FarmType) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(FarmType.RURAL_FIELD) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SheetBody("Add farm") {
            OutlinedTextField(name, { name = it }, label = { Text("Farm name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(location, { location = it }, label = { Text("Location (e.g., Baybay, Leyte)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Type", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(FarmType.RURAL_FIELD to "Field", FarmType.URBAN_CONTAINER to "Containers").forEachIndexed { i, (t, label) ->
                    SegmentedButton(selected = type == t, onClick = { type = t }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                }
            }
            Text(
                "Container farms get fertilizer doses per pot or litre instead of per hectare.",
                style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted,
            )
            Button(onClick = { onSave(name, location, type) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Save farm")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddPlotSheet(onDismiss: () -> Unit, onSave: (String, String?, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var area by rememberSaveable { mutableStateOf("") }
    var crop by rememberSaveable { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SheetBody("Add plot") {
            OutlinedTextField(name, { name = it }, label = { Text("Plot name (e.g., Zone D)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(area, { area = it }, label = { Text("Size (e.g., 0.25 ha or 20 L pot)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            CropPicker(crop) { crop = it }
            Button(onClick = { onSave(name, crop, area) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Save plot")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CropPicker(selected: String?, onSelect: (String?) -> Unit) {
    Text("Current crop", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Not planted yet") })
        CropProfiles.all.forEach { c ->
            FilterChip(selected = selected == c.name, onClick = { onSelect(c.name) }, label = { Text("${c.emoji} ${c.name}") })
        }
    }
}

@Composable
private fun SheetBody(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        content()
        Spacer(Modifier.height(12.dp))
    }
}
