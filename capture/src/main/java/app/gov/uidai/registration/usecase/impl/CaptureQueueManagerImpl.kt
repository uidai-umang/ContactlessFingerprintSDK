package app.gov.uidai.registration.usecase.impl

import android.util.Log
import app.gov.uidai.registration.data.dao.PendingCaptureDao
import app.gov.uidai.registration.data.entity.PendingCaptureEntity
import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.data.remote.network.ErrorCodeMapper
import app.gov.uidai.registration.data.remote.network.ErrorBehavior
import app.gov.uidai.registration.model.capture.CaptureRequest
import app.gov.uidai.registration.model.capture.CaptureResponse
import app.gov.uidai.registration.usecase.CaptureQueueManager
import app.gov.uidai.registration.usecase.CaptureUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CaptureQueueManagerImpl @Inject constructor(
    private val captureUseCase: CaptureUseCase,
    private val pendingCaptureDao: PendingCaptureDao,
    private val captureFileStorage: CaptureFileStorage
) : CaptureQueueManager {

    companion object {
        private const val TAG = "CaptureQueueManager"
        private const val MAX_RETRY_COUNT = 5
    }

    override suspend fun uploadOrQueue(
        request: CaptureRequest
    ): ApiResult<List<CaptureResponse>> {
        val pendingCaptures = pendingCaptureDao.getByResidentId(
            request.residentPseudonymId
        )

        Log.d("PENDING_DEBUG", "Found ${pendingCaptures.size} pending: ${pendingCaptures.map { it.fingerType }}")

        return if (pendingCaptures.isEmpty()) {
            Log.d(TAG, "No pending captures. Trying single upload.")
            uploadSingle(request)
        } else {
            Log.d(TAG, "${pendingCaptures.size} pending found. Trying batch upload.")
            uploadBatch(
                pendingCaptures = pendingCaptures,
                newRequest = request
            )
        }
    }

    // 409 is treated as soft-success: the finger was already captured previously.
    private suspend fun uploadSingle(
        request: CaptureRequest
    ): ApiResult<List<CaptureResponse>> {
        val result = captureUseCase.uploadCapture(request)

        return when (result) {
            is ApiResult.Success -> {
                Log.d(TAG, "Single upload succeeded: ${request.fingerType}")
                ApiResult.Success(listOf(result.data))
            }

            is ApiResult.Error -> {
                if (ErrorCodeMapper.behaviorFor(result.code) == ErrorBehavior.TREAT_AS_DUPLICATE_SUCCESS) {
                    Log.d(TAG, "Single upload 409 — finger already captured: ${request.fingerType}")
                    return ApiResult.Success(emptyList())
                }
                Log.w(TAG, "Single upload failed. Saving to local queue: ${result.message}")
                saveToPendingQueue(request)
                ApiResult.Error(result.message, result.code, result.errorData)
            }
        }
    }

    // 409 is treated as soft-success: all fingers in the batch were already captured.
    private suspend fun uploadBatch(
        pendingCaptures: List<PendingCaptureEntity>,
        newRequest: CaptureRequest
    ): ApiResult<List<CaptureResponse>> {
        val batchRequests = pendingCaptures.map { it.toCaptureRequest() } + newRequest

        val result = captureUseCase.uploadBatchCaptures(batchRequests)

        return when (result) {
            is ApiResult.Success -> {
                clearUploaded(pendingCaptures)
                Log.d(TAG, "Batch upload succeeded. Cleared ${pendingCaptures.size} pending.")
                ApiResult.Success(result.data)
            }

            is ApiResult.Error -> {
                if (ErrorCodeMapper.behaviorFor(result.code) == ErrorBehavior.TREAT_AS_DUPLICATE_SUCCESS) {
                    clearUploaded(pendingCaptures)
                    Log.d(TAG, "Batch upload 409 — all fingers already captured. Cleared pending queue.")
                    return ApiResult.Success(emptyList())
                }
                Log.w(TAG, "Batch upload failed. Saving new to queue: ${result.message}")
                saveToPendingQueue(newRequest)
                ApiResult.Error(result.message, result.code, result.errorData)
            }
        }
    }

    // Called by WorkManager every 15 mins.
    // Groups all pending by resident and uploads sequentially.
    // Stops on first resident's failure — retries everything next cycle.
    override suspend fun syncPendingCaptures(): ApiResult<Unit> {
        val allPending = pendingCaptureDao.getAll()

        if (allPending.isEmpty()) {
            Log.d(TAG, "Sync: No pending captures found.")
            return ApiResult.Success(Unit)
        }

        Log.d(TAG, "Sync: Found ${allPending.size} pending captures. Grouping by resident.")

        val groupedByResident = allPending.groupBy { it.residentPseudonymId }

        for ((residentId, captures) in groupedByResident) {

            val retryable = captures.filter { it.retryCount < MAX_RETRY_COUNT }
            if (retryable.isEmpty()) {
                Log.w(TAG, "Resident $residentId exceeded max retries. Skipping.")
                continue
            }

            Log.d(TAG, "Sync: Uploading ${retryable.size} captures for resident $residentId")

            val result = captureUseCase.uploadBatchCaptures(
                retryable.map { it.toCaptureRequest() }
            )

            when (result) {
                is ApiResult.Success -> {
                    clearUploaded(retryable)
                    Log.d(TAG, "Sync: Resident $residentId uploaded successfully. Cleared ${retryable.size} pending.")
                }

                is ApiResult.Error -> {
                    pendingCaptureDao.incrementRetryCount(residentId)
                    Log.w(TAG, "Sync: Resident $residentId failed. Retrying next cycle.")
                    return ApiResult.Error(result.message, result.code, result.errorData)
                }
            }
        }

        return ApiResult.Success(Unit)
    }

    // Row first, then file: a crash in between leaves an orphan file (harmless),
    // never a row pointing at a missing file (which would fail every later sync).
    private suspend fun clearUploaded(captures: List<PendingCaptureEntity>) {
        pendingCaptureDao.delete(captures)
        withContext(Dispatchers.IO) {
            captures.forEach { captureFileStorage.delete(it.imageFilePath) }
        }
    }

    private suspend fun saveToPendingQueue(request: CaptureRequest) {
        val fileName = "${request.residentPseudonymId}_${request.fingerType}_${System.currentTimeMillis()}.enc"
        val filePath = withContext(Dispatchers.IO) {
            captureFileStorage.write(request.imageBytes, fileName)
        }

        val entity = PendingCaptureEntity(
            residentPseudonymId = request.residentPseudonymId,
            operatorId = request.operatorId,
            captureMode = request.captureMode,
            fingerType = request.fingerType,
            hand = request.hand,
            imageFilePath = filePath,
            imageChecksum = request.imageChecksum,
            cameraModel = request.cameraModel,
            cameraResolution = request.cameraResolution,
            deviceModel = request.deviceModel,
            encryptedSessionKey = request.encryptedSessionKey,
            iv = request.iv,
            hmac = request.hmac,
            thumbprint = request.thumbprint
        )
        pendingCaptureDao.insert(entity)
    }

    private suspend fun PendingCaptureEntity.toCaptureRequest(): CaptureRequest {
        val imageBytes = withContext(Dispatchers.IO) {
            captureFileStorage.read(imageFilePath)
        }
        return CaptureRequest(
            residentPseudonymId = residentPseudonymId,
            operatorId = operatorId,
            captureMode = captureMode,
            fingerType = fingerType,
            hand = hand,
            imageBytes = imageBytes,
            imageChecksum = imageChecksum,
            cameraModel = cameraModel,
            cameraResolution = cameraResolution,
            deviceModel = deviceModel,
            encryptedSessionKey = encryptedSessionKey,
            iv = iv,
            hmac = hmac,
            thumbprint = thumbprint
        )
    }
}