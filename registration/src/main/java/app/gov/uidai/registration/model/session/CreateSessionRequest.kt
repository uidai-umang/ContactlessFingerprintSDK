package app.gov.uidai.registration.model.session

import com.google.gson.annotations.SerializedName

data class CreateSessionRequest(
    @SerializedName("operator_id") val operatorId: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("centre_id") val centreId: String,
    @SerializedName("resident_pseudonym_id") val residentPseudonymId: String
)