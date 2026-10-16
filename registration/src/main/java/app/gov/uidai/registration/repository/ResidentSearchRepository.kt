package app.gov.uidai.registration.repository

import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.model.resident.ResidentSearchResponse

interface ResidentSearchRepository {
    suspend fun searchResident(eid: String): ApiResult<ResidentSearchResponse>
}
