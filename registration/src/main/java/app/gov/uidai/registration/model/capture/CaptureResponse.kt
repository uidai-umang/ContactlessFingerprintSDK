package app.gov.uidai.registration.model.capture

import com.google.gson.annotations.SerializedName

data class CaptureResponse(
    @SerializedName("capture_id") val captureId: String,
    @SerializedName("finger_type") val fingerType: String,
    @SerializedName("upload_status") val uploadStatus: String,
    @SerializedName("total_captured") val totalCaptured: Int,
    @SerializedName("is_complete") val isComplete: Boolean
)