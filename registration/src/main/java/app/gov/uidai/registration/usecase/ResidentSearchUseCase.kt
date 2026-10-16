package app.gov.uidai.registration.usecase

import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.model.resident.ResidentSearchResponse

interface ResidentSearchUseCase {
    suspend fun search(eid: String): ApiResult<ResidentSearchResponse>
}
