package app.gov.uidai.registration.model.resident

import com.google.gson.annotations.SerializedName

data class ResidentSearchRequest(
    @SerializedName("eid") val eid: String
)
