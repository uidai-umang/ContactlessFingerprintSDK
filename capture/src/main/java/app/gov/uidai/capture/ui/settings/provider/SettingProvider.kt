package app.gov.uidai.capture.ui.settings.provider

import app.gov.uidai.capture.pref.PreferenceStore
import app.gov.uidai.capture.pref.model.PreferenceGroup
import app.gov.uidai.capture.pref.model.PreferenceParam

class SettingProvider(
    private val preferenceStore: PreferenceStore,
    private val preferenceGroup: PreferenceGroup
) {
    fun getSettings(): PreferenceGroup {
        preferenceGroup.all.forEach { loadValue(it) }
        return preferenceGroup
    }

    private fun <T> loadValue(setting: PreferenceParam<T>) {
        setting.currentValue = preferenceStore.get(setting)
    }
}
