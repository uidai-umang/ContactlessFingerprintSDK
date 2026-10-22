package app.gov.uidai.registration.model

import app.gov.uidai.registration.model.dashboard.DashboardDiversityResponse
import app.gov.uidai.registration.model.dashboard.DashboardFingersResponse
import app.gov.uidai.registration.model.dashboard.DashboardOverviewResponse
import app.gov.uidai.registration.model.dashboard.QuotaAlert

data class DashboardUiState(
    val selectedTab: DashboardTab = DashboardTab.OVERVIEW,
    val isOverviewLoading: Boolean = false,
    val isDiversityLoading: Boolean = false,
    val isFingersLoading: Boolean = false,
    val overview: DashboardOverviewResponse? = null,
    val diversity: DashboardDiversityResponse? = null,
    val fingers: DashboardFingersResponse? = null,
    val alerts: List<QuotaAlert> = emptyList(),
    val error: String? = null
)
