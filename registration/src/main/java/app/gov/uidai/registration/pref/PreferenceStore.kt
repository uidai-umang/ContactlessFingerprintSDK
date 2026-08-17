package app.gov.uidai.registration.pref

import android.content.Context
import androidx.core.content.edit
import app.gov.uidai.registration.pref.model.PreferenceParam
import app.gov.uidai.registration.pref.model.PreferenceType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferenceStore @Inject constructor(
    @ApplicationContext context: Context
) {

    private val prefStore =
        context.getSharedPreferences("attendance_app_pref", Context.MODE_PRIVATE)

    fun <T> save(pref: PreferenceParam<T>) {
        prefStore.edit {
            when (pref.type) {
                is PreferenceType.BOOLEAN -> putBoolean(
                    pref.key,
                    pref.currentValue as Boolean
                )
                is PreferenceType.INT -> putInt(
                    pref.key,
                    pref.currentValue as Int
                )
                is PreferenceType.DOUBLE -> putLong(
                    pref.key,
                    (pref.currentValue as Double).toRawBits()
                )
                is PreferenceType.STRING -> putString(
                    pref.key,
                    pref.currentValue as String
                )
                is PreferenceType.CHOICE<*> -> putString(
                    pref.key,
                    (pref.currentValue as Enum<*>).name
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(pref: PreferenceParam<T>): T {
        return when (pref.type) {
            is PreferenceType.BOOLEAN -> prefStore.getBoolean(
                pref.key,
                pref.defaultValue as Boolean
            ) as T

            is PreferenceType.INT -> prefStore.getInt(
                pref.key,
                pref.defaultValue as Int
            ) as T

            is PreferenceType.DOUBLE -> {
                val rawBits = prefStore.getLong(
                    pref.key,
                    (pref.defaultValue as Double).toRawBits()
                )
                Double.fromBits(rawBits) as T
            }

            is PreferenceType.STRING -> prefStore.getString(
                pref.key,
                pref.defaultValue as String
            ) as T

            is PreferenceType.CHOICE<*> -> {
                val savedValueName = prefStore.getString(
                    pref.key,
                    (pref.defaultValue as Enum<*>).name
                )
                val enumValue = (pref.type).options.find { it.name == savedValueName }
                (enumValue ?: pref.defaultValue) as T
            }

        }
    }
}