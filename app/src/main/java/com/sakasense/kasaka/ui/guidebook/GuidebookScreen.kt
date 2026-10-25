package com.sakasense.kasaka.ui.guidebook

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakasense.kasaka.domain.CropProfiles
import com.sakasense.kasaka.domain.Guidebook
import com.sakasense.kasaka.domain.GuidebookEntry
import com.sakasense.kasaka.ui.components.EmptyState
import com.sakasense.kasaka.ui.components.IconBadge
import com.sakasense.kasaka.ui.components.KCard
import com.sakasense.kasaka.ui.theme.KasakaTheme
import com.sakasense.kasaka.ui.theme.ReadingStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidebookScreen(onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var crop by rememberSaveable { mutableStateOf<String?>(null) }
    val crops = remember { Guidebook.entries.mapNotNull { it.crop }.distinct().sorted() }
    val results = Guidebook.entries.filter { e ->
        (crop == null || e.crop == crop) &&
            (query.isBlank() || listOfNotNull(e.code, e.crop, e.title, e.soilCondition).any { it.contains(query.trim(), ignoreCase = true) })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SakaSense Guidebook") },
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
                Text(
                    "Enter the code shown on your device, or browse by crop.",
                    style = MaterialTheme.typography.bodyMedium, color = KasakaTheme.colors.muted,
                )
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Code (e.g., 4821) or crop") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = crop == null, onClick = { crop = null }, label = { Text("All") })
                    crops.forEach { c ->
                        FilterChip(selected = crop == c, onClick = { crop = if (crop == c) null else c }, label = { Text("${CropProfiles.emoji(c)} $c") })
                    }
                }
            }
            if (results.isEmpty()) {
                item { EmptyState(Icons.Rounded.Search, "No matching page", "Check the code on your device screen, or clear the search.") }
            }
            items(results, key = { it.code }) { e -> EntryRow(e) { onOpen(e.code) } }
        }
    }
}

@Composable
private fun EntryRow(e: GuidebookEntry, onClick: () -> Unit) {
    KCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 8.dp),
            ) { Text(e.code, style = ReadingStyle.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.titleSmall)
                Text(e.soilCondition, style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted)
            }
            Text(CropProfiles.emoji(e.crop), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideDetailScreen(code: String, onBack: () -> Unit) {
    val e = Guidebook.find(code)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guidebook $code") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (e == null) {
            EmptyState(Icons.Rounded.Search, "Page $code not found", "This code isn't in the Guidebook on this phone yet. Update the app for the latest pages.", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column {
                    Text(CropProfiles.emoji(e.crop), style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(e.title, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(e.soilCondition, style = MaterialTheme.typography.bodyLarge, color = KasakaTheme.colors.muted)
                }
            }
            item {
                KCard(Modifier.fillMaxWidth()) {
                    Text("What to do", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    e.cultivationSteps.forEachIndexed { i, step ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) { Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary) }
                            Spacer(Modifier.width(12.dp))
                            Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                KCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Science, KasakaTheme.colors.attention)
                        Spacer(Modifier.width(12.dp))
                        Text("Fertilizer", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(e.fertilizerNotes, style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Info, null, tint = KasakaTheme.colors.muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "General guidance. Confirm application rates with your Municipal Agriculture Office or a licensed agronomist.",
                        style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted,
                    )
                }
            }
        }
    }
}
