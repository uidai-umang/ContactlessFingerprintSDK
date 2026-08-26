package app.gov.uidai.registration.usecase.impl

import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.model.dashboard.DashboardAlertsResponse
import app.gov.uidai.registration.model.dashboard.DashboardDiversityResponse
import app.gov.uidai.registration.model.dashboard.DashboardFingersResponse
import app.gov.uidai.registration.model.dashboard.DashboardOverviewResponse
import app.gov.uidai.registration.model.dashboard.LogOverrideRequest
import app.gov.uidai.registration.model.dashboard.QuotaCheckResponse
import app.gov.uidai.registration.model.dashboard.QuotaOverrideResponse
import app.gov.uidai.registration.repository.DashboardRepository
import app.gov.uidai.registration.usecase.DashboardUseCase
import javax.inject.Inject

class DashboardUseCaseImpl @Inject constructor(
    private val dashboardRepository: DashboardRepository
) : DashboardUseCase {

    override suspend fun getOverview(
        operatorId: String
    ): ApiResult<DashboardOverviewResponse> = dashboardRepository.getOverview(operatorId)

    override suspend fun getDiversity(
        operatorId: String
    ): ApiResult<DashboardDiversityResponse> = dashboardRepository.getDiversity(operatorId)

    override suspend fun getFingerStats(
        operatorId: String
    ): ApiResult<DashboardFingersResponse> = dashboardRepository.getFingerStats(operatorId)

    override suspend fun getAlerts(): ApiResult<DashboardAlertsResponse> = dashboardRepository.getAlerts()

    override suspend fun checkQuota(
        gender: String,
        ageGroup: String
    ): ApiResult<QuotaCheckResponse> = dashboardRepository.checkQuota(gender, ageGroup)

    // Builds the override log request and delegates to repository
    override suspend fun logOverride(
        sessionId: String,
        residentPseudonymId: String,
        operatorId: String,
        dimension: String,
        key: String
    ): ApiResult<QuotaOverrideResponse> {
        val request = LogOverrideRequest(
            sessionId = sessionId,
            residentPseudonymId = residentPseudonymId,
            operatorId = operatorId,
            dimension = dimension,
            key = key
        )
        return dashboardRepository.logOverride(request)
    }
}
