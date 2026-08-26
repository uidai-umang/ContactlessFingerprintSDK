package app.gov.uidai.registration.model.dashboard

import com.google.gson.annotations.SerializedName

data class LogOverrideRequest(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("resident_pseudonym_id") val residentPseudonymId: String,
    @SerializedName("operator_id") val operatorId: String,
    @SerializedName("dimension") val dimension: String,
    @SerializedName("key") val key: String
)
