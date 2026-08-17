package app.gov.uidai.registration.ui.uidentry

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gov.uidai.registration.model.UIDEntryUiState
import app.gov.uidai.registration.pref.PreferenceStore
import app.gov.uidai.registration.pref.model.PreferenceParam
import app.gov.uidai.registration.pref.model.PreferenceType
import app.gov.uidai.registration.usecase.UIDManager
import app.gov.uidai.registration.usecase.UserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UIDEntryViewModel @Inject constructor(
    private val userUseCase: UserUseCase,
    private val uidManager: UIDManager,
    private val preferenceStore: PreferenceStore
) : ViewModel() {

    companion object {
        private val TAG = UIDEntryViewModel::class.simpleName

        private val REMEMBER_ME_PREF = PreferenceParam(
            key = "uid_entry.remember_me",
            displayName = "Remember Me",
            type = PreferenceType.BOOLEAN,
            defaultValue = false
        )

        private val UID_PREF = PreferenceParam(
            key = "uid_entry.uid",
            displayName = "UID",
            type = PreferenceType.STRING,
            defaultValue = ""
        )
    }

    private val _uiState = MutableStateFlow(UIDEntryUiState())
    val uiState = _uiState.asStateFlow()

    private var currentUIDHash: String = ""

    init {
        val initRememberMe = preferenceStore.get(REMEMBER_ME_PREF)
        val initUid = if (initRememberMe) preferenceStore.get(UID_PREF) else ""
        onRememberMeChanged(initRememberMe)
        onUIDChanged(initUid)

        if (initRememberMe) {
            checkRegistration(isCheckingFromOnResume = true)
        }

        // Observe rememberMe and uid
        uiState.map { it.rememberMe to it.isValidUID }
            .distinctUntilChanged()
            .onEach { (rememberMe, isValidUID) ->
                preferenceStore.save(
                    REMEMBER_ME_PREF.copy(currentValue = rememberMe)
                )
                if (rememberMe && isValidUID) {
                    preferenceStore.save(
                        pref = UID_PREF.copy(currentValue = _uiState.value.uid)
                    )
                } else if (!rememberMe) {
                    preferenceStore.save(
                        pref = UID_PREF.copy(currentValue = "")
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onUIDChanged(uid: String) {
        val isValidUid = uidManager.validateUID(uid)

        _uiState.update {
            it.copy(
                uid = uid,
                isValidUID = isValidUid,
                isUserRegistered = null,
                user = null,
                textInputErrorMessage = if (uid.isNotEmpty() && !isValidUid) {
                    "Please enter a valid 12-digit UID"
                } else null
            )
        }

        if (isValidUid) {
            checkRegistration()
        }
    }

    fun onRememberMeChanged(value: Boolean) {
        _uiState.update {
            it.copy(
                rememberMe = value
            )
        }
    }

    fun checkRegistration(isCheckingFromOnResume: Boolean = false) {
        val currentState = _uiState.value

        if (!currentState.isValidUID) {
            if (!isCheckingFromOnResume) {
                _uiState.update {
                    it.copy(message = "Please enter a valid 12-digit UID")
                }
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                textInputErrorMessage = null
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                currentUIDHash = uidManager.hashUID(currentState.uid)
                val user = userUseCase.getUser(currentUIDHash)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        user = user,
                        isUserRegistered = user != null
                    )
                }

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

    fun clearMessage() {
        _uiState.update {
            it.copy(message = null)
        }
    }

    fun getCurrentUidHash(): String = currentUIDHash
}
