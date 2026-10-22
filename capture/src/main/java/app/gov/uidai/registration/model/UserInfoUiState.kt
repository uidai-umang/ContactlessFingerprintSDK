package app.gov.uidai.registration.model

// UI State for UserInfo Fragment
data class UserInfoUiState(
    val user: User? = null,
    val isLoadingUserData: Boolean = false,
    val isCapturingImage: Boolean = false,
    val isUploadingImage: Boolean = false,
    val isMatching: Boolean = false,
    val saveImageAfterCapture: Boolean = false,
    val currentFingerprint: Fingerprint? = null,
    val matchingResults: Map<FingerPosition, Float>? = null,
    val errorMessage: String? = null
)