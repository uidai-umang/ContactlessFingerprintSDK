package app.gov.uidai.registration.usecase

import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.model.capture.CaptureRequest
import app.gov.uidai.registration.model.capture.CaptureResponse

interface CaptureQueueManager {

    // Called after every successful finger capture.
    // Checks pending for this resident first — batch if any, single if none.
    suspend fun uploadOrQueue(
        request: CaptureRequest
    ): ApiResult<List<CaptureResponse>>

    // Called by WorkManager every 15 mins.
    // Processes all pending captures grouped by session_id sequentially.
    suspend fun syncPendingCaptures(): ApiResult<Unit>
}