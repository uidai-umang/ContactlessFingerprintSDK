package app.gov.uidai.registration.model.resident

import com.google.gson.annotations.SerializedName

data class ResidentLookupRequest(
    @SerializedName("resident_ref_id") val residentRefId: String,
    @SerializedName("date_of_birth") val dateOfBirth: String,
    @SerializedName("gender") val gender: String
)