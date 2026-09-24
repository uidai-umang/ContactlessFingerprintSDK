package app.gov.uidai.capture.ui.camera.slap

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
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
import app.gov.uidai.capture.domain.method.final_processing.FinalProcessingU2Net
import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.domain.model.ProcessingResult
import app.gov.uidai.capture.slap.processing.SlapFingerprintProcessor
import app.gov.uidai.capture.usecase.factory.SegmentationFactory
import app.gov.uidai.capture.utils.KotlinUtils
import app.gov.uidai.capture.utils.extension.toBase64
import app.gov.uidai.capture.utils.extension.toByteArray
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.gov.uidai.network.data.local.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.getValue

@HiltViewModel
class SlapCaptureViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    val cameraController: CameraController,
    private val analyzer: SlapFrameAnalyzer,
    private val fileRepository: FileRepository,
    private val segmentationFactory: SegmentationFactory,
    private val blurChecker: SlapBlurChecker,
    private val preferenceStore: PreferenceStore
) : ViewModel() {

    companion object {
        private val TAG = SlapCaptureViewModel::class.simpleName
    }

    init {
        // Slap-only, device-independent fixed focus -- see
        // SlapFixedDistanceFocus's kdoc and CameraController.
        // useSlapFixedFocus's kdoc. Does not touch the shared FOCUS_TYPE
        // preference or any existing FocusManager class, so single-finger
        // capture (a separate CameraController instance) is unaffected.
        cameraController.useSlapFixedFocus(preferenceStore.get(CameraSettings.TARGET_HAND_DISTANCE_MM))
    }

    private var expectedHandType: String = "Left"
    private var listener: SlapCaptureListener? = null

    private val _liveState = MutableStateFlow(SlapLiveState())
    val liveState = _liveState.asStateFlow()

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap = _capturedBitmap.asStateFlow()

    private val _isTorchOn = MutableStateFlow(preferenceStore.get(CameraSettings.TORCH_ON))
    val isTorchOn = _isTorchOn.asStateFlow()

    private val segmentationCheck by lazy { segmentationFactory.create() }

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
            },
            getHandDistanceMM = { box, size, rotation ->
                cameraController.getHandDistanceMM(box, size, rotation)
            },
            targetHandDistanceMM = preferenceStore.get(CameraSettings.TARGET_HAND_DISTANCE_MM),
            handDistanceToleranceMM = preferenceStore.get(CameraSettings.HAND_DISTANCE_TOLERANCE_MM)
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

    /**
     * Mirrors CameraViewModel.processImageAfterSuccess: segmentation ->
     * FinalProcessingU2Net -> save. Runs AFTER the review screen is
     * already showing (which displays a spinner while bitmap is null),
     * so the ~5-6s segmentation cost is hidden rather than freezing the
     * preview before review appears.
     *
     * Returns the base64 ridge image for the CaptureResult. Falls back to
     * the colour crop if segmentation produces no mask -- same silent
     * degradation FinalProcessingU2Net.run already has when maskedImage
     * is null.
     */

    private val slapFingerprintProcessor = SlapFingerprintProcessor(context = appContext)

    suspend fun processCapturedImage(
        colourCrop: Bitmap,
        handType: String
    ): String = withContext(Dispatchers.Default) {

        try {
            val slapHandType = if (handType.equals("LEFT", ignoreCase = true)) {
                SlapFingerprintProcessor.HandType.LEFT
            } else {
                SlapFingerprintProcessor.HandType.RIGHT
            }

            val result = slapFingerprintProcessor.process(
                bitmap = colourCrop,
                handType = slapHandType
            )

            val diagnosticUri = fileRepository.saveBitmapAndGetUri(result.diagnosticImage, "Slap_Finger_Detection_Debug")
            Log.d("DiagnosticImage", "FINGER DETECTION URI = $diagnosticUri")

            // Whole hand-region crop -- synced to backend as "the frame".
            val handRegionUri = fileRepository.saveBitmapAndGetUri(result.handRegionBitmap, "${handType.uppercase()}_SLAP_HAND_REGION")
            Log.d("DiagnosticImage", "Hand region URI = $handRegionUri")

            result.fingers.forEach { finger ->
                val roiUri = fileRepository.saveBitmapAndGetUri(finger.fingerprintRoi, "Slap_Finger_${finger.index + 1}_ROI")
                Log.d("DiagnosticImage", "Finger ${finger.index + 1} ROI URI = $roiUri")

                finger.segmentationMask?.let { mask ->
                    val maskUri = fileRepository.saveBitmapAndGetUri(mask, "Slap_Finger_${finger.index + 1}_Mask")
                    Log.d("DiagnosticImage", "Finger ${finger.index + 1} Mask URI = $maskUri")
                } ?: Log.w("DiagnosticImage", "Finger ${finger.index + 1} -- segmentation found no mask")

                val ridgeUri = fileRepository.saveBitmapAndGetUri(finger.ridgeImage, "${handType.uppercase()}_SLAP_Finger_${finger.index + 1}_RIDGES")
                Log.d("DiagnosticImage", "Finger ${finger.index + 1} Ridge URI = $ridgeUri")
            }

            colourCrop.toBase64()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to process Slap fingerprint image", e)
            colourCrop.toBase64()
        }
    }

    suspend fun processPickedImage(pickedBitmap: Bitmap, handType: String): String {
        return processCapturedImage(pickedBitmap, handType)
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