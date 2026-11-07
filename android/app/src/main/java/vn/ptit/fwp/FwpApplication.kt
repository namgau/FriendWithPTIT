package vn.ptit.fwp

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import vn.ptit.fwp.core.ai.RemoteAiClient
import vn.ptit.fwp.core.data.CampusRepository
import vn.ptit.fwp.core.data.FwpRepository
import vn.ptit.fwp.core.data.SettingsStore
import vn.ptit.fwp.core.data.BackendSessionManager
import vn.ptit.fwp.core.data.SecureSessionStore
import vn.ptit.fwp.core.data.BackendProfileRepository
import vn.ptit.fwp.core.data.BackendSocialRepository
import vn.ptit.fwp.core.data.local.FwpDatabase
import vn.ptit.fwp.core.data.remote.CampusApiFactory

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
    val campusApi = CampusApiFactory()
    val backendSession = BackendSessionManager(campusApi, SecureSessionStore(context), settingsStore)
    val backendProfile = BackendProfileRepository(backendSession)
    val backendSocial = BackendSocialRepository(backendSession, settingsStore.settings)
    val campusRepository = CampusRepository(backendSession)
    val remoteAi = RemoteAiClient()
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        appScope.launch {
            repository.initialize()
        }
    }
}
