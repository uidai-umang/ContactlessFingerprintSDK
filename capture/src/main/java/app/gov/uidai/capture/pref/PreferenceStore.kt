package app.gov.uidai.capture.pref

import android.content.Context
import androidx.core.content.edit
import app.gov.uidai.capture.pref.model.PreferenceParam
import app.gov.uidai.capture.pref.model.PreferenceType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferenceStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefStore = context.getSharedPreferences("debug_settings", Context.MODE_PRIVATE)

    fun <T> save(setting: PreferenceParam<T>) {
        prefStore.edit {
            when (setting.type) {
                is PreferenceType.BOOLEAN -> putBoolean(setting.key, setting.currentValue as Boolean)
                is PreferenceType.FLOAT -> putFloat(setting.key, setting.currentValue as Float)
                is PreferenceType.INT -> putInt(setting.key, setting.currentValue as Int)
                is PreferenceType.CHOICE<*> -> putString(setting.key, (setting.currentValue as Enum<*>).name)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(setting: PreferenceParam<T>): T {
        return when (setting.type) {
            is PreferenceType.BOOLEAN -> prefStore.getBoolean(setting.key, setting.defaultValue as Boolean) as T
            is PreferenceType.FLOAT -> prefStore.getFloat(setting.key, setting.defaultValue as Float) as T
            is PreferenceType.INT -> prefStore.getInt(setting.key, setting.defaultValue as Int) as T
            is PreferenceType.CHOICE<*> -> {
                val savedValueName = prefStore.getString(setting.key, (setting.defaultValue as Enum<*>).name)
                val enumValue = (setting.type).options.find { it.name == savedValueName }
                (enumValue ?: setting.defaultValue) as T
            }
        }
    }
}