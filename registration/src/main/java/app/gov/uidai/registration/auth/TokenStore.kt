package app.gov.uidai.registration.auth

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun save(accessToken: String, refreshToken: String, expiresInSeconds: Int) {
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + expiresInSeconds * 1000L)
            .apply()
    }

    fun saveOperatorRefId(operatorRefId: String) {
        preferences.edit().putString(KEY_OPERATOR_REF_ID, operatorRefId).apply()
    }

    fun getAccessToken(): String? = preferences.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = preferences.getString(KEY_REFRESH_TOKEN, null)
    fun getOperatorRefId(): String? = preferences.getString(KEY_OPERATOR_REF_ID, null)
    fun hasSession(): Boolean = getRefreshToken() != null

    // Keeps operator_ref_id so a silent re-login is possible.
    fun clearTokens() {
        preferences.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .apply()
    }

    companion object {
        private const val PREF_NAME = "clf_auth"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "access_token_expires_at"
        private const val KEY_OPERATOR_REF_ID = "operator_ref_id"
    }
}