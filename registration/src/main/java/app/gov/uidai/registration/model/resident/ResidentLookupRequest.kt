package app.gov.uidai.registration.model.resident

import com.google.gson.annotations.SerializedName

data class ResidentLookupRequest(
    @SerializedName("aadhaar_hash") val aadhaarHash: String,
    @SerializedName("age_group") val ageGroup: String = "25",
    @SerializedName("gender") val gender: String = "Male",
    @SerializedName("skin_tone") val skinTone: String = "Dusky"
)