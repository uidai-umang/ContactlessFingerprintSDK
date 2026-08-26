package app.gov.uidai.registration.data.remote.api

import app.gov.uidai.registration.model.capture.BatchCaptureRequest
import app.gov.uidai.registration.model.capture.CaptureRequest
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
import app.gov.uidai.registration.model.session.CloseSessionRequest
import app.gov.uidai.registration.model.session.CreateSessionRequest
import app.gov.uidai.registration.model.session.CreateSessionResponse
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

    @POST(Urls.RESIDENT_LOOKUP)
    suspend fun lookupResident(
        @Body request: ResidentLookupRequest
    ): Response<ResidentLookupResponse>

    @POST(Urls.SESSION_CREATE)
    suspend fun createSession(
        @Body request: CreateSessionRequest
    ): Response<CreateSessionResponse>

    @POST(Urls.SESSION_CLOSE)
    suspend fun closeSession(
        @Body request: CloseSessionRequest
    ): Response<Unit>

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