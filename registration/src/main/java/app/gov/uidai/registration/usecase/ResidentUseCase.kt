package app.gov.uidai.registration.usecase

import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.model.resident.ResidentLookupRequest
import app.gov.uidai.registration.model.resident.ResidentLookupResponse

interface ResidentUseCase {

    suspend fun lookupResident(
        residentRefId: String,
        dateOfBirth: String,
        gender: String
    ): ApiResult<ResidentLookupResponse>
}