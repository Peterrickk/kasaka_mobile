package com.sakasense.kasaka

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.sakasense.kasaka.data.Session
import com.sakasense.kasaka.data.ThemeMode
import com.sakasense.kasaka.ui.KasakaRoot
import com.sakasense.kasaka.ui.theme.KasakaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var sessionLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !sessionLoaded }

        val container = (application as KasakaApp).container
        lifecycleScope.launch { runCatching { container.soil.refreshAlerts() } }
        // A demo account signed in before the app read temperature/humidity has readings without them.
        lifecycleScope.launch {
            runCatching { if (container.session.session.first().user?.isDemo == true) container.soil.backfillDemoClimate() }
        }

        setContent {
            val session: Session? by container.session.session.collectAsStateWithLifecycle(initialValue = null)
            val theme = session?.theme ?: ThemeMode.SYSTEM
            val dark = when (theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                val transparent = Color.Transparent.toArgb()
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                )
                onDispose {}
            }
            CompositionLocalProvider(LocalAppContainer provides container) {
                KasakaTheme(theme) {
                    session?.let {
                        SideEffect { sessionLoaded = true }
                        KasakaRoot(it)
                    }
                }
            }
        }
    }
}
