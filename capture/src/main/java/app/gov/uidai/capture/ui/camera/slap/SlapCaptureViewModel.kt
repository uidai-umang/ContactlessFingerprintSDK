package app.gov.uidai.capture.ui.camera.slap

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gov.uidai.capture.pref.PreferenceStore
import app.gov.uidai.capture.ui.camera.CameraController
import app.gov.uidai.capture.ui.camera.config.CameraSettings
import app.gov.uidai.capture.usecase.slap.SlapBlurChecker
import app.gov.uidai.capture.usecase.slap.SlapCaptureListener
import app.gov.uidai.capture.usecase.slap.SlapFrameAnalyzer
import app.gov.uidai.capture.usecase.slap.SlapLiveState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SlapCaptureViewModel @Inject constructor(
    val cameraController: CameraController,
    private val analyzer: SlapFrameAnalyzer,
    private val blurChecker: SlapBlurChecker,
    private val preferenceStore: PreferenceStore
) : ViewModel() {

    private var expectedHandType: String = "Left"
    private var listener: SlapCaptureListener? = null

    private val _liveState = MutableStateFlow(SlapLiveState())
    val liveState = _liveState.asStateFlow()

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap = _capturedBitmap.asStateFlow()

    private val _isTorchOn = MutableStateFlow(preferenceStore.get(CameraSettings.TORCH_ON))
    val isTorchOn = _isTorchOn.asStateFlow()

    fun setExpectedHandType(handType: String) {
        expectedHandType = handType
    }

    fun toggleTorch() {
        val newValue = !_isTorchOn.value
        preferenceStore.save(CameraSettings.TORCH_ON.apply { currentValue = newValue })
        _isTorchOn.update { newValue }
        cameraController.updateTorchState()
    }

    fun getOrCreateListener(getRotationDegrees: () -> Int): SlapCaptureListener {
        listener?.let { return it }
        val newListener = SlapCaptureListener(
            expectedHandType = expectedHandType,
            analyzer = analyzer,
            blurChecker = blurChecker,
            coroutineScope = viewModelScope,
            getRotationDegrees = getRotationDegrees,
            triggerFocus = { box, size, rotation ->
                cameraController.triggerHandFocusLock(box, size, rotation)
            }
        )
        listener = newListener
        viewModelScope.launch { newListener.liveState.collect { _liveState.value = it } }
        viewModelScope.launch {
            newListener.capturedBitmap.collect { bitmap ->
                if (bitmap != null) _capturedBitmap.value = bitmap
            }
        }
        return newListener
    }

    fun reset() {
        listener?.reset()
        _capturedBitmap.value = null
        _liveState.value = SlapLiveState()
    }

    override fun onCleared() {
        super.onCleared()
        cameraController.setOnImageAvailableListener(null)
        cameraController.closeCamera()
    }
}