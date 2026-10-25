package com.sakasense.kasaka.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sakasense.kasaka.ui.theme.KasakaTheme

/**
 * Line chart for one soil metric over time, with the target zone shaded so a farmer can see at a
 * glance whether the line sits where it should.
 */
@Composable
fun TrendChart(
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
    target: ClosedFloatingPointRange<Double>? = null,
    format: (Double) -> String = { it.toInt().toString() },
    startLabel: String = "",
    endLabel: String = "",
    description: String = "Trend chart",
) {
    val muted = KasakaTheme.colors.muted
    val grid = KasakaTheme.colors.border
    val zone = KasakaTheme.colors.healthy.copy(alpha = 0.08f)
    if (values.size < 2) {
        Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text("Not enough readings yet", style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        return
    }
    val lo = minOf(values.min(), target?.start ?: values.min())
    // Scale to the data and the zone's lower edge only, so a big zone doesn't flatten the line.
    val hi = maxOf(values.max(), target?.start ?: values.max())
    val pad = ((hi - lo) * 0.15).coerceAtLeast(0.5)
    val min = lo - pad
    val max = hi + pad

    Column(modifier.semantics { contentDescription = "$description, latest ${format(values.last())}" }) {
        Row(Modifier.fillMaxWidth()) {
            Text("max ${format(values.max())}", style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.weight(1f))
            Text("latest ${format(values.last())}", style = MaterialTheme.typography.labelSmall, color = color)
        }
        Canvas(Modifier.fillMaxWidth().height(height).padding(vertical = 8.dp)) {
            val w = size.width
            val h = size.height
            fun y(v: Double) = (h - ((v - min) / (max - min)) * h).toFloat()
            fun x(i: Int) = w * i / (values.size - 1)

            if (target != null) {
                val top = y(target.endInclusive.coerceAtMost(max))
                val bottom = y(target.start.coerceAtLeast(min))
                drawRect(zone, topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(w, bottom - top))
            }
            repeat(4) { i ->
                val gy = h * i / 3
                drawLine(grid, Offset(0f, gy), Offset(w, gy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
            }

            // Smooth curve through the points (cubic, horizontal tangents).
            val line = Path()
            values.forEachIndexed { i, v ->
                if (i == 0) line.moveTo(x(0), y(v)) else {
                    val px = x(i - 1); val py = y(values[i - 1])
                    val cx = (px + x(i)) / 2
                    line.cubicTo(cx, py, cx, y(v), x(i), y(v))
                }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
            drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val last = Offset(x(values.lastIndex), y(values.last()))
            drawCircle(color.copy(alpha = 0.25f), 9.dp.toPx(), last)
            drawCircle(color, 4.5.dp.toPx(), last)
        }
        Row(Modifier.fillMaxWidth()) {
            Text(startLabel, style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.weight(1f))
            Text("min ${format(values.min())}", style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.weight(1f))
            Text(endLabel, style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}
