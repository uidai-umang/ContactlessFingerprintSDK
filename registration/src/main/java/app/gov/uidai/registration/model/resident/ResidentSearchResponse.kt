package app.gov.uidai.registration.model.resident

import com.google.gson.annotations.SerializedName

// Field names are a GUESS until the API is live. Only the 3 fields we need.
data class ResidentSearchResponse(
    @SerializedName("id") val id: String?,
    @SerializedName("dob") val dob: String?,
    @SerializedName("gender") val gender: String?
)
