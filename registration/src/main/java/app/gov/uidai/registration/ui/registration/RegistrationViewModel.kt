package app.gov.uidai.registration.ui.registration

import android.content.Intent
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gov.uidai.registration.data.dao.PendingCaptureDao
import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.model.CLFingerprint
import app.gov.uidai.registration.model.CaptureMode
import app.gov.uidai.registration.model.FingerCaptureStatus
import app.gov.uidai.registration.model.FingerPosition
import app.gov.uidai.registration.model.FingerType
import app.gov.uidai.registration.model.RegistrationUiState
import app.gov.uidai.registration.model.SDKResult
import app.gov.uidai.registration.model.User
import app.gov.uidai.registration.model.capture.CaptureRequest
import app.gov.uidai.registration.repository.FileRepository
import app.gov.uidai.registration.usecase.CaptureQueueManager
import app.gov.uidai.registration.usecase.FingerSDKManager
import app.gov.uidai.registration.usecase.ResidentUseCase
import app.gov.uidai.registration.usecase.SessionUseCase
import app.gov.uidai.registration.usecase.UserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import app.gov.uidai.registration.encryption.EncryptionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// Hoisted to Activity scope (see RegistrationActivity: `by viewModels()`,
// passed into both CaptureMethodRoute and RegistrationRoute) instead of
// being scoped to the Registration nav destination alone. Both the
// sequential finger-list screen and the slap capture method screen need
// the same resident/session/capture_mode state — one lookup, one source
// of truth, no drift between the two flows' completeness/locking logic.
@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val userUseCase: UserUseCase,
    private val fileRepository: FileRepository,
    private val residentUseCase: ResidentUseCase,
    private val sessionUseCase: SessionUseCase,
    private val captureQueueManager: CaptureQueueManager,
    private val pendingCaptureDao: PendingCaptureDao,
    private val sdkManager: FingerSDKManager,
    private val encryptionService: EncryptionService
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistrationUiState())
    val uiState = _uiState.asStateFlow()

    private val _registrationResult = MutableStateFlow<RegistrationResult?>(null)
    val registrationResult = _registrationResult.asStateFlow()

    private var currentUidHash: String = ""
    private var currentResidentId: String = ""
    private var currentSessionId: String = ""
    private val testOperatorId = "00000000-0000-0000-0000-000000000001"
    private val testDeviceId = "00000000-0000-0000-0000-000000000002"
    private val testCentreId = "00000000-0000-0000-0000-000000000003"

    init {
        sdkManager.setResultListener { result -> onSDKResult(result) }
    }

    fun setUidHash(uidHash: String) {
        // Same uidHash may be set again when navigating back into this
        // shared instance from a sibling destination (e.g. CaptureMethod)
        // -- avoid re-triggering a fresh lookup/session for no reason.
        if (uidHash == currentUidHash && currentResidentId.isNotEmpty()) return
        currentUidHash = uidHash
        lookupResidentAndCreateSession()
    }

    private fun lookupResidentAndCreateSession() {
        _uiState.update { it.copy(isLookingUpResident = true) }

        viewModelScope.launch {
            val result = residentUseCase.lookupResident(
                aadhaarHash = currentUidHash,
                ageGroup = "25",
                gender = "Male",
                skinTone = "Dusky"
            )

            when (result) {
                is ApiResult.Success -> {
                    val response = result.data
                    currentResidentId = response.residentPseudonymId

                    // Backend truth: what's confirmed captured/pending on the server
                    val capturedFingers = response.capturedFingers.mapNotNull { name ->
                        FingerPosition.entries.find { it.name == name }
                    }
                    val backendPendingFingers = response.pendingUploads.mapNotNull { name ->
                        FingerPosition.entries.find { it.name == name }
                    }

                    // Local truth: captures sitting in Room, never reached backend yet
                    // (e.g. app was closed mid-session before upload succeeded)
                    val localPendingEntries =
                        pendingCaptureDao.getByResidentId(response.residentPseudonymId)
                    val localPendingFingers = localPendingEntries.mapNotNull { entity ->
                        FingerPosition.entries.find { it.name == entity.fingerType }
                    }

                    // Merge: local pending takes priority over "not captured" --
                    // operator already captured it, just hasn't synced yet
                    val uploadStatusMap = FingerPosition.entries
                        .filter { it != FingerPosition.UNKNOWN }
                        .associateWith { position ->
                            when {
                                position in capturedFingers -> FingerCaptureStatus.CAPTURED
                                position in backendPendingFingers -> FingerCaptureStatus.PENDING
                                position in localPendingFingers -> FingerCaptureStatus.PENDING
                                else -> FingerCaptureStatus.NOT_CAPTURED
                            }
                        }

                    _uiState.update {
                        it.copy(
                            isLookingUpResident = false,
                            residentPseudonymId = response.residentPseudonymId,
                            captureMode = response.captureMode,
                            totalCaptured = response.totalCaptured,
                            isComplete = response.isComplete,
                            fingerUploadStatus = uploadStatusMap
                        )
                    }

                    createSession(response.residentPseudonymId)
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLookingUpResident = false,
                            message = "Failed to load resident: ${result.message}"
                        )
                    }
                }
            }
        }
    }

    private suspend fun createSession(residentPseudonymId: String) {
        val result = sessionUseCase.createSession(
            operatorId = testOperatorId,
            deviceId = testDeviceId,
            centreId = testCentreId,
            residentPseudonymId = residentPseudonymId
        )

        when (result) {
            is ApiResult.Success -> {
                currentSessionId = result.data.sessionId
            }

            is ApiResult.Error -> {
                _uiState.update {
                    it.copy(message = "Session error: ${result.message}")
                }
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update {
            it.copy(
                name = name
            )
        }
    }

    fun onPhoneNumberChanged(phoneNumber: String) {
        _uiState.update {
            it.copy(
                phoneNumber = phoneNumber
            )
        }
    }

    fun startFingerprintCapture(fingerPosition: FingerPosition, launchSdk: () -> Unit) {
        val currentState = _uiState.value

        if (currentState.loadingFinger != null) {
            _uiState.update {
                it.copy(
                    message = "Already processing ${
                        currentState.loadingFinger.name.replace(
                            '_',
                            ' '
                        )
                    }"
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    loadingFinger = fingerPosition,
                    fingerUploadStatus = it.fingerUploadStatus.toMutableMap().apply {
                        set(fingerPosition, FingerCaptureStatus.CAPTURING)
                    }
                )
            }
            launchSdk()
        }
    }

    fun onSDKResult(result: SDKResult<CLFingerprint>) {
        val currentState = _uiState.value
        when (result) {
            is SDKResult.Success -> {
                val data = result.data.copy(fingerPosition = currentState.loadingFinger!!)
                val updatedFingerprints = currentState.fingerprints.toMutableMap().apply {
                    set(currentState.loadingFinger, data)
                }
                _uiState.update {
                    currentState.copy(
                        fingerprints = updatedFingerprints,
                        loadingFinger = null,
                        message = null,
                        fingerUploadStatus = currentState.fingerUploadStatus.toMutableMap().apply {
                            set(data.fingerPosition, FingerCaptureStatus.UPLOADING)
                        }
                    )
                }
                viewModelScope.launch {
                    uploadCapture(
                        fingerPosition = data.fingerPosition,
                        captureMode = CaptureMode.SEQUENTIAL,
                        imageBytes = data.imageBytes,
                        blurScore = data.blurScore,
                        brightnessScore = data.brightnessScore,
                        glareScore = data.glareScore
                    )
                }
            }

            is SDKResult.Error -> {
                _uiState.update {
                    currentState.copy(
                        loadingFinger = null,
                        message = result.message,
                        fingerUploadStatus = currentState.loadingFinger?.let { position ->
                            currentState.fingerUploadStatus.toMutableMap().apply {
                                set(position, FingerCaptureStatus.NOT_CAPTURED)
                            }
                        } ?: currentState.fingerUploadStatus
                    )
                }
            }
        }
    }

    // Entry point for a completed slap capture (LEFT_SLAP / RIGHT_SLAP).
    // Called from RegistrationActivity's slap ActivityResultLauncher callback
    // once it's decoded the returned image + scores -- this ViewModel stays
    // Context-free, same as the rest of the class.
    fun uploadSlapResult(
        handType: String,
        imageBytes: ByteArray,
        blurScore: Double = 0.0,
        brightnessScore: Double = 0.0,
        glareScore: Double = 0.0
    ) {
        val fingerPosition = when (handType) {
            "Left" -> FingerPosition.LEFT_SLAP
            "Right" -> FingerPosition.RIGHT_SLAP
            else -> {
                _uiState.update { it.copy(message = "Unknown slap hand type: $handType") }
                return
            }
        }

        _uiState.update {
            it.copy(
                fingerUploadStatus = it.fingerUploadStatus.toMutableMap().apply {
                    set(fingerPosition, FingerCaptureStatus.UPLOADING)
                }
            )
        }

        viewModelScope.launch {
            uploadCapture(
                fingerPosition = fingerPosition,
                captureMode = CaptureMode.SLAP,
                imageBytes = imageBytes,
                blurScore = blurScore,
                brightnessScore = brightnessScore,
                glareScore = glareScore
            )
        }
    }

    private suspend fun uploadCapture(
        fingerPosition: FingerPosition,
        captureMode: String,
        imageBytes: ByteArray,
        blurScore: Double = 0.0,
        brightnessScore: Double = 0.0,
        glareScore: Double = 0.0
    ) {
        val hand = if (fingerPosition.name.startsWith("LEFT")) "LEFT" else "RIGHT"

        val encrypted = encryptionService.encryptImage(imageBytes)

        val request = CaptureRequest(
            sessionId = currentSessionId,
            residentPseudonymId = currentResidentId,
            operatorId = testOperatorId,
            captureMode = captureMode,
            fingerType = fingerPosition.name,
            hand = hand,
            imageBytes = encrypted.encryptedImageBytes,
            deviceModel = Build.MODEL,
            blurScore = blurScore,
            brightnessScore = brightnessScore,
            glareScore = glareScore,
            encryptedSessionKey = encrypted.encryptedSessionKey,
            iv = encrypted.iv,
            hmac = encrypted.hmac,
            thumbprint = encrypted.thumbprint
        )

        val result = captureQueueManager.uploadOrQueue(request)

        when (result) {
            is ApiResult.Success -> {
                val lastResponse = result.data.lastOrNull()

                // Mark EVERY finger in the batch response as CAPTURED, not just the
                // one that triggered this call -- CaptureQueueManager may have bundled
                // previously-pending fingers into the same batch upload
                val updatedStatusMap = _uiState.value.fingerUploadStatus.toMutableMap()
                result.data.forEach { captureResponse ->
                    val respondedPosition = FingerPosition.entries.find {
                        it.name == captureResponse.fingerType
                    }
                    respondedPosition?.let {
                        updatedStatusMap[it] = FingerCaptureStatus.CAPTURED
                    }
                }

                _uiState.update { state ->
                    state.copy(
                        captureMode = captureMode,
                        totalCaptured = lastResponse?.totalCaptured ?: state.totalCaptured,
                        isComplete = lastResponse?.isComplete ?: state.isComplete,
                        fingerUploadStatus = updatedStatusMap
                    )
                }
            }

            is ApiResult.Error -> {
                _uiState.update { state ->
                    state.copy(
                        message = "Upload queued — will retry automatically",
                        fingerUploadStatus = state.fingerUploadStatus.toMutableMap().apply {
                            set(fingerPosition, FingerCaptureStatus.PENDING)
                        }
                    )
                }
            }
        }
    }

    fun removeFingerprint(fingerPosition: FingerPosition) {
        val currentState = _uiState.value
        val updatedFingerprints = currentState.fingerprints.toMutableMap().apply {
            set(fingerPosition, null)
        }
        val updatedStatus = currentState.fingerUploadStatus.toMutableMap().apply {
            set(fingerPosition, FingerCaptureStatus.NOT_CAPTURED)
        }

        _uiState.update {
            currentState.copy(
                fingerprints = updatedFingerprints,
                fingerUploadStatus = updatedStatus
            )
        }
    }

    fun registerUser() {
        val currentState = _uiState.value

        if (!currentState.canSave) {
            _registrationResult.update {
                RegistrationResult.Error("Cannot save - Required fields are missing")
            }
            return
        }

        if (currentState.loadingFinger != null) {
            _uiState.update {
                it.copy(
                    message = "Please wait! ${
                        currentState.loadingFinger.name.replace(
                            '_',
                            ' '
                        )
                    } is processing"
                )
            }
            return
        }

        _uiState.update {
            it.copy(isLoading = true)
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fingerprints = currentState.fingerprints.values.filterNotNull()
                // Save user data
                userUseCase.register(
                    uidHash = currentUidHash,
                    user = User(
                        name = currentState.name,
                        phoneNumber = currentState.phoneNumber
                    ),
                    fingerprints = fingerprints
                )

                saveRegisteredImagesToGallery(fingerprints = fingerprints)

                if (currentSessionId.isNotEmpty()) {
                    sessionUseCase.closeSession(currentSessionId)
                }
                _registrationResult.update { RegistrationResult.Success }

            } catch (ex: Exception) {
                _uiState.update {
                    currentState.copy(
                        isLoading = false,
                        message = "Registration failed: ${ex.message}"
                    )
                }
                _registrationResult.update {
                    RegistrationResult.Error("Registration failed: ${ex.message}")
                }
            }
        }
    }

    suspend fun saveRegisteredImagesToGallery(fingerprints: List<CLFingerprint>) =
        withContext(Dispatchers.IO) {
            fingerprints.forEach {
                val data = it.jp2ByteArray
                val fileName = "[REG]_${it.fingerPosition.name}"
                launch {
                    fileRepository.saveJP2FingerImageToGallery(
                        uid = currentUidHash.take(10),
                        fingerType = FingerType.Contactless,
                        fileName = fileName,
                        data = data
                    )
                }
            }
        }

    fun handleSdkActivityResult(resultCode: Int, data: Intent?) {
        viewModelScope.launch {
            sdkManager.parseResponse(resultCode, data)
        }
    }

    fun captureFingerprint(
        fingerPosition: FingerPosition,
        launcher: ActivityResultLauncher<Intent>
    ) {
        startFingerprintCapture(fingerPosition) {
            sdkManager.captureFingerprint(activityResultLauncher = launcher, purpose = "register", fingerPosition = fingerPosition)
        }
    }


    fun clearError() {
        _uiState.update {
            it.copy(
                message = null
            )
        }
    }
}

sealed class RegistrationResult {
    object Success : RegistrationResult()
    data class Error(val message: String) : RegistrationResult()
}