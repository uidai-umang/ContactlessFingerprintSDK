package app.gov.uidai.registration.model.resident

import com.google.gson.annotations.SerializedName

data class ResidentLookupResponse(
    @SerializedName("resident_pseudonym_id") val residentPseudonymId: String,
    @SerializedName("captured_fingers") val capturedFingers: List<String>,
    @SerializedName("pending_uploads") val pendingUploads: List<String>,
    @SerializedName("total_captured") val totalCaptured: Int,
    @SerializedName("is_complete") val isComplete: Boolean
)