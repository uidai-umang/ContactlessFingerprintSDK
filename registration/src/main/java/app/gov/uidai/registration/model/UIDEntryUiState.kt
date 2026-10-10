package app.gov.uidai.registration.model

import app.gov.uidai.registration.model.resident.Gender
import java.time.LocalDate

data class UIDEntryUiState(
    val refId: String = "",
    val isValidRefId: Boolean = false,
    val dob: LocalDate? = null,
    val gender: Gender? = null,
    val isLoading: Boolean = false,
    val user: User? = null,
    val isUserRegistered: Boolean? = null,
    val message: String? = null
) {
    val canRegister: Boolean
        get() = isValidRefId && dob != null && gender != null && !isLoading
}