package app.gov.uidai.registration.model.capture

import com.google.gson.annotations.SerializedName

data class BatchCaptureRequest(
    @SerializedName("captures") val captures: List<CaptureRequest>
)