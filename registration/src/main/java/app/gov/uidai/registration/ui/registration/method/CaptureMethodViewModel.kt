package app.gov.uidai.registration.ui.registration.method

import androidx.lifecycle.ViewModel
import app.gov.uidai.registration.model.CaptureMethod
import app.gov.uidai.registration.model.CaptureMethodUiState
import app.gov.uidai.registration.model.CaptureMode
import app.gov.uidai.registration.model.FingerCaptureStatus
import app.gov.uidai.registration.model.FingerPosition
import app.gov.uidai.registration.model.SlapSubOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class CaptureMethodViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureMethodUiState())
    val uiState = _uiState.asStateFlow()

    // Snapshot of fingerUploadStatus from the PREVIOUS call, used only to
    // detect the exact UPLOADING -> resolved transition below. Not exposed
    // in uiState -- purely internal bookkeeping.
    private var previousFingerUploadStatus: Map<FingerPosition, FingerCaptureStatus> = emptyMap()

    private fun SlapSubOption.toFingerPositionOrNull(): FingerPosition? = when (this) {
        SlapSubOption.LEFT_SLAP -> FingerPosition.LEFT_SLAP
        SlapSubOption.RIGHT_SLAP -> FingerPosition.RIGHT_SLAP
        SlapSubOption.THUMBS -> null // no capture path built for Thumbs
    }

    // Called reactively from CaptureMethodRoute whenever the shared
    // RegistrationViewModel's uiState changes. registrationCaptureMode is ""
    // until the resident's first capture sets it -- once set, it's permanent
    // (mirrors the backend's capture_mode lock), so the operator can no
    // longer switch method cards.
    fun updateFromRegistrationState(
        registrationCaptureMode: String,
        fingerUploadStatus: Map<FingerPosition, FingerCaptureStatus>
    ) {
        val isLocked = registrationCaptureMode.isNotEmpty()

        val fingersAlreadyCaptured = fingerUploadStatus
            .filterKeys {
                it != FingerPosition.LEFT_SLAP &&
                        it != FingerPosition.RIGHT_SLAP &&
                        it != FingerPosition.UNKNOWN
            }
            .count { it.value == FingerCaptureStatus.CAPTURED }

        val completedSlapSubOptions = buildSet {
            if (fingerUploadStatus[FingerPosition.LEFT_SLAP] == FingerCaptureStatus.CAPTURED) {
                add(SlapSubOption.LEFT_SLAP)
            }
            if (fingerUploadStatus[FingerPosition.RIGHT_SLAP] == FingerCaptureStatus.CAPTURED) {
                add(SlapSubOption.RIGHT_SLAP)
            }
        }

        val selected = _uiState.value.selectedSlapSubOption
        val selectedPosition = selected?.toFingerPositionOrNull()

        val inFlightStates = setOf(FingerCaptureStatus.UPLOADING, FingerCaptureStatus.CAPTURING)
        val wasInFlight = selectedPosition != null &&
                previousFingerUploadStatus[selectedPosition] in inFlightStates
        val isNowResolved = selectedPosition != null &&
                fingerUploadStatus[selectedPosition] !in inFlightStates
        // True only on the exact call where the selected option's upload
        // just finished (success or failure) -- not on any later
        // recomposition, since previousFingerUploadStatus updates every call.
        val justResolved = wasInFlight && isNowResolved

        val nextSelectedSubOption = when {
            !justResolved -> selected
            selected in completedSlapSubOptions -> {
                // Success -- auto-advance to the next real, uncompleted
                // sub-option. Thumbs is never auto-selected -- no capture
                // path is built for it (see SlapCaptureLauncher).
                listOf(SlapSubOption.LEFT_SLAP, SlapSubOption.RIGHT_SLAP)
                    .firstOrNull { it != selected && it !in completedSlapSubOptions }
            }
            else -> null // Failure -- clear selection, let the operator re-pick.
        }

        previousFingerUploadStatus = fingerUploadStatus

        _uiState.update { current ->
            current.copy(
                isLocked = isLocked,
                fingersAlreadyCaptured = fingersAlreadyCaptured,
                completedSlapSubOptions = completedSlapSubOptions,
                selectedSlapSubOption = nextSelectedSubOption,
                // Once locked, force the card selection to match the
                // resident's actual mode -- the operator can't pick the
                // other one anymore.
                selectedMethod = when {
                    !isLocked -> current.selectedMethod
                    registrationCaptureMode == CaptureMode.SLAP -> CaptureMethod.SLAP
                    registrationCaptureMode == CaptureMode.SEQUENTIAL -> CaptureMethod.SEQUENTIAL
                    else -> current.selectedMethod
                }
            )
        }
    }

    fun selectMethod(method: CaptureMethod) {
        val current = _uiState.value
        if (current.isLocked) return
        if (current.selectedMethod == method) return
        // Switching methods clears any in-progress slap sub-option choice.
        _uiState.update { it.copy(selectedMethod = method, selectedSlapSubOption = null) }
    }

    fun selectSlapSubOption(option: SlapSubOption) {
        val current = _uiState.value
        if (current.selectedMethod != CaptureMethod.SLAP) return
        if (option in current.completedSlapSubOptions) return
        _uiState.update { it.copy(selectedSlapSubOption = option) }
    }

    fun onContinue() {
        // No-op stub — actual navigation is wired by the Route via a callback
        // passed in from the NavHost, no backend calls needed for this screen.
    }
}