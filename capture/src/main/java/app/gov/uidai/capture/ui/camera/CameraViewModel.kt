package app.gov.uidai.capture.ui.camera

import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gov.uidai.capture.domain.method.final_processing.FinalProcessingU2Net
import app.gov.uidai.capture.domain.model.SegmentedFrame
import app.gov.uidai.capture.pref.PreferenceStore
import app.gov.uidai.capture.ui.camera.config.CameraSettings
import app.gov.uidai.capture.ui.camera.config.SessionSettings
import app.gov.uidai.capture.ui.camera.focus.FocusType
import app.gov.uidai.capture.ui.camera.manager.CaptureStateManager
import app.gov.uidai.capture.ui.camera.manager.SessionTimerManager
import app.gov.uidai.capture.ui.camera.model.CaptureState
import app.gov.uidai.capture.ui.camera.model.Stage2ResultValue
import app.gov.uidai.capture.ui.camera.model.UIMessage
import app.gov.uidai.capture.ui.camera.view.OverlayView
import app.gov.uidai.capture.utils.extension.toBase64
import autovalue.shaded.com.`google$`.common.primitives.`$Longs`.max
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.gov.uidai.network.data.local.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CaptureUIState(
    val headingTextRes: Int? = null,
    val overlayViewState: OverlayView.State = OverlayView.State.INITIAL,
    val isManualCapture: Boolean = false,
    val isCaptureEnabled: Boolean = false,
    val isTorchOn: Boolean = false,
    val isManualFocus: Boolean = false,
    val version: String = "1.0",
    val transactionId: String = "",
    val date: String = "01/01/2025",
    val isBottomSheetVisible: Boolean = false,
    val bottomSheetMessage: UIMessage? = null,
    val bottomSheetResults: Stage2ResultValue? = null
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val preferenceStore: PreferenceStore,
    val captureStateManager: CaptureStateManager,
) : ViewModel() {

    companion object {
        private val TAG = CameraViewModel::class.simpleName
    }

    val captureState = captureStateManager.captureState

    private val isTorchOn
        get() = preferenceStore.get(CameraSettings.TORCH_ON)

    private val isManualCapture
        get() = preferenceStore.get(CameraSettings.MANUAL_CAPTURE)

    private val isManualFocus: Boolean
        get() {
            val focusType = preferenceStore.get(CameraSettings.FOCUS_TYPE)
            return (focusType == FocusType.ManualFocusAtFixedDistance)
        }

    // New Capture UI State migration going on
    private val _captureUIState = MutableStateFlow(CaptureUIState())
    val captureUIState = _captureUIState.asStateFlow()

    private val sessionTimeoutInMillis
        get() = preferenceStore.get(SessionSettings.TIMEOUT) * 1000L

    private val sessionTimerManager = SessionTimerManager(sessionTimeoutInMillis)

    val sessionTimeoutEvent = sessionTimerManager.sessionTimeoutEvent

    init {
        sessionTimerManager.setCoroutineScope(viewModelScope)

        viewModelScope.launch {
            captureStateManager.captureState.collectLatest { state ->
                if (state is CaptureState.AutoCaptureSuccess && sessionTimerManager.isSessionTimerActive()) {
                    Log.i(TAG, "SUCCESS state reached - stopping session timer")
                    sessionTimerManager.stopSessionTimer()
                }
            }
        }

        _captureUIState.update {
            it.copy(
                isTorchOn = isTorchOn,
                isManualCapture = isManualCapture,
                isManualFocus = isManualFocus
            )
        }
    }

    fun getCurrentCaptureState(): CaptureState {
        return captureStateManager.currentState()
    }
    fun startSessionTimer(){
        sessionTimerManager.startSessionTimer()
    }

    fun resetSessionTimerTo(timeInMillis: Long) {
        sessionTimerManager.resetSessionTimerTo(timeInMillis)
    }

    fun getSessionElapsedTime(): Long {
        return sessionTimerManager.getTimeElapsed()
    }

    fun updateTorchState() {
        val newIsTorchOn = !isTorchOn
        val newValue = CameraSettings.TORCH_ON.apply { currentValue = newIsTorchOn }
        preferenceStore.save(newValue)

        _captureUIState.update {
            it.copy(
                isTorchOn = newIsTorchOn
            )
        }
    }

    fun updateManualCaptureState(isManual: Boolean) {
        val newValue = CameraSettings.MANUAL_CAPTURE.apply { currentValue = isManual }
        preferenceStore.save(newValue)

        _captureUIState.update {
            it.copy(
                isManualCapture = isManual
            )
        }
    }

    fun updateManualFocusState(){
        val newIsManualFocus = !isManualFocus
        val newValue = if(newIsManualFocus) {
            CameraSettings.FOCUS_TYPE.copy(currentValue = FocusType.ManualFocusAtFixedDistance)
        } else {
            CameraSettings.FOCUS_TYPE.copy(currentValue = FocusType.ContinuousAF)
        }
        preferenceStore.save(newValue)

        _captureUIState.update {
            it.copy(
                isManualFocus = newIsManualFocus
            )
        }
    }

    fun getManualFocusDistance(): Float {
        return preferenceStore.get(CameraSettings.MANUAL_FOCUS_DISTANCE)
    }

    fun updateManualFocusDistance(distance: Float){
        val newValue = CameraSettings.MANUAL_FOCUS_DISTANCE.copy(currentValue = distance)
        preferenceStore.save(newValue)
    }

    suspend fun saveBitmapAndGetUri(
        bitmap: Bitmap,
        captureName: String
    ): Uri {
        return fileRepository.saveBitmapAndGetUri(bitmap, captureName)
    }

    suspend fun processImageAfterSuccess(segmentedFrame: SegmentedFrame): String =
        withContext(Dispatchers.Default) {
            sessionTimerManager.stopSessionTimer()
            delay(300)
            val startTime = SystemClock.uptimeMillis()

            Log.d(TAG, "ACTIVITY_RESULT -- encoding Pid and sending it")
            val image = segmentedFrame.finalBitmap
            val maskedImage = segmentedFrame.finalMask
            val enhancedImage = FinalProcessingU2Net.run(image, maskedImage)

            saveBitmapAndGetUri(enhancedImage, "FinalEnhancedImage")
            val encodedBitmap = enhancedImage.toBase64()
            val timeLapsed = SystemClock.uptimeMillis() - startTime
            delay(max(2500 - timeLapsed, 1))
            encodedBitmap
        }

    fun reset() {
        sessionTimerManager.resetSessionTimerTo()
        captureStateManager.reset()
        Log.i(TAG, "reset()")
    }

    fun close(){
        sessionTimerManager.stopSessionTimer()
        captureStateManager.reset()
        Log.i(TAG, "close()")
    }
}