package app.gov.uidai.registration.model.device

import com.google.gson.annotations.SerializedName

data class DeviceRegistrationRequest(
    @SerializedName("operator_id") val operatorId: String,
    @SerializedName("android_id") val androidId: String,
    @SerializedName("device_fingerprint") val deviceFingerprint: String,
    @SerializedName("device_model") val deviceModel: String,
    @SerializedName("device_manufacturer") val deviceManufacturer: String,
    @SerializedName("os_version") val osVersion: String,
    @SerializedName("play_integrity_status") val playIntegrityStatus: String = "",
    @SerializedName("android_sdk_version") val androidSdkVersion: Int,
    @SerializedName("android_security_patch") val androidSecurityPatch: String,
    @SerializedName("soc_model") val socModel: String,
    @SerializedName("ram_total_mb") val ramTotalMb: Int,

    @SerializedName("camera_fingerprint_hash") val cameraFingerprintHash: String,
    @SerializedName("camera_id") val cameraId: String,
    @SerializedName("lens_facing") val lensFacing: String,
    @SerializedName("hardware_level") val hardwareLevel: String,
    @SerializedName("sensor_physical_size_mm") val sensorPhysicalSizeMm: String,
    @SerializedName("sensor_active_array_size") val sensorActiveArraySize: String,
    @SerializedName("pixel_array_size") val pixelArraySize: String,
    @SerializedName("focal_length_mm") val focalLengthMm: Float,
    @SerializedName("aperture") val aperture: Float,
    @SerializedName("min_focus_distance_diopters") val minFocusDistanceDiopters: Float,
    @SerializedName("hyperfocal_distance_diopters") val hyperfocalDistanceDiopters: Float,
    @SerializedName("has_flash") val hasFlash: Boolean,
    @SerializedName("has_ois") val hasOis: Boolean,
    @SerializedName("max_digital_zoom") val maxDigitalZoom: Float,
    @SerializedName("sensor_orientation") val sensorOrientation: Int,
    @SerializedName("supports_raw") val supportsRaw: Boolean,
    @SerializedName("af_modes") val afModes: List<Int>,
    @SerializedName("ae_modes") val aeModes: List<Int>,
    @SerializedName("awb_modes") val awbModes: List<Int>
)

data class DeviceRegistrationResponse(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("camera_spec_id") val cameraSpecId: String
)