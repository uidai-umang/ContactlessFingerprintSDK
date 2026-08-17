package app.gov.uidai.registration.usecase

import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.model.session.CreateSessionResponse

interface SessionUseCase {

    suspend fun createSession(
        operatorId: String,
        deviceId: String,
        centreId: String,
        residentPseudonymId: String
    ): ApiResult<CreateSessionResponse>

    suspend fun closeSession(
        sessionId: String
    ): ApiResult<Unit>
}