package app.gov.uidai.registration.model.session

import com.google.gson.annotations.SerializedName

data class CreateSessionResponse(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("operator_id") val operatorId: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("centre_id") val centreId: String,
    @SerializedName("resident_pseudonym_id") val residentPseudonymId: String,
    @SerializedName("status") val status: String,
    @SerializedName("started_at") val startedAt: String
)