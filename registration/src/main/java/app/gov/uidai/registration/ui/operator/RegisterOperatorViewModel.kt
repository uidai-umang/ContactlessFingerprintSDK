package app.gov.uidai.registration.ui.operator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.model.RegisterOperatorUiState
import app.gov.uidai.registration.repository.AuthRepository
import app.gov.uidai.registration.repository.OperatorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterOperatorViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val operatorRepository: OperatorRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RegisterOperatorUiState(operatorRefId = operatorRepository.getLastOperatorRefId())
    )
    val uiState = _uiState.asStateFlow()

    // Host app may pass an operator_ref_id; it overrides the remembered one.
    fun prefill(operatorRefId: String?) {
        if (!operatorRefId.isNullOrBlank()) onOperatorRefIdChanged(operatorRefId)
    }

    fun onOperatorRefIdChanged(value: String) {
        _uiState.update { it.copy(operatorRefId = value, errorMessage = null) }
    }

    fun register() {
        val state = _uiState.value
        val refId = state.operatorRefId.trim()
        if (refId.isEmpty() || state.isLoading) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            if (!authRepository.ensureSession(refId)) {
                fail("Could not sign in. Check your connection and try again.")
                return@launch
            }
            when (val result = operatorRepository.registerOperator(refId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoading = false, registeredOperatorId = result.data.operatorId)
                }

                is ApiResult.Error -> fail(result.message)
            }
        }
    }

    fun onNavigated() {
        _uiState.update { it.copy(registeredOperatorId = null) }
    }

    private fun fail(message: String) {
        _uiState.update { it.copy(isLoading = false, errorMessage = message) }
    }
}
