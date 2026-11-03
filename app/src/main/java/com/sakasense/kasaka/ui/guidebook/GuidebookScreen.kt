package com.sakasense.kasaka.ui.guidebook

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakasense.kasaka.domain.CropGuide
import com.sakasense.kasaka.domain.CropProfile
import com.sakasense.kasaka.domain.CropProfiles
import com.sakasense.kasaka.domain.Fix
import com.sakasense.kasaka.domain.GuideBlock
import com.sakasense.kasaka.domain.GuideTopic
import com.sakasense.kasaka.domain.Guidebook
import com.sakasense.kasaka.domain.Level
import com.sakasense.kasaka.domain.Nutrient
import com.sakasense.kasaka.domain.SoilCondition
import com.sakasense.kasaka.ui.components.EmptyState
import com.sakasense.kasaka.ui.components.KCard
import com.sakasense.kasaka.ui.components.SectionHeader
import com.sakasense.kasaka.ui.components.levelColor
import com.sakasense.kasaka.ui.plots.cropImageRes
import com.sakasense.kasaka.ui.theme.KasakaTheme
import com.sakasense.kasaka.ui.theme.ReadingStyle
import java.util.Locale

/** Opens the Guidebook page for a recommendation: its crop's Fix-it code, or the Code finder. */
typealias OpenGuide = (crop: String?, code: String) -> Unit

private val sections = listOf("Start here", "Soil care", "Look-up")

/** The code number in a square, as the book prints it. */
@Composable
fun CodeBadge(code: String, small: Boolean = false) {
    val size = if (small) 28.dp else 44.dp
    Box(
        Modifier.size(size).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(code, style = ReadingStyle.copy(fontSize = if (small) 14.sp else 22.sp), color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidebookScreen(onOpenCrop: (crop: String, code: Int?) -> Unit, onOpenTopic: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val code = SoilCondition.fromCode(q)
    val crops = Guidebook.crops.filter { g ->
        q.isEmpty() || code != null || listOfNotNull(g.crop, g.profile.localName, g.scientificName).any { it.contains(q, ignoreCase = true) }
    }
    val topics = Guidebook.topics.filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kasaka Guidebook") },
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
                    "Read your soil numbers. Know what to do. Enter the code (1–7) from your reading, or open your crop's chapter.",
                    style = MaterialTheme.typography.bodyMedium, color = KasakaTheme.colors.muted,
                )
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Code 1–7, crop or topic") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (code != null) item { CodeCard(code, onOpenCrop) }
            if (crops.isNotEmpty()) {
                item { SectionHeader(if (code != null) "Open code ${code.code} for your crop" else "Crop chapters") }
                items(crops, key = { it.crop }) { g -> CropRow(g) { onOpenCrop(g.crop, code?.code) } }
            }
            if (code == null) {
                sections.forEach { section ->
                    val inSection = topics.filter { it.section == section }
                    if (inSection.isNotEmpty()) {
                        item { SectionHeader(section) }
                        items(inSection, key = { it.id }) { t -> TopicRow(t) { onOpenTopic(t.id) } }
                    }
                }
            }
            if (code == null && crops.isEmpty() && topics.isEmpty()) {
                item { EmptyState(Icons.Rounded.Search, "No matching page", "Codes go from 1 to 7. Try a crop name, or clear the search.") }
            }
            item { BookFootnote() }
        }
    }
}

@Composable
private fun CodeCard(c: SoilCondition, onOpenCrop: (String, Int?) -> Unit) {
    KCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CodeBadge("${c.code}")
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(c.title, style = MaterialTheme.typography.titleMedium)
                Text(c.summary, style = MaterialTheme.typography.bodyMedium, color = KasakaTheme.colors.muted)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Guidebook.crops.forEach { g ->
                FilterChip(selected = false, onClick = { onOpenCrop(g.crop, c.code) }, label = { Text("${g.profile.emoji} ${g.crop}") })
            }
        }
    }
}

@Composable
private fun CropThumb(crop: String, size: androidx.compose.ui.unit.Dp) {
    val img = cropImageRes(LocalContext.current, crop)
    Box(
        Modifier.size(size).clip(MaterialTheme.shapes.small).background(KasakaTheme.colors.surface2),
        contentAlignment = Alignment.Center,
    ) {
        if (img != 0) Image(painterResource(img), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(CropProfiles.emoji(crop), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun CropRow(g: CropGuide, onClick: () -> Unit) {
    val p = g.profile
    KCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CropThumb(g.crop, 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(g.crop + (p.localName?.let { " · $it" } ?: ""), style = MaterialTheme.typography.titleSmall)
                Text(
                    "Good pH ${fmt(p.ph.goodFrom)}–${fmt(p.ph.goodTo)} · harvest ${g.harvest}",
                    style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted,
                )
            }
            PageTag(g.page)
        }
    }
}

@Composable
private fun TopicRow(t: GuideTopic, onClick: () -> Unit) {
    KCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Text(t.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            PageTag(t.page)
        }
    }
}

@Composable
private fun PageTag(page: Int) {
    Text("p. $page", style = MaterialTheme.typography.labelSmall, color = KasakaTheme.colors.muted)
}

@Composable
private fun BookFootnote() {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.Info, null, tint = KasakaTheme.colors.muted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Kasaka Guidebook, Region VIII Edition (2026). Page numbers match the printed book. Amounts are per bed of 1 m × 20 m. " +
                "The probe gives a quick field estimate, not a laboratory test; always follow the label on fertilizer bags.",
            style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted,
        )
    }
}

private enum class CropTab(val label: String) { GROWING("Growing"), READING("Reading table"), PLAN("Fertilizer plan"), FIX("Fix-it codes") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideCropScreen(crop: String, code: Int?, onBack: () -> Unit, onOpenTopic: (String) -> Unit) {
    val g = Guidebook.crop(crop)
    var tab by rememberSaveable { mutableStateOf(if (code != null) CropTab.FIX else CropTab.GROWING) }
    val list = rememberLazyListState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(g?.crop ?: crop) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (g == null) {
            EmptyState(Icons.Rounded.Search, "$crop is not in the guidebook", "Open the Guide tab to see the crops the Kasaka Guidebook covers. For other crops the app gives the book's general amounts.", Modifier.padding(padding))
            return@Scaffold
        }
        // Items before the Fix-it cards: header, tabs, intro.
        LaunchedEffect(tab, code) { if (tab == CropTab.FIX && code != null) list.animateScrollToItem(2 + code) }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = list,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { CropHeader(g) }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CropTab.entries.forEach { t -> FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.label) }) }
                }
            }
            when (tab) {
                CropTab.GROWING -> growing(g)
                CropTab.READING -> reading(g)
                CropTab.PLAN -> plan(g)
                CropTab.FIX -> fixIt(g, code, onOpenTopic)
            }
        }
    }
}

@Composable
private fun CropHeader(g: CropGuide) {
    val p = g.profile
    Row(verticalAlignment = Alignment.CenterVertically) {
        CropThumb(g.crop, 72.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(g.crop, style = MaterialTheme.typography.headlineSmall)
            Text(g.scientificName, style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted)
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Fact("Good pH", "${fmt(p.ph.goodFrom)} – ${fmt(p.ph.goodTo)}", Modifier.weight(1f))
        Fact("Harvest", g.harvest, Modifier.weight(1f))
        Fact("Spacing", g.spacing, Modifier.weight(1f))
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(MaterialTheme.shapes.small).background(KasakaTheme.colors.surface2).padding(10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = KasakaTheme.colors.muted)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

private fun LazyListScope.growing(g: CropGuide) {
    item {
        KCard(Modifier.fillMaxWidth()) {
            Title("Growing ${g.crop.lowercase()}", g.page)
            Spacer(Modifier.height(8.dp))
            Steps(g.growing)
        }
    }
    item {
        KCard(Modifier.fillMaxWidth()) {
            Text("Pests and diseases", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(g.pests, style = MaterialTheme.typography.bodyMedium)
        }
    }
    item { Source(g.growingSource) }
}

private fun LazyListScope.reading(g: CropGuide) {
    item {
        KCard(Modifier.fillMaxWidth()) {
            Title("${g.crop}: reading your numbers", g.page + 1)
            Text(g.readingNote, style = MaterialTheme.typography.bodyMedium, color = KasakaTheme.colors.muted)
            Spacer(Modifier.height(12.dp))
            ReadingTable(g.profile)
        }
    }
    item {
        KCard(Modifier.fillMaxWidth()) {
            Text("Example", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            g.example.forEachIndexed { i, line ->
                Text(
                    line,
                    style = if (i == g.example.lastIndex) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold) else MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
    item {
        KCard(Modifier.fillMaxWidth()) {
            Text("Why these numbers?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(g.why, style = MaterialTheme.typography.bodyMedium, color = KasakaTheme.colors.muted)
        }
    }
}

/** The four reading boxes per row, coloured like the reading tiles. */
@Composable
fun ReadingTable(p: CropProfile) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Level.entries.forEach { l ->
            Text(
                l.box.replace("SLIGHTLY ", "SLIGHTLY\n"),
                style = MaterialTheme.typography.labelSmall, color = levelColor(l), textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Nutrient.entries.forEach { n ->
        Spacer(Modifier.height(10.dp))
        Text(if (n == Nutrient.PH) "pH" else "${n.short} (${n.label.lowercase()}) mg/kg", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Level.entries.forEach { l ->
                Column(
                    Modifier.weight(1f).clip(MaterialTheme.shapes.extraSmall).background(levelColor(l).copy(alpha = 0.14f)).padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(rangeText(p, n, l), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                    caption(p, n, l)?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = KasakaTheme.colors.muted, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

private fun rangeText(p: CropProfile, n: Nutrient, l: Level): String {
    val row = p.row(n)
    val step = if (n == Nutrient.PH) 0.1 else 1.0
    fun f(v: Double) = if (n == Nutrient.PH) fmt(v) else v.toInt().toString()
    return when (l) {
        Level.LOW -> "below ${f(row.lowBelow)}"
        Level.SLIGHTLY_LOW -> "${f(row.lowBelow)} – ${f(row.goodFrom - step)}"
        Level.GOOD -> "${f(row.goodFrom)} – ${f(row.goodTo)}"
        Level.HIGH -> "above ${f(row.goodTo)}"
    }
}

private fun caption(p: CropProfile, n: Nutrient, l: Level): String? = when {
    n == Nutrient.PH -> when (l) {
        Level.LOW -> "too sour"
        Level.SLIGHTLY_LOW -> "a bit sour"
        Level.GOOD -> "best ${p.bestPh}"
        Level.HIGH -> "alkaline"
    }
    n == Nutrient.N && l == Level.HIGH -> p.highNitrogenNote
    else -> null
}

private fun LazyListScope.plan(g: CropGuide) {
    item {
        Column {
            Title("${g.crop}: standard fertilizer plan", g.page + 2)
            Text(g.planIntro, style = MaterialTheme.typography.bodyMedium, color = KasakaTheme.colors.muted)
        }
    }
    items(g.plan, key = { it.whenLabel }) { step ->
        KCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
            Text(step.whenLabel, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(step.perBed, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Text("Per hectare: ${step.perHectare}", style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted)
        }
    }
    item {
        KCard(Modifier.fillMaxWidth()) {
            Text("Tips", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Bullets(g.planTips)
        }
    }
    item { Source(g.planSource) }
}

private fun LazyListScope.fixIt(g: CropGuide, highlight: Int?, onOpenTopic: (String) -> Unit) {
    item {
        Column {
            Title("${g.crop}: Fix-it codes", g.page + 3)
            Text(g.fixIntro, style = MaterialTheme.typography.bodyMedium, color = KasakaTheme.colors.muted)
        }
    }
    itemsIndexed(g.fixes, key = { _, f -> f.condition.code }) { _, f -> FixCard(f, f.condition.code == highlight) }
    item {
        KCard(Modifier.fillMaxWidth()) {
            Text("▲ Too high", style = MaterialTheme.typography.titleMedium, color = levelColor(Level.HIGH))
            Spacer(Modifier.height(6.dp))
            g.tooHigh.forEach { (n, text) ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("${n.short} high", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(64.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = { onOpenTopic("lime") }) { Text("Fixing sour soil (p. ${Guidebook.topic("lime")?.page})") }
            TextButton(onClick = { onOpenTopic("fertilizers") }) { Text("Measuring fertilizer (p. ${Guidebook.topic("fertilizers")?.page})") }
        }
    }
}

@Composable
private fun FixCard(f: Fix, highlighted: Boolean) {
    KCard(
        Modifier.fillMaxWidth(),
        contentPadding = 14.dp,
        container = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainer,
        border = if (highlighted) MaterialTheme.colorScheme.primary else KasakaTheme.colors.border,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            CodeBadge("${f.condition.code}")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(f.condition.title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(f.text, style = MaterialTheme.typography.bodyMedium)
                if (highlighted) {
                    Spacer(Modifier.height(4.dp))
                    Text("Your reading's code", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideTopicScreen(id: String, onBack: () -> Unit) {
    val t = Guidebook.topic(id)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t?.section ?: "Guidebook") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (t == null) {
            EmptyState(Icons.Rounded.Search, "Page not found", "Update the app for the latest Guidebook pages.", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Title(t.title, t.page, large = true) }
            items(t.blocks.size) { i -> Block(t.blocks[i]) }
        }
    }
}

@Composable
private fun Block(b: GuideBlock) {
    when (b) {
        is GuideBlock.Heading -> Text(b.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
        is GuideBlock.Para -> Text(b.text, style = MaterialTheme.typography.bodyLarge)
        is GuideBlock.Steps -> KCard(Modifier.fillMaxWidth()) { Steps(b.items) }
        is GuideBlock.Bullets -> Bullets(b.items)
        is GuideBlock.Note -> KCard(Modifier.fillMaxWidth(), container = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), contentPadding = 14.dp) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(b.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        is GuideBlock.Table -> TableBlock(b)
    }
}

/** Two columns side by side; wider tables as one small card per row (they don't fit a phone). */
@Composable
private fun TableBlock(t: GuideBlock.Table) {
    KCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        t.rows.forEachIndexed { i, row ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            if (t.header.size == 2) {
                Row {
                    Text(row[0], style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.4f))
                    Spacer(Modifier.width(10.dp))
                    Text(row[1], style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
                }
            } else {
                Text(row[0], style = MaterialTheme.typography.labelLarge)
                row.drop(1).forEachIndexed { j, cell ->
                    Row(Modifier.padding(top = 2.dp)) {
                        Text(t.header[j + 1], style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted, modifier = Modifier.weight(0.4f))
                        Spacer(Modifier.width(10.dp))
                        Text(cell, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String, page: Int, large: Boolean = false) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text, style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        PageTag(page)
    }
}

@Composable
private fun Steps(items: List<String>) {
    items.forEachIndexed { i, step ->
        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) { Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary) }
            Spacer(Modifier.width(12.dp))
            Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Bullets(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { text ->
            Row(verticalAlignment = Alignment.Top) {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(16.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Where the advice comes from; the book marks which sources are from Region 8 and which are national. */
@Composable
private fun Source(text: String) {
    Column {
        Text("Sources", style = MaterialTheme.typography.labelMedium, color = KasakaTheme.colors.muted)
        Text(text, style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted)
    }
}

private fun fmt(d: Double) = String.format(Locale.US, "%.1f", d)
