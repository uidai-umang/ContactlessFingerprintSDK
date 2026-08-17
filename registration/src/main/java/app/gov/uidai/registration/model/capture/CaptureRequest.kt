package app.gov.uidai.registration.model.capture

import com.google.gson.annotations.SerializedName

data class CaptureRequest(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("resident_pseudonym_id") val residentPseudonymId: String,
    @SerializedName("operator_id") val operatorId: String,
    @SerializedName("finger_type") val fingerType: String,
    @SerializedName("hand") val hand: String,
    @SerializedName("nfiq2_score") val nfiq2Score: Double = 0.0,
    @SerializedName("blur_score") val blurScore: Double = 0.0,
    @SerializedName("brightness_score") val brightnessScore: Double = 0.0,
    @SerializedName("glare_score") val glareScore: Double = 0.0,
    @SerializedName("attempt_count") val attemptCount: Int = 1,
    @SerializedName("degraded_flag") val degradedFlag: Boolean = false,
    @SerializedName("imageBytes") val imageBytes: ByteArray,
    @SerializedName("image_checksum") val imageChecksum: String = "",
    @SerializedName("camera_model") val cameraModel: String = "",
    @SerializedName("camera_resolution") val cameraResolution: String = "",
    @SerializedName("device_model") val deviceModel: String = "",
    @SerializedName("encrypted_session_key") val encryptedSessionKey: String,
    @SerializedName("iv") val iv: String,
    @SerializedName("hmac") val hmac: String,
    @SerializedName("thumbprint") val thumbprint: String
)