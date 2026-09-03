package app.gov.uidai.registration.model

data class CaptureMethodUiState(
    val selectedMethod: CaptureMethod? = null,
    val selectedSlapSubOption: SlapSubOption? = null,
    val completedSlapSubOptions: Set<SlapSubOption> = emptySet(),
    val isLocked: Boolean = false,
    val fingersAlreadyCaptured: Int = 0
)
