package app.gov.uidai.registration.model

data class SharedUiState(
    val isLoadingEmbedder: Boolean = false,
    val isLoadingAssets: Boolean = false,
    val message: String? = null,
)
