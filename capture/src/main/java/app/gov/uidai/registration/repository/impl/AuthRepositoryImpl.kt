package app.gov.uidai.registration.repository.impl

import app.gov.uidai.registration.auth.TokenStore
import app.gov.uidai.registration.data.remote.api.ClfApiService
import app.gov.uidai.registration.model.auth.AuthTokenRequest
import app.gov.uidai.registration.model.auth.LogoutRequest
import app.gov.uidai.registration.model.auth.RefreshTokenRequest
import app.gov.uidai.registration.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named

class AuthRepositoryImpl @Inject constructor(
    @Named("authApi") private val authApi: ClfApiService,
    private val tokenStore: TokenStore
) : AuthRepository {

    // One lock for login + refresh: refresh tokens rotate, so only one may run at a time.
    private val lock = Any()

    override suspend fun ensureSession(operatorRefId: String): Boolean =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                if (tokenStore.hasSession() && tokenStore.getOperatorRefId() == operatorRefId) true
                else loginBlocking(operatorRefId)
            }
        }

    override suspend fun logout() = withContext(Dispatchers.IO) {
        val refreshToken = tokenStore.getRefreshToken()
        try {
            if (refreshToken != null) authApi.logout(LogoutRequest(refreshToken))
        } catch (_: IOException) {
        } finally {
            tokenStore.clearTokens()
        }
    }

    override fun refreshBlocking(staleAccessToken: String?): String? = synchronized(lock) {
        // Another request already refreshed while this one waited for the lock.
        val current = tokenStore.getAccessToken()
        if (current != null && current != staleAccessToken) return@synchronized current

        val refreshToken = tokenStore.getRefreshToken()
        if (refreshToken != null) {
            try {
                val response = runBlocking { authApi.refreshToken(RefreshTokenRequest(refreshToken)) }
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    // Refresh token rotates: save BOTH.
                    tokenStore.save(body.accessToken, body.refreshToken, body.expiresIn)
                    return@synchronized body.accessToken
                }
                if (response.code() == 401) tokenStore.clearTokens()
            } catch (e: IOException) {
                return@synchronized null   // network issue: keep tokens
            }
        }

        // Refresh token missing/expired/revoked: log in again with the stored ref id.
        val refId = tokenStore.getOperatorRefId()
        if (refId != null && loginBlocking(refId)) tokenStore.getAccessToken() else null
    }

    private fun loginBlocking(operatorRefId: String): Boolean = try {
        val response = runBlocking { authApi.issueToken(AuthTokenRequest(operatorRefId)) }
        val body = response.body()
        if (response.isSuccessful && body != null) {
            tokenStore.save(body.accessToken, body.refreshToken, body.expiresIn)
            tokenStore.saveOperatorRefId(operatorRefId)
            true
        } else false
    } catch (e: IOException) {
        false
    }
}