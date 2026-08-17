package app.gov.uidai.registration.utils.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import java.security.MessageDigest

data class CameraSpecData(
    val cameraId: String,
    val lensFacing: String,
    val hardwareLevel: String,
    val sensorPhysicalSizeMm: String,
    val sensorActiveArraySize: String,
    val pixelArraySize: String,
    val focalLengthMm: Float,
    val aperture: Float,
    val minFocusDistanceDiopters: Float,
    val hyperfocalDistanceDiopters: Float,
    val hasFlash: Boolean,
    val hasOis: Boolean,
    val maxDigitalZoom: Float,
    val sensorOrientation: Int,
    val supportsRaw: Boolean,
    val afModes: List<Int>,
    val aeModes: List<Int>,
    val awbModes: List<Int>,
    val fingerprintHash: String
)

object CameraSpecManager {

    // Picks the primary back-facing camera — matches the SDK's own
    // camera selection (CameraUtils.getCameraId with LENS_FACING_BACK),
    // since that's the camera actually used for finger capture.
    fun fetch(context: Context): CameraSpecData? {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: return null

        val characteristics = cameraManager.getCameraCharacteristics(cameraId)

        val hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: -1
        val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        val pixelArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val focalLength = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull() ?: 0f
        val aperture = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
            ?.firstOrNull() ?: 0f
        val minFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val hyperfocalDistance = characteristics.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE) ?: 0f
        val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
        val stabilizationModes = characteristics.get(
            CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION
        ) ?: intArrayOf()
        val hasOis = stabilizationModes.any { it != CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF }
        val maxDigitalZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        val supportsRaw = capabilities.contains(
            CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW
        )
        val afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.toList() ?: emptyList()
        val aeModes = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.toList() ?: emptyList()
        val awbModes = characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toList() ?: emptyList()

        val hardwareLevelName = hardwareLevelToString(hardwareLevel)

        val fingerprintHash = computeCameraFingerprintHash(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            cameraId = cameraId,
            hardwareLevel = hardwareLevel,
            sensorWidthMm = sensorSize?.width ?: 0f,
            sensorHeightMm = sensorSize?.height ?: 0f,
            focalLengthMm = focalLength,
            aperture = aperture
        )

        return CameraSpecData(
            cameraId = cameraId,
            lensFacing = "BACK",
            hardwareLevel = hardwareLevelName,
            sensorPhysicalSizeMm = "${sensorSize?.width ?: 0f}x${sensorSize?.height ?: 0f}",
            sensorActiveArraySize = activeArray?.let { "${it.width()}x${it.height()}" } ?: "",
            pixelArraySize = pixelArray?.let { "${it.width}x${it.height}" } ?: "",
            focalLengthMm = focalLength,
            aperture = aperture,
            minFocusDistanceDiopters = minFocusDistance,
            hyperfocalDistanceDiopters = hyperfocalDistance,
            hasFlash = hasFlash,
            hasOis = hasOis,
            maxDigitalZoom = maxDigitalZoom,
            sensorOrientation = sensorOrientation,
            supportsRaw = supportsRaw,
            afModes = afModes,
            aeModes = aeModes,
            awbModes = awbModes,
            fingerprintHash = fingerprintHash
        )
    }

    private fun hardwareLevelToString(level: Int): String = when (level) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        else -> "UNKNOWN"
    }

    // Design and reasoning for this hash: see FetchCameraSpecToMapSettingsAndStoreInBackend.md
    private fun computeCameraFingerprintHash(
        manufacturer: String,
        model: String,
        cameraId: String,
        hardwareLevel: Int,
        sensorWidthMm: Float,
        sensorHeightMm: Float,
        focalLengthMm: Float,
        aperture: Float
    ): String {
        val normalized = listOf(
            manufacturer.trim().lowercase(),
            model.trim().lowercase(),
            cameraId.trim(),
            hardwareLevel.toString(),
            String.format("%.2f", sensorWidthMm),
            String.format("%.2f", sensorHeightMm),
            String.format("%.2f", focalLengthMm),
            String.format("%.2f", aperture)
        ).joinToString("|")

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))

        return digest.joinToString("") { "%02x".format(it) }
    }
}