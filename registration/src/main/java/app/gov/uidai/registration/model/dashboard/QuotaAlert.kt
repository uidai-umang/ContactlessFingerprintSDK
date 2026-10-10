package app.gov.uidai.registration.model.dashboard

import com.google.gson.annotations.SerializedName

data class QuotaAlert(
    @SerializedName("dimension") val dimension: String,
    @SerializedName("key") val key: String,
    @SerializedName("status") val status: String,
    @SerializedName("slots_open") val slotsOpen: Int,
    @SerializedName("message") val message: String
) {
    val quotaStatus: QuotaStatus get() = QuotaStatus.fromRaw(status)
}
