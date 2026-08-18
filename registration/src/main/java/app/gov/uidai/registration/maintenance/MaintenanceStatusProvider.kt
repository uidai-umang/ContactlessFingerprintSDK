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
        // TODO -- confirm this matches the exact key name set up in
        // Firebase Remote Config console.
        private const val KEY_MAINTENANCE_MODE = "maintenance_mode"
        private const val MIN_FETCH_INTERVAL_SECONDS = 300L // 5 min, avoid hammering on every launch
    }

    override suspend fun isUnderMaintenance(): Boolean {
        return try {
            val settings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = MIN_FETCH_INTERVAL_SECONDS
            }
            remoteConfig.setConfigSettingsAsync(settings).await()
            remoteConfig.fetchAndActivate().await()
            remoteConfig.getBoolean(KEY_MAINTENANCE_MODE)
        } catch (e: Exception) {
            // Fetch failure (no network, Remote Config unreachable) should
            // NOT block the app -- fail open, not closed. A maintenance
            // check that itself requires network shouldn't be the thing
            // that locks residents out when Remote Config is briefly
            // unreachable but everything else works fine.
            false
        }
    }
}