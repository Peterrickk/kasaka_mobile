package com.sakasense.kasaka

import android.app.Application
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.sakasense.kasaka.data.SessionRepository
import com.sakasense.kasaka.data.SoilRepository
import com.sakasense.kasaka.data.local.KasakaDatabase
import com.sakasense.kasaka.device.DeviceManager

class KasakaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency container — small enough that a DI framework isn't worth the build time. */
class AppContainer(context: Context) {
    private val db by lazy { KasakaDatabase.create(context) }
    val soil by lazy { SoilRepository(db) }
    val session by lazy { SessionRepository(context) }
    val devices by lazy { DeviceManager(context) }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
