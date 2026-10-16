package app.gov.uidai.registration.model

import app.gov.uidai.registration.model.resident.ResidentSearchResponse

data class ResidentSearchUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val resident: ResidentSearchResponse? = null
)
