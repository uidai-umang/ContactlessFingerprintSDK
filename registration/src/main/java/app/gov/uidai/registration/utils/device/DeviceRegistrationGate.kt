package app.gov.uidai.registration.utils.device

import android.content.Context

object DeviceRegistrationGate {
    private const val PREFS_NAME = "device_registration"
    private const val KEY_REGISTERED = "registered"

    fun isRegistered(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_REGISTERED, false)
    }

    fun markRegistered(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REGISTERED, true)
            .apply()
    }
}