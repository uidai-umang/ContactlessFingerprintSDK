package app.gov.uidai.registration.model.dashboard

import com.google.gson.annotations.SerializedName

data class QuotaCheckResponse(
    @SerializedName("gender") val gender: QuotaCheckLine,
    @SerializedName("age_group") val ageGroup: QuotaCheckLine,
    @SerializedName("alternatives") val alternatives: List<Alternative>
) {
    val worstStatus: QuotaStatus
        get() = when {
            gender.quotaStatus == QuotaStatus.FULL || ageGroup.quotaStatus == QuotaStatus.FULL -> QuotaStatus.FULL
            gender.quotaStatus == QuotaStatus.WARN || ageGroup.quotaStatus == QuotaStatus.WARN -> QuotaStatus.WARN
            else -> QuotaStatus.OPEN
        }
}
