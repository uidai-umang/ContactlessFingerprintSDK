package app.gov.uidai.capture.pref

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.gov.uidai.capture.pref.model.PreferenceParam
import app.gov.uidai.capture.pref.model.PreferenceType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferenceStore @Inject constructor(@ApplicationContext context: Context) {

    companion object {
        private const val PREF_FILE = "debug_settings"

        // Registration's former separate store (operator / resident prefs).
        // Copied into PREF_FILE once, then cleared.
        private const val LEGACY_REGISTRATION_PREF_FILE = "attendance_app_pref"
    }

    private val prefStore = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    init {
        migrateLegacyStore(context.getSharedPreferences(LEGACY_REGISTRATION_PREF_FILE, Context.MODE_PRIVATE))
    }

    fun <T> save(setting: PreferenceParam<T>) {
        prefStore.edit {
            when (setting.type) {
                is PreferenceType.BOOLEAN -> putBoolean(setting.key, setting.currentValue as Boolean)
                is PreferenceType.FLOAT -> putFloat(setting.key, setting.currentValue as Float)
                is PreferenceType.INT -> putInt(setting.key, setting.currentValue as Int)
                is PreferenceType.DOUBLE -> putLong(setting.key, (setting.currentValue as Double).toRawBits())
                is PreferenceType.STRING -> putString(setting.key, setting.currentValue as String)
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
            is PreferenceType.DOUBLE -> {
                val rawBits = prefStore.getLong(setting.key, (setting.defaultValue as Double).toRawBits())
                Double.fromBits(rawBits) as T
            }
            is PreferenceType.STRING -> prefStore.getString(setting.key, setting.defaultValue as String) as T
            is PreferenceType.CHOICE<*> -> {
                val savedValueName = prefStore.getString(setting.key, (setting.defaultValue as Enum<*>).name)
                val enumValue = (setting.type).options.find { it.name == savedValueName }
                (enumValue ?: setting.defaultValue) as T
            }
        }
    }

    /** Copies every entry not already present in [prefStore], then clears [legacy]. */
    @Suppress("UNCHECKED_CAST")
    private fun migrateLegacyStore(legacy: SharedPreferences) {
        val entries = legacy.all
        if (entries.isEmpty()) return
        prefStore.edit(commit = true) {
            entries.forEach { (key, value) ->
                if (prefStore.contains(key)) return@forEach
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Float -> putFloat(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is String -> putString(key, value)
                    is Set<*> -> putStringSet(key, value as Set<String>)
                }
            }
        }
        legacy.edit(commit = true) { clear() }
    }
}
