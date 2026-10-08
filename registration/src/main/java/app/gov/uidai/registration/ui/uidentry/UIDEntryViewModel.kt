package app.gov.uidai.registration.ui.uidentry

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gov.uidai.registration.model.UIDEntryUiState
import app.gov.uidai.registration.model.resident.Gender
import app.gov.uidai.registration.model.resident.ResidentInput
import app.gov.uidai.registration.pref.PreferenceStore
import app.gov.uidai.registration.pref.model.PreferenceParam
import app.gov.uidai.registration.pref.model.PreferenceType
import app.gov.uidai.registration.usecase.UIDManager
import app.gov.uidai.registration.usecase.UserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class UIDEntryViewModel @Inject constructor(
    private val userUseCase: UserUseCase,
    private val uidManager: UIDManager,
    private val preferenceStore: PreferenceStore
) : ViewModel() {

    companion object {
        private val TAG = UIDEntryViewModel::class.simpleName

        // Older builds saved the raw 12-digit UID under this key.
        private val LEGACY_UID_PREF = PreferenceParam(
            key = "uid_entry.uid",
            displayName = "UID",
            type = PreferenceType.STRING,
            defaultValue = ""
        )
    }

    private val _uiState = MutableStateFlow(UIDEntryUiState())
    val uiState = _uiState.asStateFlow()

    private var checkJob: Job? = null

    init {
        preferenceStore.save(LEGACY_UID_PREF.copy(currentValue = ""))
    }

    fun onRefIdChanged(raw: String) {
        val refId = uidManager.sanitizeRefId(raw)
        _uiState.update {
            it.copy(
                refId = refId,
                isValidRefId = uidManager.validateRefId(refId),
                isUserRegistered = null,
                user = null,
                isLoading = false
            )
        }
        checkRegistration()
    }

    fun onDobSelected(dob: LocalDate) {
        _uiState.update { it.copy(dob = dob) }
    }

    fun onGenderSelected(gender: Gender) {
        _uiState.update { it.copy(gender = gender) }
    }

    fun checkRegistration() {
        val state = _uiState.value
        checkJob?.cancel()
        if (!state.isValidRefId) return

        _uiState.update { it.copy(isLoading = true) }

        checkJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val user = userUseCase.getUser(state.refId)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        user = user,
                        isUserRegistered = user != null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error while checking registration", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "An error occurred. Please try again. ($e)"
                    )
                }
            }
        }
    }

    fun residentInput(): ResidentInput? {
        val state = _uiState.value
        val dob = state.dob ?: return null
        val gender = state.gender ?: return null
        if (!state.isValidRefId) return null
        return ResidentInput(refId = state.refId, dob = dob.toString(), gender = gender.name)
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}