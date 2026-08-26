package app.gov.uidai.registration.usecase

import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.model.dashboard.DashboardAlertsResponse
import app.gov.uidai.registration.model.dashboard.DashboardDiversityResponse
import app.gov.uidai.registration.model.dashboard.DashboardFingersResponse
import app.gov.uidai.registration.model.dashboard.DashboardOverviewResponse
import app.gov.uidai.registration.model.dashboard.QuotaCheckResponse
import app.gov.uidai.registration.model.dashboard.QuotaOverrideResponse

interface DashboardUseCase {

    suspend fun getOverview(operatorId: String): ApiResult<DashboardOverviewResponse>

    suspend fun getDiversity(operatorId: String): ApiResult<DashboardDiversityResponse>

    suspend fun getFingerStats(operatorId: String): ApiResult<DashboardFingersResponse>

    suspend fun getAlerts(): ApiResult<DashboardAlertsResponse>

    suspend fun checkQuota(gender: String, ageGroup: String): ApiResult<QuotaCheckResponse>

    suspend fun logOverride(
        sessionId: String,
        residentPseudonymId: String,
        operatorId: String,
        dimension: String,
        key: String
    ): ApiResult<QuotaOverrideResponse>
}
