package app.gov.uidai.registration.model.session

import com.google.gson.annotations.SerializedName

data class CloseSessionRequest(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("close_reason") val closeReason: String = "completed"
)