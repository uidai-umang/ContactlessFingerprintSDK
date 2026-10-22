package app.gov.uidai.registration.maintenance

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

interface MaintenanceStatusProvider {
    suspend fun isUnderMaintenance(): Boolean
}

@Singleton
class FirebaseMaintenanceStatusProvider @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig
) : MaintenanceStatusProvider {

    companion object {
        private const val KEY_UNDER_MAINTENANCE = "under_maintenance"
        private const val MIN_FETCH_INTERVAL_SECONDS = 300L
    }

    override suspend fun isUnderMaintenance(): Boolean {
        return try {
            val settings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = MIN_FETCH_INTERVAL_SECONDS
            }
            remoteConfig.setConfigSettingsAsync(settings).await()
            remoteConfig.fetchAndActivate().await()
            remoteConfig.getBoolean(KEY_UNDER_MAINTENANCE)
        } catch (e: Exception) {
            false
        }
    }
}