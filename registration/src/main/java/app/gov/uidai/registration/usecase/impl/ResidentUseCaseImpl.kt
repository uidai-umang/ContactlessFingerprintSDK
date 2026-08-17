package app.gov.uidai.registration.usecase.impl

import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.model.resident.ResidentLookupRequest
import app.gov.uidai.registration.model.resident.ResidentLookupResponse
import app.gov.uidai.registration.repository.ClfRepository
import app.gov.uidai.registration.usecase.ResidentUseCase
import javax.inject.Inject

class ResidentUseCaseImpl @Inject constructor(
    private val clfRepository: ClfRepository
) : ResidentUseCase {

    // Builds the lookup request and delegates to repository
    override suspend fun lookupResident(
        aadhaarHash: String,
        ageGroup: String,
        gender: String,
        skinTone: String
    ): ApiResult<ResidentLookupResponse> {
        val request = ResidentLookupRequest(
            aadhaarHash = aadhaarHash,
            ageGroup = ageGroup,
            gender = gender,
            skinTone = skinTone
        )
        return clfRepository.lookupResident(request)
    }
}