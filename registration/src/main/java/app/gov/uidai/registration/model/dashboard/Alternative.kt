package app.gov.uidai.registration.model.dashboard

import com.google.gson.annotations.SerializedName

data class Alternative(
    @SerializedName("dimension") val dimension: String,
    @SerializedName("key") val key: String,
    @SerializedName("label") val label: String,
    @SerializedName("slots_open") val slotsOpen: Int
)
