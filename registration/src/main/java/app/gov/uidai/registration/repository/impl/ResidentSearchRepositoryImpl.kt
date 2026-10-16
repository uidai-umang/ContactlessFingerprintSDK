package app.gov.uidai.registration.repository.impl

import app.gov.uidai.registration.data.remote.api.ResidentApi
import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.data.remote.network.ResponseHandler
import app.gov.uidai.registration.model.resident.ResidentSearchRequest
import app.gov.uidai.registration.model.resident.ResidentSearchResponse
import app.gov.uidai.registration.repository.ResidentSearchRepository
import java.util.UUID
import javax.inject.Inject

class ResidentSearchRepositoryImpl @Inject constructor(
    private val residentApi: ResidentApi
) : ResidentSearchRepository {

    override suspend fun searchResident(eid: String): ApiResult<ResidentSearchResponse> =
        ResponseHandler.safeApiCall {
            residentApi.searchResident(
                requestId = UUID.randomUUID().toString(),
                request = ResidentSearchRequest(eid)
            )
        }
}
