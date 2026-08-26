package app.gov.uidai.registration.model.dashboard

import com.google.gson.annotations.SerializedName

data class QuotaOverrideResponse(
    @SerializedName("override_id") val overrideId: String,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("resident_pseudonym_id") val residentPseudonymId: String,
    @SerializedName("operator_id") val operatorId: String,
    @SerializedName("dimension") val dimension: String,
    @SerializedName("key") val key: String,
    @SerializedName("created_at") val createdAt: String
)
