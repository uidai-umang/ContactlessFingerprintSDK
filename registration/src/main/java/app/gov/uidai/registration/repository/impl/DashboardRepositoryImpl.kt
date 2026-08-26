package app.gov.uidai.registration.repository.impl

import app.gov.uidai.registration.data.remote.api.ClfApiService
import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.data.remote.network.ResponseHandler
import app.gov.uidai.registration.model.dashboard.DashboardAlertsResponse
import app.gov.uidai.registration.model.dashboard.DashboardDiversityResponse
import app.gov.uidai.registration.model.dashboard.DashboardFingersResponse
import app.gov.uidai.registration.model.dashboard.DashboardOverviewResponse
import app.gov.uidai.registration.model.dashboard.LogOverrideRequest
import app.gov.uidai.registration.model.dashboard.QuotaCheckResponse
import app.gov.uidai.registration.model.dashboard.QuotaOverrideResponse
import app.gov.uidai.registration.repository.DashboardRepository
import javax.inject.Inject

class DashboardRepositoryImpl @Inject constructor(
    private val apiService: ClfApiService
) : DashboardRepository {

    override suspend fun getOverview(
        operatorId: String
    ): ApiResult<DashboardOverviewResponse> = ResponseHandler.safeApiCall {
        apiService.getDashboardOverview(operatorId)
    }

    override suspend fun getDiversity(
        operatorId: String
    ): ApiResult<DashboardDiversityResponse> = ResponseHandler.safeApiCall {
        apiService.getDashboardDiversity(operatorId)
    }

    override suspend fun getFingerStats(
        operatorId: String
    ): ApiResult<DashboardFingersResponse> = ResponseHandler.safeApiCall {
        apiService.getDashboardFingers(operatorId)
    }

    override suspend fun getAlerts(): ApiResult<DashboardAlertsResponse> = ResponseHandler.safeApiCall {
        apiService.getDashboardAlerts()
    }

    override suspend fun checkQuota(
        gender: String,
        ageGroup: String
    ): ApiResult<QuotaCheckResponse> = ResponseHandler.safeApiCall {
        apiService.checkQuota(gender, ageGroup)
    }

    override suspend fun logOverride(
        request: LogOverrideRequest
    ): ApiResult<QuotaOverrideResponse> = ResponseHandler.safeApiCall {
        apiService.logOverride(request)
    }
}
