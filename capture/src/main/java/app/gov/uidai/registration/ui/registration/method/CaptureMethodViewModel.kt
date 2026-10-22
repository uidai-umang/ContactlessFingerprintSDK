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
import app.gov.uidai.registration.model.toFingerPositionOrNull

@HiltViewModel
class CaptureMethodViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureMethodUiState())
    val uiState = _uiState.asStateFlow()

    // Snapshot of fingerUploadStatus from the PREVIOUS call, used only to
    // detect the exact UPLOADING -> resolved transition below. Not exposed
    // in uiState -- purely internal bookkeeping.
    private var previousFingerUploadStatus: Map<FingerPosition, FingerCaptureStatus> = emptyMap()


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
                        it != FingerPosition.LEFT_THUMB &&
                        it != FingerPosition.RIGHT_THUMB &&
                        it != FingerPosition.UNKNOWN
            }
            .count { it.value == FingerCaptureStatus.CAPTURED }

        val slapSubOptionStatus = SlapSubOption.entries.associateWith { option ->
            option.toFingerPositionOrNull()?.let { fingerUploadStatus[it] }
                ?: FingerCaptureStatus.NOT_CAPTURED
        }

        val completedSlapSubOptions = slapSubOptionStatus
            .filterValues { it == FingerCaptureStatus.CAPTURED }
            .keys

        val selected = _uiState.value.selectedSlapSubOption
        val selectedPosition = selected?.toFingerPositionOrNull()

        val inFlightStates = setOf(FingerCaptureStatus.UPLOADING, FingerCaptureStatus.CAPTURING)
        val previousStatus = selectedPosition?.let { previousFingerUploadStatus[it] }
        val currentStatus = selectedPosition?.let { fingerUploadStatus[it] }
        val wasInFlight = previousStatus in inFlightStates
        val isNowInFlight = currentStatus in inFlightStates
        val justResolved = wasInFlight && !isNowInFlight

        val nextSelectedSubOption = when {
            !justResolved -> selected
            selected in completedSlapSubOptions ->
                SlapSubOption.entries.firstOrNull { it != selected && it !in completedSlapSubOptions }
            else -> null
        }

        previousFingerUploadStatus = fingerUploadStatus

        _uiState.update { current ->
            current.copy(
                isLocked = isLocked,
                fingersAlreadyCaptured = fingersAlreadyCaptured,
                completedSlapSubOptions = completedSlapSubOptions,
                slapSubOptionStatus = slapSubOptionStatus,
                selectedSlapSubOption = nextSelectedSubOption,
                uploadStage = if (isNowInFlight) currentStatus else null,
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