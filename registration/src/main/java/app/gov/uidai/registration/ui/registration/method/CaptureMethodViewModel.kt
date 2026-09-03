package app.gov.uidai.registration.ui.registration.method

import androidx.lifecycle.ViewModel
import app.gov.uidai.registration.model.CaptureMethod
import app.gov.uidai.registration.model.CaptureMethodUiState
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

    init {
        // TODO: there's no existing session-scoped signal yet for "sequential
        // capture already started" or "which slap sub-options are already
        // captured" (RegistrationUiState/FingerCaptureStatus are scoped to the
        // finger-list screen's own ViewModel instance, not shared here). Wire
        // isLocked/fingersAlreadyCaptured/completedSlapSubOptions up once that
        // signal exists — stubbed unlocked/empty for now since this task is
        // UI/flow only.
        _uiState.update {
            it.copy(isLocked = false, fingersAlreadyCaptured = 0, completedSlapSubOptions = emptySet())
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
        if (current.isLocked) return
        if (current.selectedMethod != CaptureMethod.SLAP) return
        if (option in current.completedSlapSubOptions) return
        _uiState.update { it.copy(selectedSlapSubOption = option) }
    }

    fun onContinue() {
        // No-op stub — actual navigation is wired by the Route via a callback
        // passed in from the NavHost, no backend calls needed for this screen.
    }
}
