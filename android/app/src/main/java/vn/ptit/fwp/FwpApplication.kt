package vn.ptit.fwp

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import vn.ptit.fwp.core.ai.RemoteAiClient
import vn.ptit.fwp.core.data.FwpRepository
import vn.ptit.fwp.core.data.SettingsStore
import vn.ptit.fwp.core.data.local.FwpDatabase

class FwpApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Application) {
    val database: FwpDatabase = FwpDatabase.get(context)
    val settingsStore = SettingsStore(context)
    val repository = FwpRepository(database, settingsStore)
    val remoteAi = RemoteAiClient()
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        appScope.launch {
            repository.initialize()
        }
    }
}
