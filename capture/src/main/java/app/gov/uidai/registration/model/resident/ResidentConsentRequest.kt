package app.gov.uidai.registration.model.resident

import com.google.gson.annotations.SerializedName

data class ResidentConsentRequest(
    @SerializedName("resident_pseudonym_id") val residentPseudonymId: String,
    @SerializedName("consented") val consented: Boolean,
    @SerializedName("language_shown") val languageShown: String = "en"
)
