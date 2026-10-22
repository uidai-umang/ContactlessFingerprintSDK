package app.gov.uidai.registration.repository

interface AuthRepository {

    // Get tokens at SDK start, or reuse the saved session for the same operator_ref_id.
    suspend fun ensureSession(operatorRefId: String): Boolean

    suspend fun logout()

    // Used by TokenAuthenticator (OkHttp thread) on 401. Returns a new access token, or null if the session is dead.
    fun refreshBlocking(staleAccessToken: String?): String?
}