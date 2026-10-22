package app.gov.uidai.registration.model.operator

import com.google.gson.annotations.SerializedName

data class RegisterOperatorResponse(
    @SerializedName("operator_id") val operatorId: String,
    @SerializedName("operator_ref_id") val operatorRefId: String,
    @SerializedName("status") val status: String
)
