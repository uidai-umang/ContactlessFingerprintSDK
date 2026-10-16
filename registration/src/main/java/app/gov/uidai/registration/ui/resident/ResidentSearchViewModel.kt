package app.gov.uidai.registration.ui.resident

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.model.ResidentSearchUiState
import app.gov.uidai.registration.usecase.ResidentSearchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResidentSearchViewModel @Inject constructor(
    private val residentSearchUseCase: ResidentSearchUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResidentSearchUiState())
    val uiState = _uiState.asStateFlow()

    // eid comes from the host app. How it reaches here is a later task.
    fun search(eid: String) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = residentSearchUseCase.search(eid)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoading = false, resident = result.data)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }
}
