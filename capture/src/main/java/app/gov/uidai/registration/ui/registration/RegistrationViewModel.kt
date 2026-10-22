package app.gov.uidai.registration.ui.registration

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
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
import app.gov.uidai.registration.model.User
import app.gov.uidai.registration.model.capture.CaptureRequest
import `in`.gov.uidai.network.data.local.FileRepository
import app.gov.uidai.registration.repository.OperatorRepository
import app.gov.uidai.registration.usecase.CaptureQueueManager
import app.gov.uidai.registration.usecase.ResidentUseCase
import app.gov.uidai.registration.usecase.UserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import app.gov.uidai.registration.encryption.EncryptionService
import app.gov.uidai.registration.model.resident.ResidentInput
import app.gov.uidai.capture.ui.camera.CaptureResult
import app.gov.uidai.registration.di.ApplicationScope
import com.gemalto.jp2.JP2Encoder
import `in`.gov.uidai.embedding.FingerEmbedder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.util.UUID
import `in`.gov.uidai.utility.constants.ResultCode
import app.gov.uidai.capture.BuildConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val captureQueueManager: CaptureQueueManager,
    private val pendingCaptureDao: PendingCaptureDao,
    private val fingerEmbedder: FingerEmbedder,
    private val encryptionService: EncryptionService,
    private val operatorRepository: OperatorRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    companion object {
        private val TAG = RegistrationViewModel::class.simpleName
    }

    private val _uiState = MutableStateFlow(RegistrationUiState())
    val uiState = _uiState.asStateFlow()

    private val _registrationResult = MutableStateFlow<RegistrationResult?>(null)
    val registrationResult = _registrationResult.asStateFlow()
    private var currentResidentId: String = ""

    private var selectedCaptureMode: String = CaptureMode.SEQUENTIAL

    private var currentResident: ResidentInput? = null

    private val testDeviceId = "00000000-0000-0000-0000-000000000002"
    private val testCentreId = "00000000-0000-0000-0000-000000000003"


    // ---- Capture loop (sequence + per-capture upload) ----
    // The capture loop destination renders whatever activeCapture points at
    // and pops back to the launching screen once it goes null.
    private val captureSequence = ArrayDeque<FingerPosition>()
    private val uploadMutex = Mutex()
    private val _activeCapture = MutableStateFlow<ActiveCapture?>(null)
    val activeCapture: StateFlow<ActiveCapture?> = _activeCapture.asStateFlow()
    private var captureCounter = 0
    private var segmentCounter = 0

    // Status of the in-flight position before it was flipped to CAPTURING,
    // restored if the capture is aborted / fails.
    private var statusBeforeCapture: FingerCaptureStatus? = null

    private fun FingerPosition.isDone() = when (_uiState.value.fingerUploadStatus[this]) {
        FingerCaptureStatus.CAPTURED, FingerCaptureStatus.PENDING, FingerCaptureStatus.UPLOADING -> true
        else -> false
    }

    /** Sequential mode: tapped finger first, then the rest of that hand, thumb -> little. */
    fun startHandSequence(start: FingerPosition) {
        val hand = if (start.name.startsWith("LEFT")) "LEFT" else "RIGHT"
        val order = listOf("THUMB", "INDEX", "MIDDLE", "RING", "LITTLE")
            .map { FingerPosition.valueOf("${hand}_$it") }
        beginSequence(listOf(start) + order.filter { it != start && !it.isDone() })
    }

    /** Slap mode: chosen option first, then LEFT_SLAP, RIGHT_SLAP, LEFT_THUMB, RIGHT_THUMB. */
    fun startSlapSequence(start: FingerPosition) {
        val canonical = listOf(
            FingerPosition.LEFT_SLAP, FingerPosition.RIGHT_SLAP,
            FingerPosition.LEFT_THUMB, FingerPosition.RIGHT_THUMB
        )
        beginSequence(listOf(start) + canonical.filter { it != start && !it.isDone() })
    }

    private fun beginSequence(positions: List<FingerPosition>) {
        captureSequence.clear()
        captureSequence.addAll(positions)
        advanceSequence()
    }

    private fun advanceSequence() {
        val next = captureSequence.removeFirstOrNull()
        if (next == null) {
            _activeCapture.value = null
            return
        }
        val previous = _activeCapture.value
        // Same screen type as the previous capture -> same segment, so the
        // camera screen stays mounted and only resets between captures.
        val segment = if (previous != null && previous.position.isSlap == next.isSlap) {
            previous.segment
        } else {
            ++segmentCounter
        }
        statusBeforeCapture = _uiState.value.fingerUploadStatus[next]
        _uiState.update {
            it.copy(
                loadingFinger = if (next.isSlap) it.loadingFinger else next,
                fingerUploadStatus = it.fingerUploadStatus.toMutableMap().apply {
                    set(next, FingerCaptureStatus.CAPTURING)
                }
            )
        }
        _activeCapture.value = ActiveCapture(
            position = next,
            id = ++captureCounter,
            segment = segment,
            txnId = UUID.randomUUID().toString()
        )
    }

    /** Back-press / abort / failure / permission denial: stop the chain and leave the loop. */
    fun cancelSequence() {
        captureSequence.clear()
        _activeCapture.value?.let { restoreStatus(it.position) }
        _activeCapture.value = null
    }

    private fun restoreStatus(position: FingerPosition) {
        val restored = statusBeforeCapture ?: FingerCaptureStatus.NOT_CAPTURED
        statusBeforeCapture = null
        _uiState.update {
            it.copy(
                loadingFinger = if (it.loadingFinger == position) null else it.loadingFinger,
                fingerUploadStatus = it.fingerUploadStatus.toMutableMap().apply {
                    set(position, restored)
                }
            )
        }
    }

    /**
     * Called by the capture screen (in memory) when the active capture ends.
     * On success the upload starts in [appScope] -- it must outlive the
     * capture screen -- and the next capture in the sequence starts at once.
     */
    fun onCaptureResult(result: CaptureResult) {
        val capture = _activeCapture.value ?: return
        val bitmap = result.finalImage
        if (result.resultCode != ResultCode.CAPTURE_SUCCESS || bitmap == null) {
            Log.d(TAG, "Capture of ${capture.position} ended with code ${result.resultCode}")
            cancelSequence()
            return
        }

        val position = capture.position
        statusBeforeCapture = null
        _uiState.update {
            it.copy(
                loadingFinger = if (it.loadingFinger == position) null else it.loadingFinger,
                message = null,
                fingerUploadStatus = it.fingerUploadStatus.toMutableMap().apply {
                    set(position, FingerCaptureStatus.UPLOADING)
                }
            )
        }

        val captureMode = if (position.isSlap) CaptureMode.SLAP else selectedCaptureMode
        appScope.launch {
            processAndUpload(
                position = position,
                captureMode = captureMode,
                bitmap = bitmap,
                blurScore = result.blurScore.toDouble(),
                brightnessScore = result.brightnessScore.toDouble(),
                glareScore = result.glareScore.toDouble()
            )
        }
        advanceSequence()
    }

    private suspend fun processAndUpload(
        position: FingerPosition,
        captureMode: String,
        bitmap: Bitmap,
        blurScore: Double,
        brightnessScore: Double,
        glareScore: Double
    ) {
        try {
            val jp2ByteArray = JP2Encoder(bitmap).encode()
            if (!position.isSlap) {
                // Single fingers keep the embedding + preview the finger grid
                // shows; a whole-hand slap image isn't a single fingerprint.
                val (embedding, fingerQuality) = fingerEmbedder.embed(jp2ByteArray)
                val fingerprint = CLFingerprint(
                    fingerPosition = position,
                    bitmap = bitmap,
                    embedding = embedding,
                    fingerQuality = fingerQuality,
                    jp2ByteArray = jp2ByteArray,
                    blurScore = blurScore,
                    brightnessScore = brightnessScore,
                    glareScore = glareScore
                )
                _uiState.update {
                    it.copy(fingerprints = it.fingerprints.toMutableMap().apply { set(position, fingerprint) })
                }
            }
            uploadCapture(
                fingerPosition = position,
                captureMode = captureMode,
                imageBytes = jp2ByteArray,
                blurScore = blurScore,
                brightnessScore = brightnessScore,
                glareScore = glareScore
            )
        } catch (e: Exception) {
            Log.e(TAG, "Processing/upload of $position failed", e)
            _uiState.update {
                it.copy(
                    message = "Unable to process ${position.name.replace('_', ' ')} capture.",
                    fingerUploadStatus = it.fingerUploadStatus.toMutableMap().apply {
                        set(position, FingerCaptureStatus.NOT_CAPTURED)
                    }
                )
            }
        }
    }


    fun setResident(resident: ResidentInput) {
        // Same resident may be set again when navigating back into this
        // shared instance from a sibling destination (e.g. CaptureMethod)
        // -- avoid re-triggering a fresh lookup/session for no reason.
        if (resident == currentResident && currentResidentId.isNotEmpty()) return
        currentResident = resident
        currentResidentId = ""
        lookupResidentAndCreateSession()
    }
    private fun lookupResidentAndCreateSession() {
        val resident = currentResident ?: return
        _uiState.update { it.copy(isLookingUpResident = true) }

        viewModelScope.launch {
            val result = residentUseCase.lookupResident(
                residentRefId = resident.refId,
                dateOfBirth = resident.dob,
                gender = resident.gender
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

    private suspend fun uploadCapture(
        fingerPosition: FingerPosition,
        captureMode: String,
        imageBytes: ByteArray,
        blurScore: Double = 0.0,
        brightnessScore: Double = 0.0,
        glareScore: Double = 0.0
    ) {
        val hand = if (fingerPosition.name.startsWith("LEFT")) "LEFT" else "RIGHT"

        val encrypted = withContext(Dispatchers.Default) {
            encryptionService.encryptImage(imageBytes)
        }

        val request = CaptureRequest(
            residentPseudonymId = currentResidentId,
            operatorId = operatorRepository.getOperatorId(),
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

        val result = uploadMutex.withLock { captureQueueManager.uploadOrQueue(request) }

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
                    uidHash = currentResident?.refId.orEmpty(),
                    user = User(
                        name = currentState.name,
                        phoneNumber = currentState.phoneNumber
                    ),
                    fingerprints = fingerprints
                )

                if (BuildConfig.DEBUG) saveRegisteredImagesToGallery(fingerprints = fingerprints)

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
                        uid = currentResident?.refId.orEmpty().take(10),
                        fingerType = FingerType.Contactless,
                        fileName = fileName,
                        data = data
                    )
                }
            }
        }

    fun setSelectedCaptureMode(mode: String) {
        selectedCaptureMode = mode
    }


    fun clearError() {
        _uiState.update {
            it.copy(
                message = null
            )
        }
    }
}

data class ActiveCapture(
    val position: FingerPosition,
    /** Unique per capture; the capture screens reset when it changes. */
    val id: Int,
    /** Changes only when switching between single-finger and slap screens. */
    val segment: Int,
    val txnId: String
)

private val FingerPosition.isSlap: Boolean
    get() = this == FingerPosition.LEFT_SLAP || this == FingerPosition.RIGHT_SLAP

sealed class RegistrationResult {
    object Success : RegistrationResult()
    data class Error(val message: String) : RegistrationResult()
}