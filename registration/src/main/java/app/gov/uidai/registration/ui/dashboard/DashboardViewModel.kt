package app.gov.uidai.registration.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.model.DashboardTab
import app.gov.uidai.registration.model.DashboardUiState
import app.gov.uidai.registration.model.dashboard.QuotaCheckResponse
import app.gov.uidai.registration.usecase.DashboardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardUseCase: DashboardUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState = _uiState.asStateFlow()

    private val _quotaCheckResult = MutableStateFlow<QuotaCheckResponse?>(null)
    val quotaCheckResult = _quotaCheckResult.asStateFlow()

    private val _overrideResult = MutableStateFlow<OverrideResult?>(null)
    val overrideResult = _overrideResult.asStateFlow()

    private var currentSessionId: String = ""
    private var currentResidentPseudonymId: String = ""
    private var currentOperatorId: String = ""

    private var hasLoadedOverview = false
    private var hasLoadedDiversity = false
    private var hasLoadedFingers = false

    init {
        loadAlerts()
    }

    fun setSessionContext(sessionId: String, residentPseudonymId: String, operatorId: String) {
        currentSessionId = sessionId
        currentResidentPseudonymId = residentPseudonymId
        currentOperatorId = operatorId
    }

    fun onTabSelected(tab: DashboardTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        when (tab) {
            DashboardTab.OVERVIEW -> if (!hasLoadedOverview) loadOverview(currentOperatorId)
            DashboardTab.DIVERSITY -> if (!hasLoadedDiversity) loadDiversity(currentOperatorId)
            DashboardTab.FINGERS -> if (!hasLoadedFingers) loadFingers(currentOperatorId)
        }
    }

    fun loadOverview(operatorId: String) {
        hasLoadedOverview = true
        _uiState.update { it.copy(isOverviewLoading = true) }
        viewModelScope.launch {
            when (val result = dashboardUseCase.getOverview(operatorId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isOverviewLoading = false, overview = result.data)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isOverviewLoading = false, error = result.message)
                }
            }
        }
    }

    fun loadDiversity(operatorId: String) {
        hasLoadedDiversity = true
        _uiState.update { it.copy(isDiversityLoading = true) }
        viewModelScope.launch {
            when (val result = dashboardUseCase.getDiversity(operatorId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isDiversityLoading = false, diversity = result.data)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isDiversityLoading = false, error = result.message)
                }
            }
        }
    }

    fun loadFingers(operatorId: String) {
        hasLoadedFingers = true
        _uiState.update { it.copy(isFingersLoading = true) }
        viewModelScope.launch {
            when (val result = dashboardUseCase.getFingerStats(operatorId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isFingersLoading = false, fingers = result.data)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isFingersLoading = false, error = result.message)
                }
            }
        }
    }

    fun loadAlerts() {
        viewModelScope.launch {
            when (val result = dashboardUseCase.getAlerts()) {
                is ApiResult.Success -> _uiState.update { it.copy(alerts = result.data.alerts) }
                is ApiResult.Error -> _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    fun refreshCurrentTab() {
        when (_uiState.value.selectedTab) {
            DashboardTab.OVERVIEW -> loadOverview(currentOperatorId)
            DashboardTab.DIVERSITY -> loadDiversity(currentOperatorId)
            DashboardTab.FINGERS -> loadFingers(currentOperatorId)
        }
        loadAlerts()
    }

    fun checkQuotaBeforeCapture(gender: String, ageGroup: String) {
        viewModelScope.launch {
            when (val result = dashboardUseCase.checkQuota(gender, ageGroup)) {
                is ApiResult.Success -> _quotaCheckResult.update { result.data }
                is ApiResult.Error -> _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    fun clearQuotaCheckResult() {
        _quotaCheckResult.update { null }
    }

    fun confirmOverride(dimension: String, key: String) {
        viewModelScope.launch {
            val result = dashboardUseCase.logOverride(
                sessionId = currentSessionId,
                residentPseudonymId = currentResidentPseudonymId,
                operatorId = currentOperatorId,
                dimension = dimension,
                key = key
            )
            when (result) {
                is ApiResult.Success -> _overrideResult.update { OverrideResult.Success }
                is ApiResult.Error -> _overrideResult.update { OverrideResult.Error(result.message) }
            }
        }
    }

    fun clearOverrideResult() {
        _overrideResult.update { null }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

sealed class OverrideResult {
    object Success : OverrideResult()
    data class Error(val message: String) : OverrideResult()
}
