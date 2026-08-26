package app.gov.uidai.registration.model.dashboard

import com.google.gson.annotations.SerializedName

data class QuotaLine(
    @SerializedName("key") val key: String,
    @SerializedName("target_count") val targetCount: Int,
    @SerializedName("captured_count") val capturedCount: Int,
    @SerializedName("slots_open") val slotsOpen: Int,
    @SerializedName("status") val status: String,
    @SerializedName("overrides_total") val overridesTotal: Int,
    @SerializedName("overrides_by_operator") val overridesByOperator: Int
) {
    val quotaStatus: QuotaStatus get() = QuotaStatus.fromRaw(status)
}
