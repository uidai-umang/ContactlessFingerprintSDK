package app.gov.uidai.registration.data.remote.api

import app.gov.uidai.registration.model.resident.ResidentSearchRequest
import app.gov.uidai.registration.model.resident.ResidentSearchResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ResidentApi {
    @POST(Urls.RESIDENT_SEARCH)
    suspend fun searchResident(
        @Header("X-Request-ID") requestId: String,
        @Body request: ResidentSearchRequest
    ): Response<ResidentSearchResponse>
}
