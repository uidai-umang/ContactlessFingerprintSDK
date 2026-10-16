package app.gov.uidai.registration.usecase.impl

import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.model.resident.ResidentSearchResponse
import app.gov.uidai.registration.repository.ResidentSearchRepository
import app.gov.uidai.registration.usecase.ResidentSearchUseCase

class ResidentSearchUseCaseImpl(
    private val repository: ResidentSearchRepository
) : ResidentSearchUseCase {

    override suspend fun search(eid: String): ApiResult<ResidentSearchResponse> {
        if (eid.isBlank()) return ApiResult.Error("EID is missing")
        return repository.searchResident(eid.trim())
    }
}
