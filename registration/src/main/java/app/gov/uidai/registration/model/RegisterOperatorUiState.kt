package app.gov.uidai.registration.model

data class RegisterOperatorUiState(
    val operatorRefId: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val registeredOperatorId: String? = null
) {
    val canSubmit: Boolean
        get() = operatorRefId.isNotBlank() && !isLoading
}
