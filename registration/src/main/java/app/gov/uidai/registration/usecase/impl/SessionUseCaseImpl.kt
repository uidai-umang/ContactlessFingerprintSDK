package app.gov.uidai.registration.usecase.impl

import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.model.session.CloseSessionRequest
import app.gov.uidai.registration.model.session.CreateSessionRequest
import app.gov.uidai.registration.model.session.CreateSessionResponse
import app.gov.uidai.registration.repository.ClfRepository
import app.gov.uidai.registration.usecase.SessionUseCase
import javax.inject.Inject

class SessionUseCaseImpl @Inject constructor(
    private val clfRepository: ClfRepository
) : SessionUseCase {

    // Builds create session request and delegates to repository
    override suspend fun createSession(
        operatorId: String,
        deviceId: String,
        centreId: String,
        residentPseudonymId: String
    ): ApiResult<CreateSessionResponse> {
        val request = CreateSessionRequest(
            operatorId = operatorId,
            deviceId = deviceId,
            centreId = centreId,
            residentPseudonymId = residentPseudonymId
        )
        return clfRepository.createSession(request)
    }

    // Builds close session request and delegates to repository
    override suspend fun closeSession(
        sessionId: String
    ): ApiResult<Unit> {
        val request = CloseSessionRequest(
            sessionId = sessionId
        )
        return clfRepository.closeSession(request)
    }
}