package app.gov.uidai.registration.usecase

import android.content.Context
import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.model.device.DeviceRegistrationResponse

interface DeviceUseCase {
    // Fetches camera specs from CameraSpecManager, builds the request,
    // and registers this device. Idempotent server-side by android_id —
    // safe to call on every cold start.
    suspend fun registerDeviceIfNeeded(
        context: Context,
        operatorId: String,
        androidId: String
    ): ApiResult<DeviceRegistrationResponse>
}