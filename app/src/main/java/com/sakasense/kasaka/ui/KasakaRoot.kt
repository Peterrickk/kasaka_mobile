package com.sakasense.kasaka.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.sakasense.kasaka.data.Session
import com.sakasense.kasaka.domain.Guidebook
import com.sakasense.kasaka.domain.SoilCondition
import com.sakasense.kasaka.ui.alerts.AlertsScreen
import com.sakasense.kasaka.ui.auth.AuthScreen
import com.sakasense.kasaka.ui.guidebook.GuideCropScreen
import com.sakasense.kasaka.ui.guidebook.GuideTopicScreen
import com.sakasense.kasaka.ui.guidebook.GuidebookScreen
import com.sakasense.kasaka.ui.home.HomeScreen
import com.sakasense.kasaka.ui.onboarding.OnboardingScreen
import com.sakasense.kasaka.ui.plots.PlotDetailScreen
import com.sakasense.kasaka.ui.plots.PlotsScreen
import com.sakasense.kasaka.ui.profile.ProfileScreen
import com.sakasense.kasaka.ui.scan.ScanScreen
import com.sakasense.kasaka.ui.theme.KasakaTheme
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable object OnboardingRoute
@Serializable object AuthRoute
@Serializable object HomeRoute
@Serializable object PlotsRoute
@Serializable data class ScanRoute(val plotId: String? = null)
@Serializable object GuidebookRoute
@Serializable object ProfileRoute
@Serializable object AlertsRoute
@Serializable data class PlotDetailRoute(val plotId: String)
/** A crop chapter of the Kasaka Guidebook; [code] 1–7 opens its Fix-it page at that code, 0 the chapter start. */
@Serializable data class GuideCropRoute(val crop: String, val code: Int = 0)
@Serializable data class GuideTopicRoute(val id: String)

private data class Tab(val label: String, val icon: ImageVector, val route: Any, val type: KClass<*>)

private val tabs = listOf(
    Tab("Home", Icons.Rounded.Home, HomeRoute, HomeRoute::class),
    Tab("Plots", Icons.Rounded.Grass, PlotsRoute, PlotsRoute::class),
    Tab("Scan", Icons.Rounded.Sensors, ScanRoute(), ScanRoute::class),
    Tab("Guide", Icons.AutoMirrored.Rounded.MenuBook, GuidebookRoute, GuidebookRoute::class),
    Tab("Profile", Icons.Rounded.Person, ProfileRoute, ProfileRoute::class),
)

@Composable
fun KasakaRoot(session: Session) {
    val nav = rememberNavController()
    val start: Any = remember {
        when {
            !session.onboarded -> OnboardingRoute
            session.user == null -> AuthRoute
            else -> HomeRoute
        }
    }
    val signedIn = session.user != null

    // Signing out anywhere returns to the sign-in screen with a clean back stack.
    LaunchedEffect(signedIn) {
        val current = nav.currentDestination
        if (!signedIn && current != null && !current.hasRoute(AuthRoute::class) && !current.hasRoute(OnboardingRoute::class)) {
            nav.navigate(AuthRoute) { popUpTo(0) { inclusive = true } }
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = destination != null && tabs.any { t -> destination.hierarchy.any { it.hasRoute(t.type) } } &&
        !(destination.hasRoute(ScanRoute::class) && backStack?.toRoute<ScanRoute>()?.plotId != null)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { if (showBar) KasakaBottomBar(nav, tabs.firstOrNull { t -> destination.hasRoute(t.type) }) },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = start,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
                .consumeWindowInsets(PaddingValues(bottom = padding.calculateBottomPadding())),
            enterTransition = { fadeIn() + slideInHorizontally { it / 12 } },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() + slideOutHorizontally { it / 12 } },
        ) {
            composable<OnboardingRoute> {
                OnboardingScreen(onDone = {
                    nav.navigate(if (signedIn) HomeRoute else AuthRoute) { popUpTo(0) { inclusive = true } }
                })
            }
            composable<AuthRoute> {
                AuthScreen(onSignedIn = { nav.navigate(HomeRoute) { popUpTo(0) { inclusive = true } } })
            }
            composable<HomeRoute> {
                HomeScreen(
                    userName = session.user?.name.orEmpty(),
                    onOpenPlot = { nav.navigate(PlotDetailRoute(it)) },
                    onScan = { nav.navigateTab(ScanRoute()) },
                    onAlerts = { nav.navigate(AlertsRoute) },
                    onPlots = { nav.navigateTab(PlotsRoute) },
                )
            }
            composable<PlotsRoute> {
                PlotsScreen(onOpenPlot = { nav.navigate(PlotDetailRoute(it)) })
            }
            composable<ScanRoute> { entry ->
                val route = entry.toRoute<ScanRoute>()
                ScanScreen(
                    presetPlotId = route.plotId,
                    onBack = if (route.plotId != null) ({ nav.popBackStack() }) else null,
                    onOpenPlot = { id ->
                        nav.navigate(PlotDetailRoute(id)) {
                            if (route.plotId != null) popUpTo<PlotDetailRoute> { inclusive = true }
                        }
                    },
                    onOpenGuide = { crop, code -> nav.navigate(guideRoute(crop, code)) },
                )
            }
            composable<GuidebookRoute> {
                GuidebookScreen(
                    onOpenCrop = { crop, code -> nav.navigate(GuideCropRoute(crop, code ?: 0)) },
                    onOpenTopic = { nav.navigate(GuideTopicRoute(it)) },
                )
            }
            composable<GuideCropRoute> { entry ->
                val route = entry.toRoute<GuideCropRoute>()
                GuideCropScreen(
                    crop = route.crop,
                    code = route.code.takeIf { it in 1..7 },
                    onBack = { nav.popBackStack() },
                    onOpenTopic = { nav.navigate(GuideTopicRoute(it)) },
                )
            }
            composable<GuideTopicRoute> { entry ->
                GuideTopicScreen(id = entry.toRoute<GuideTopicRoute>().id, onBack = { nav.popBackStack() })
            }
            composable<ProfileRoute> {
                ProfileScreen(session = session)
            }
            composable<AlertsRoute> {
                AlertsScreen(onBack = { nav.popBackStack() }, onOpenPlot = { nav.navigate(PlotDetailRoute(it)) })
            }
            composable<PlotDetailRoute> { entry ->
                PlotDetailScreen(
                    plotId = entry.toRoute<PlotDetailRoute>().plotId,
                    onBack = { nav.popBackStack() },
                    onScan = { nav.navigate(ScanRoute(it)) },
                    onOpenGuide = { crop, code -> nav.navigate(guideRoute(crop, code)) },
                )
            }
        }
    }
}

private fun NavHostController.navigateTab(route: Any) = navigate(route) {
    popUpTo<HomeRoute> { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun KasakaBottomBar(nav: NavHostController, current: Tab?) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
        tabs.forEach { tab ->
            val selected = tab == current
            val isScan = tab.type == ScanRoute::class
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) nav.navigateTab(tab.route) },
                icon = {
                    if (isScan) {
                        // The primary action gets a filled, always-visible button.
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) { Icon(tab.icon, tab.label, tint = MaterialTheme.colorScheme.onPrimary) }
                    } else {
                        Icon(tab.icon, tab.label)
                    }
                },
                // The Scan button is self-explanatory and taller; a label would sit lower than the others.
                label = if (isScan) null else ({ Text(tab.label) }),
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = if (isScan) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    unselectedIconColor = KasakaTheme.colors.muted,
                    unselectedTextColor = KasakaTheme.colors.muted,
                ),
            )
        }
    }
}

/** A recommendation's crop + code opens that Fix-it page; anything else (e.g. an old 4-digit code) the Code finder. */
private fun guideRoute(crop: String?, code: String): Any {
    val guide = Guidebook.crop(crop)
    val c = SoilCondition.fromCode(code)
    return if (guide != null && c != null) GuideCropRoute(guide.crop, c.code) else GuideTopicRoute("codes")
}
