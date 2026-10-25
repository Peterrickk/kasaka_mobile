package com.sakasense.kasaka.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sakasense.kasaka.LocalAppContainer
import com.sakasense.kasaka.ui.components.LogoBadge
import com.sakasense.kasaka.ui.theme.KasakaTheme
import kotlinx.coroutines.launch

private data class Page(val icon: ImageVector, val eyebrow: String, val title: String, val body: String)

private val pages = listOf(
    Page(
        Icons.Rounded.Sensors, "MEASURE",
        "Know your soil.",
        "Push the SakaSense probe into the ground and read pH, nitrogen, phosphorus and potassium in seconds.",
    ),
    Page(
        Icons.Rounded.Insights, "UNDERSTAND",
        "Grow with confidence.",
        "Get a soil health score, the best crops for your plot and a clear fertilizer prescription — in plain language.",
    ),
    Page(
        Icons.Rounded.CloudOff, "ANYWHERE",
        "Works in the field.",
        "Pairs over Bluetooth and keeps every reading on your phone. No signal needed.",
    ),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val session = LocalAppContainer.current.session
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { pages.size }
    val finish: () -> Unit = { scope.launch { session.completeOnboarding(); onDone() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoBadge(36.dp)
            Spacer(Modifier.width(10.dp))
            Text("SakaSense", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (pager.currentPage < pages.lastIndex) TextButton(onClick = finish) { Text("Skip") }
        }

        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
            val page = pages[i]
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Hero(page.icon)
                Spacer(Modifier.height(40.dp))
                Text(page.eyebrow, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(page.title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(
                    page.body, style = MaterialTheme.typography.bodyLarge, color = KasakaTheme.colors.muted,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                val active = pager.currentPage == i
                val w by animateDpAsState(if (active) 24.dp else 8.dp, label = "dot")
                val c by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else KasakaTheme.colors.surface2, label = "dotColor")
                Box(Modifier.padding(horizontal = 4.dp).height(8.dp).width(w).clip(CircleShape).background(c))
            }
        }
        Button(
            onClick = {
                if (pager.currentPage < pages.lastIndex) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } else finish()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(if (pager.currentPage < pages.lastIndex) "Next" else "Get started") }
    }
}

@Composable
private fun Hero(icon: ImageVector) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(220.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(primary.copy(alpha = 0.28f), primary.copy(alpha = 0.04f)))),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(132.dp).clip(CircleShape).background(primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = primary, modifier = Modifier.size(64.dp))
        }
    }
}
