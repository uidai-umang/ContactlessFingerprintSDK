package app.gov.uidai.registration.model.dashboard

import com.google.gson.annotations.SerializedName

data class QuotaCheckLine(
    @SerializedName("key") val key: String,
    @SerializedName("status") val status: String,
    @SerializedName("slots_open") val slotsOpen: Int
) {
    val quotaStatus: QuotaStatus get() = QuotaStatus.fromRaw(status)
}
