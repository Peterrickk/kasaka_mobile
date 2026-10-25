package com.sakasense.kasaka.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakasense.kasaka.R
import com.sakasense.kasaka.data.local.PlotStatus
import com.sakasense.kasaka.domain.Level
import com.sakasense.kasaka.domain.Nutrient
import com.sakasense.kasaka.domain.NutrientBand
import com.sakasense.kasaka.ui.theme.KasakaTheme
import com.sakasense.kasaka.ui.theme.ReadingStyle

/** The standard card: tinted surface with the brand's hairline border. */
@Composable
fun KCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    border: Color = KasakaTheme.colors.border,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val inner: @Composable () -> Unit = { Column(Modifier.padding(contentPadding), content = content) }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = container, border = BorderStroke(1.dp, border), content = inner)
    } else {
        Surface(modifier = modifier, shape = shape, color = container, border = BorderStroke(1.dp, border), content = inner)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun statusColor(status: PlotStatus): Color = when (status) {
    PlotStatus.HEALTHY -> KasakaTheme.colors.healthy
    PlotStatus.NEEDS_ATTENTION -> KasakaTheme.colors.attention
    PlotStatus.ACTION_REQUIRED -> KasakaTheme.colors.action
}

fun statusLabel(status: PlotStatus) = when (status) {
    PlotStatus.HEALTHY -> "Healthy"
    PlotStatus.NEEDS_ATTENTION -> "Needs attention"
    PlotStatus.ACTION_REQUIRED -> "Action required"
}

@Composable
fun levelColor(level: Level): Color = when (level) {
    Level.LOW -> KasakaTheme.colors.action
    Level.FAIR -> KasakaTheme.colors.attention
    Level.GOOD -> KasakaTheme.colors.healthy
    Level.HIGH -> KasakaTheme.colors.info
}

@Composable
fun nutrientColor(n: Nutrient): Color = when (n) {
    Nutrient.PH -> KasakaTheme.colors.ph
    Nutrient.N -> KasakaTheme.colors.nitrogen
    Nutrient.P -> KasakaTheme.colors.phosphorus
    Nutrient.K -> KasakaTheme.colors.potassium
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, dot: Boolean = true) {
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun StatusChip(status: PlotStatus, modifier: Modifier = Modifier) = Pill(statusLabel(status), statusColor(status), modifier)

/** Circular soil-health gauge, animated on first show. */
@Composable
fun HealthRing(
    score: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    stroke: Dp = 12.dp,
    caption: String = "Soil health",
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(score) { anim.animateTo((score ?: 0) / 100f, tween(900, easing = FastOutSlowInEasing)) }
    val color = when {
        score == null -> KasakaTheme.colors.muted
        score >= 78 -> KasakaTheme.colors.healthy
        score >= 60 -> KasakaTheme.colors.attention
        else -> KasakaTheme.colors.action
    }
    val track = KasakaTheme.colors.surface2
    Box(
        modifier
            .size(size)
            .semantics { contentDescription = "$caption ${score ?: "unknown"} out of 100" },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 135f, 270f, false, topLeft, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawArc(
                brush = androidx.compose.ui.graphics.Brush.sweepGradient(listOf(color.copy(alpha = 0.55f), color, color)),
                startAngle = 135f, sweepAngle = 270f * anim.value, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                score?.toString() ?: "—",
                style = ReadingStyle.copy(fontSize = (size.value * 0.27f).sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(caption, style = MaterialTheme.typography.labelSmall, color = KasakaTheme.colors.muted, textAlign = TextAlign.Center)
        }
    }
}

/** One soil value (pH / N / P / K) with its level and a bar against the target. */
@Composable
fun NutrientTile(band: NutrientBand, modifier: Modifier = Modifier) {
    val accent = nutrientColor(band.nutrient)
    val lvl = levelColor(band.level)
    KCard(modifier, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Text(band.nutrient.short, style = MaterialTheme.typography.labelMedium, color = accent) }
            Spacer(Modifier.width(8.dp))
            Text(band.nutrient.label, style = MaterialTheme.typography.labelMedium, color = KasakaTheme.colors.muted, maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (band.nutrient == Nutrient.PH) "%.1f".format(java.util.Locale.US, band.value) else band.value.toInt().toString(),
                style = ReadingStyle.copy(fontSize = 26.sp),
            )
            if (band.nutrient.unit.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(band.nutrient.unit, style = MaterialTheme.typography.labelSmall, color = KasakaTheme.colors.muted, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        LevelMeter(band.level, lvl)
        Spacer(Modifier.height(6.dp))
        Row {
            Text(band.label, style = MaterialTheme.typography.labelMedium, color = lvl, modifier = Modifier.weight(1f))
            Text(band.target, style = MaterialTheme.typography.labelSmall, color = KasakaTheme.colors.muted)
        }
    }
}

/** 4-segment meter: Low · Fair · Good · High. */
@Composable
fun LevelMeter(level: Level, color: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Level.entries.forEach { l ->
            Box(
                Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(if (l.ordinal <= level.ordinal) color else KasakaTheme.colors.surface2)
            )
        }
    }
}

@Composable
fun ScoreBar(label: String, score: Int, modifier: Modifier = Modifier, leading: String? = null, highlight: Boolean = false) {
    val color = if (highlight) MaterialTheme.colorScheme.primary else KasakaTheme.colors.muted
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) Text(leading, modifier = Modifier.padding(end = 8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("$score", style = ReadingStyle.copy(fontSize = 14.sp), color = color)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(KasakaTheme.colors.surface2)) {
            Box(
                Modifier
                    .fillMaxWidth(score.coerceIn(0, 100) / 100f)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            )
        }
    }
}

@Composable
fun LogoBadge(size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.logo_mark), contentDescription = "SakaSense", modifier = Modifier.size(size * 0.72f))
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = KasakaTheme.colors.muted, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.52f)) }
}
