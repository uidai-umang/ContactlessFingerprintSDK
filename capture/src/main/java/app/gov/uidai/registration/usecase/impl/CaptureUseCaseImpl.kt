package app.gov.uidai.registration.usecase.impl

import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.model.capture.BatchCaptureRequest
import app.gov.uidai.registration.model.capture.CaptureRequest
import app.gov.uidai.registration.model.capture.CaptureResponse
import app.gov.uidai.registration.repository.ClfRepository
import app.gov.uidai.registration.usecase.CaptureUseCase
import javax.inject.Inject

class CaptureUseCaseImpl @Inject constructor(
    private val clfRepository: ClfRepository
) : CaptureUseCase {

    // Uploads a single fingerprint capture to the backend
    override suspend fun uploadCapture(
        request: CaptureRequest
    ): ApiResult<CaptureResponse> {
        return clfRepository.uploadCapture(request)
    }

    // Uploads multiple pending captures in one batch request
    override suspend fun uploadBatchCaptures(
        requests: List<CaptureRequest>
    ): ApiResult<List<CaptureResponse>> {
        return clfRepository.uploadBatchCaptures(requests)
    }
}