package app.gov.uidai.registration.model.operator

import com.google.gson.annotations.SerializedName

data class RegisterOperatorRequest(
    @SerializedName("operator_ref_id") val operatorRefId: String
)
