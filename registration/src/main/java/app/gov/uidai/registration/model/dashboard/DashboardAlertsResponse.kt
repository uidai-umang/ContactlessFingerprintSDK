package app.gov.uidai.registration.model.dashboard

import com.google.gson.annotations.SerializedName

data class DashboardAlertsResponse(
    @SerializedName("alerts") val alerts: List<QuotaAlert>
)
