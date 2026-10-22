package app.gov.uidai.registration.model.dashboard

import com.google.gson.annotations.SerializedName

data class DashboardDiversityResponse(
    @SerializedName("gender") val gender: List<QuotaLine>,
    @SerializedName("age_group") val ageGroup: List<QuotaLine>
)
