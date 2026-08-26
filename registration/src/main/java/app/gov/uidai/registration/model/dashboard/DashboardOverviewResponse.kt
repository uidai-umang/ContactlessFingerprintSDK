package app.gov.uidai.registration.model.dashboard

import com.google.gson.annotations.SerializedName

data class DashboardOverviewResponse(
    @SerializedName("total_captured") val totalCaptured: Int,
    @SerializedName("captured_today") val capturedToday: Int,
    @SerializedName("by_gender") val byGender: Map<String, Int>,
    @SerializedName("by_age_group") val byAgeGroup: Map<String, Int>
)
