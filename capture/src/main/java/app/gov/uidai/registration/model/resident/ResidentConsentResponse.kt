package app.gov.uidai.registration.model.resident


import com.google.gson.annotations.SerializedName

data class ResidentConsentResponse(
    @SerializedName("is_consented")
    val isConsented: Boolean,
    @SerializedName("resident_pseudonym_id")
    val residentPseudonymId: String
)