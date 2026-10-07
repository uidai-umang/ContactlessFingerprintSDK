package app.gov.uidai.registration.data.remote.api

import app.gov.uidai.registration.model.auth.AuthTokenRequest
import app.gov.uidai.registration.model.auth.AuthTokenResponse
import app.gov.uidai.registration.model.auth.LogoutRequest
import app.gov.uidai.registration.model.auth.LogoutResponse
import app.gov.uidai.registration.model.auth.RefreshTokenRequest
import app.gov.uidai.registration.model.capture.CaptureResponse
import app.gov.uidai.registration.model.dashboard.DashboardAlertsResponse
import app.gov.uidai.registration.model.dashboard.DashboardDiversityResponse
import app.gov.uidai.registration.model.dashboard.DashboardFingersResponse
import app.gov.uidai.registration.model.dashboard.DashboardOverviewResponse
import app.gov.uidai.registration.model.dashboard.LogOverrideRequest
import app.gov.uidai.registration.model.dashboard.QuotaCheckResponse
import app.gov.uidai.registration.model.dashboard.QuotaOverrideResponse
import app.gov.uidai.registration.model.device.DeviceRegistrationRequest
import app.gov.uidai.registration.model.device.DeviceRegistrationResponse
import app.gov.uidai.registration.model.resident.ResidentLookupRequest
import app.gov.uidai.registration.model.resident.ResidentLookupResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Query

interface ClfApiService {

    @POST(Urls.ISSUE_TOKEN)
    suspend fun issueToken(
        @Body request: AuthTokenRequest
    ): Response<AuthTokenResponse>

    @POST(Urls.REFRESH_TOKEN)
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): Response<AuthTokenResponse>

    @POST(Urls.LOGOUT)
    suspend fun logout(
        @Body request: LogoutRequest
    ): Response<LogoutResponse>

    @POST(Urls.RESIDENT_LOOKUP)
    suspend fun lookupResident(
        @Body request: ResidentLookupRequest
    ): Response<ResidentLookupResponse>

    @Multipart
    @POST(Urls.CAPTURE_UPLOAD)
    suspend fun uploadCapture(
        @Part image: MultipartBody.Part,
        @PartMap metadata: Map<String, @JvmSuppressWildcards RequestBody>
    ): Response<CaptureResponse>

    @Multipart
    @POST(Urls.CAPTURE_BATCH_UPLOAD)
    suspend fun uploadBatchCaptures(
        @Part images: List<MultipartBody.Part>,
        @PartMap metadata: Map<String, @JvmSuppressWildcards RequestBody>
    ): Response<List<CaptureResponse>>

    @POST(Urls.DEVICE_REGISTER)
    suspend fun registerDevice(
        @Body request: DeviceRegistrationRequest
    ): Response<DeviceRegistrationResponse>

    @GET(Urls.DASHBOARD_OVERVIEW)
    suspend fun getDashboardOverview(
        @Query("operator_id") operatorId: String
    ): Response<DashboardOverviewResponse>

    @GET(Urls.DASHBOARD_DIVERSITY)
    suspend fun getDashboardDiversity(
        @Query("operator_id") operatorId: String
    ): Response<DashboardDiversityResponse>

    @GET(Urls.DASHBOARD_FINGERS)
    suspend fun getDashboardFingers(
        @Query("operator_id") operatorId: String
    ): Response<DashboardFingersResponse>

    @GET(Urls.DASHBOARD_ALERTS)
    suspend fun getDashboardAlerts(): Response<DashboardAlertsResponse>

    @GET(Urls.QUOTA_CHECK)
    suspend fun checkQuota(
        @Query("gender") gender: String,
        @Query("age_group") ageGroup: String
    ): Response<QuotaCheckResponse>

    @POST(Urls.QUOTA_OVERRIDE)
    suspend fun logOverride(
        @Body request: LogOverrideRequest
    ): Response<QuotaOverrideResponse>
}