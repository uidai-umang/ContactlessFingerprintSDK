package app.gov.uidai.registration.repository

import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.model.capture.BatchCaptureRequest
import app.gov.uidai.registration.model.capture.CaptureRequest
import app.gov.uidai.registration.model.capture.CaptureResponse
import app.gov.uidai.registration.model.device.DeviceRegistrationRequest
import app.gov.uidai.registration.model.device.DeviceRegistrationResponse
import app.gov.uidai.registration.model.resident.ResidentLookupRequest
import app.gov.uidai.registration.model.resident.ResidentLookupResponse
import app.gov.uidai.registration.model.session.CloseSessionRequest
import app.gov.uidai.registration.model.session.CreateSessionRequest
import app.gov.uidai.registration.model.session.CreateSessionResponse

interface ClfRepository {

    suspend fun lookupResident(
        request: ResidentLookupRequest
    ): ApiResult<ResidentLookupResponse>

    suspend fun createSession(
        request: CreateSessionRequest
    ): ApiResult<CreateSessionResponse>

    suspend fun closeSession(
        request: CloseSessionRequest
    ): ApiResult<Unit>

    suspend fun uploadCapture(
        request: CaptureRequest
    ): ApiResult<CaptureResponse>

    suspend fun uploadBatchCaptures(
        requests: List<CaptureRequest>
    ): ApiResult<List<CaptureResponse>>

    suspend fun registerDevice(
        request: DeviceRegistrationRequest
    ): ApiResult<DeviceRegistrationResponse>
}