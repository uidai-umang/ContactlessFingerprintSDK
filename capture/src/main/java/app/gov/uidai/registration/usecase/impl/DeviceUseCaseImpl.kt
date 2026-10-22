package app.gov.uidai.registration.usecase.impl

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import app.gov.uidai.registration.utils.camera.CameraSpecManager
import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.model.device.DeviceRegistrationRequest
import app.gov.uidai.registration.model.device.DeviceRegistrationResponse
import app.gov.uidai.registration.repository.ClfRepository
import app.gov.uidai.registration.usecase.DeviceUseCase
import javax.inject.Inject

class DeviceUseCaseImpl @Inject constructor(
    private val clfRepository: ClfRepository
) : DeviceUseCase {

    override suspend fun registerDeviceIfNeeded(
        context: Context,
        operatorId: String,
        androidId: String
    ): ApiResult<DeviceRegistrationResponse> {
        val cameraSpec = CameraSpecManager.fetch(context)
            ?: return ApiResult.Error("Unable to read camera characteristics", -1)

        val deviceFingerprint = Build.FINGERPRINT.let {
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(it.toByteArray(Charsets.UTF_8))
                .joinToString("") { b -> "%02x".format(b) }
        }

        Log.d("DeviceReg", "fingerprintHash len=${cameraSpec.fingerprintHash.length}, deviceFingerprint len=${Build.FINGERPRINT.length}")

        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL
        } else {
            "unknown"  // or omit the field entirely, or use Build.HARDWARE / Build.BOARD as an older fallback
        }

        val request = DeviceRegistrationRequest(
            operatorId = operatorId,
            androidId = androidId,
            deviceFingerprint = deviceFingerprint,
            deviceModel = Build.MODEL,
            deviceManufacturer = Build.MANUFACTURER,
            osVersion = Build.VERSION.RELEASE,
            androidSdkVersion = Build.VERSION.SDK_INT,
            androidSecurityPatch = Build.VERSION.SECURITY_PATCH,
            socModel = socModel,
            ramTotalMb = getTotalRamMb(context),
            cameraFingerprintHash = cameraSpec.fingerprintHash,
            cameraId = cameraSpec.cameraId,
            lensFacing = cameraSpec.lensFacing,
            hardwareLevel = cameraSpec.hardwareLevel,
            sensorPhysicalSizeMm = cameraSpec.sensorPhysicalSizeMm,
            sensorActiveArraySize = cameraSpec.sensorActiveArraySize,
            pixelArraySize = cameraSpec.pixelArraySize,
            focalLengthMm = cameraSpec.focalLengthMm,
            aperture = cameraSpec.aperture,
            minFocusDistanceDiopters = cameraSpec.minFocusDistanceDiopters,
            hyperfocalDistanceDiopters = cameraSpec.hyperfocalDistanceDiopters,
            hasFlash = cameraSpec.hasFlash,
            hasOis = cameraSpec.hasOis,
            maxDigitalZoom = cameraSpec.maxDigitalZoom,
            sensorOrientation = cameraSpec.sensorOrientation,
            supportsRaw = cameraSpec.supportsRaw,
            afModes = cameraSpec.afModes,
            aeModes = cameraSpec.aeModes,
            awbModes = cameraSpec.awbModes
        )

        return clfRepository.registerDevice(request)
    }

    private fun getTotalRamMb(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE)
                as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return (memInfo.totalMem / (1024 * 1024)).toInt()
    }
}