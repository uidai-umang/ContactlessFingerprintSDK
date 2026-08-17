package app.gov.uidai.registration.repository.impl

import app.gov.uidai.registration.data.remote.api.ClfApiService
import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.data.remote.network.MultipartHelper.buildBatchMetadataParts
import app.gov.uidai.registration.data.remote.network.MultipartHelper.buildImagePart
import app.gov.uidai.registration.data.remote.network.MultipartHelper.buildMetadataParts
import app.gov.uidai.registration.data.remote.network.ResponseHandler
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
import app.gov.uidai.registration.repository.ClfRepository
import javax.inject.Inject

class ClfRepositoryImpl @Inject constructor(
    private val apiService: ClfApiService
) : ClfRepository {

    override suspend fun lookupResident(
        request: ResidentLookupRequest
    ): ApiResult<ResidentLookupResponse> = ResponseHandler.safeApiCall {
        apiService.lookupResident(request)
    }

    override suspend fun createSession(
        request: CreateSessionRequest
    ): ApiResult<CreateSessionResponse> = ResponseHandler.safeApiCall {
        apiService.createSession(request)
    }

    override suspend fun closeSession(
        request: CloseSessionRequest
    ): ApiResult<Unit> = ResponseHandler.safeApiCall {
        apiService.closeSession(request)
    }

    override suspend fun uploadCapture(
        request: CaptureRequest
    ): ApiResult<CaptureResponse> = ResponseHandler.safeApiCall {
        apiService.uploadCapture(
            image = buildImagePart(request.imageBytes, request.fingerType),
            metadata = buildMetadataParts(request)
        )
    }

    override suspend fun uploadBatchCaptures(
        requests: List<CaptureRequest>
    ): ApiResult<List<CaptureResponse>> = ResponseHandler.safeApiCall {
        apiService.uploadBatchCaptures(
            images = requests.map { buildImagePart(it.imageBytes, it.fingerType) },
            metadata = buildBatchMetadataParts(requests)
        )
    }

    override suspend fun registerDevice(
        request: DeviceRegistrationRequest
    ): ApiResult<DeviceRegistrationResponse>  = ResponseHandler.safeApiCall {
        apiService.registerDevice(request)
    }
}