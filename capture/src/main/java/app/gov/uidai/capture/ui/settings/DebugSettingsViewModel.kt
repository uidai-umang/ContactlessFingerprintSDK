package app.gov.uidai.capture.ui.settings

import androidx.lifecycle.ViewModel
import app.gov.uidai.capture.pref.PreferenceStore
import app.gov.uidai.capture.pref.model.PreferenceGroup
import app.gov.uidai.capture.pref.model.PreferenceParam
import app.gov.uidai.capture.ui.settings.provider.SettingProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DebugSettingsViewModel @Inject constructor(
    private val settingProviders: Set<@JvmSuppressWildcards SettingProvider>,
    private val preferenceStore: PreferenceStore
) : ViewModel() {

    fun getSettings(): List<PreferenceGroup> {
        return settingProviders.map { it.getSettings() }.sortedBy {
            it.title
        }
    }

    fun <T> save(setting: PreferenceParam<T>) {
        preferenceStore.save(setting)
    }

    fun <T> get(setting: PreferenceParam<T>): T {
        return preferenceStore.get(setting)
    }
}
