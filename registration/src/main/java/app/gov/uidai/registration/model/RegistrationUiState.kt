package app.gov.uidai.registration.model

import app.gov.uidai.registration.model.FingerPosition

// UI State for Registration Fragment
data class RegistrationUiState(
    val name: String = "",
    val phoneNumber: String = "",
    val isLoading: Boolean = false,
    val loadingFinger: FingerPosition? = null,
    val fingerprints: Map<FingerPosition, CLFingerprint?> = mapOf(
        FingerPosition.LEFT_THUMB to null,
        FingerPosition.RIGHT_THUMB to null,
        FingerPosition.LEFT_INDEX to null,
        FingerPosition.RIGHT_INDEX to null,
        FingerPosition.LEFT_MIDDLE to null,
        FingerPosition.RIGHT_MIDDLE to null,
        FingerPosition.LEFT_RING to null,
        FingerPosition.RIGHT_RING to null,
        FingerPosition.LEFT_LITTLE to null,
        FingerPosition.RIGHT_LITTLE to null,
    ),
    val fingerUploadStatus: Map<FingerPosition, FingerCaptureStatus> = mapOf(
        FingerPosition.LEFT_THUMB to FingerCaptureStatus.NOT_CAPTURED,
        FingerPosition.RIGHT_THUMB to FingerCaptureStatus.NOT_CAPTURED,
        FingerPosition.LEFT_INDEX to FingerCaptureStatus.NOT_CAPTURED,
        FingerPosition.RIGHT_INDEX to FingerCaptureStatus.NOT_CAPTURED,
        FingerPosition.LEFT_MIDDLE to FingerCaptureStatus.NOT_CAPTURED,
        FingerPosition.RIGHT_MIDDLE to FingerCaptureStatus.NOT_CAPTURED,
        FingerPosition.LEFT_RING to FingerCaptureStatus.NOT_CAPTURED,
        FingerPosition.RIGHT_RING to FingerCaptureStatus.NOT_CAPTURED,
        FingerPosition.LEFT_LITTLE to FingerCaptureStatus.NOT_CAPTURED,
        FingerPosition.RIGHT_LITTLE to FingerCaptureStatus.NOT_CAPTURED,
    ),
    val message: String? = null,
    val residentPseudonymId: String = "",
    val sessionId: String = "",
    val isLookingUpResident: Boolean = false,
    val totalCaptured: Int = 0,
    val isComplete: Boolean = false
) {
    val hasMinimumFingerprints: Boolean
        get() = fingerprints.values.count { it != null } >= 3

    val canSave: Boolean
        get() = name.isNotBlank() && phoneNumber.isNotBlank() && hasMinimumFingerprints

}