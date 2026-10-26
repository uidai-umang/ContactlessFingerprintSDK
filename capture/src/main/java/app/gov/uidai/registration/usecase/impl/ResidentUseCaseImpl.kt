package app.gov.uidai.registration.usecase.impl

import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.model.resident.ResidentConsentRequest
import app.gov.uidai.registration.model.resident.ResidentConsentResponse
import app.gov.uidai.registration.model.resident.ResidentLookupRequest
import app.gov.uidai.registration.model.resident.ResidentLookupResponse
import app.gov.uidai.registration.repository.ClfRepository
import app.gov.uidai.registration.usecase.ResidentUseCase
import javax.inject.Inject

class ResidentUseCaseImpl @Inject constructor(
    private val clfRepository: ClfRepository
) : ResidentUseCase {

    override suspend fun lookupResident(
        residentRefId: String,
        dateOfBirth: String,
        gender: String
    ): ApiResult<ResidentLookupResponse> {
        val request = ResidentLookupRequest(
            residentRefId = residentRefId,
            dateOfBirth = dateOfBirth,
            gender = gender
        )
        return clfRepository.lookupResident(request)
    }

    override suspend fun postResidentConsent(request: ResidentConsentRequest): ApiResult<ResidentConsentResponse> {
        return clfRepository.postResidentConsent(request)
    }
}