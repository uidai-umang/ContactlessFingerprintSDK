package app.gov.uidai.registration.data.remote.api

import app.gov.uidai.registration.model.capture.BatchCaptureRequest
import app.gov.uidai.registration.model.capture.CaptureRequest
import app.gov.uidai.registration.model.capture.CaptureResponse
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
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap

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
}