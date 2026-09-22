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
import app.gov.uidai.capture.usecase.slap.SlapMediaPipeAnalyzer
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
    private val mediaPipeAnalyzer: SlapMediaPipeAnalyzer,
    private val segmentationFactory: SegmentationFactory,
    private val blurChecker: SlapBlurChecker,
    private val preferenceStore: PreferenceStore
) : ViewModel() {

    companion object {
        private val TAG = SlapCaptureViewModel::class.simpleName
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
            mediaPipeAnalyzer = mediaPipeAnalyzer,
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
            // 1. Save the exact captured Slap image.
            val inputUri = fileRepository.saveBitmapAndGetUri(
                colourCrop,
                "Slap_ColourCrop_Before_Processing"
            )

            Log.d(
                "DiagnosticImage",
                "SLAP INPUT URI = $inputUri"
            )

            // 2. Convert hand type.
            val slapHandType =
                if (handType.equals("LEFT", ignoreCase = true)) {
                    SlapFingerprintProcessor.HandType.LEFT
                } else {
                    SlapFingerprintProcessor.HandType.RIGHT
                }

            // 3. Run the completely independent Slap pipeline.
            //
            // No:
            // - SlapSkinAreaDetector
            // - MediaPipe
            // - U2Net
            // - existing segmentation pipeline
            //
            // The captured bitmap is passed at its original resolution.
            val result = slapFingerprintProcessor.process(
                bitmap = colourCrop,
                handType = slapHandType
            )

            Log.d(
                TAG,
                "Slap processing complete. " +
                        "Detected fingers = ${result.fingers.size}"
            )

            // 4. Save diagnostic image showing the four detected
            // finger regions over the original image.
            val diagnosticUri = fileRepository.saveBitmapAndGetUri(
                result.diagnosticImage,
                "Slap_Finger_Detection_Debug"
            )

            Log.d(
                "DiagnosticImage",
                "FINGER DETECTION URI = $diagnosticUri"
            )

            // 5. Save every finger's intermediate/final images.
            result.fingers.forEach { finger ->

                val roiUri = fileRepository.saveBitmapAndGetUri(
                    finger.fingerprintRoi,
                    "Slap_Finger_${finger.index + 1}_ROI"
                )

                Log.d(
                    "DiagnosticImage",
                    "Finger ${finger.index + 1} ROI URI = $roiUri"
                )

                finger.segmentationMask?.let { mask ->
                    val maskUri = fileRepository.saveBitmapAndGetUri(
                        mask,
                        "Slap_Finger_${finger.index + 1}_Mask"
                    )
                    Log.d("DiagnosticImage", "Finger ${finger.index + 1} Mask URI = $maskUri")
                } ?: Log.w("DiagnosticImage", "Finger ${finger.index + 1} -- segmentation found no mask")


                val ridgeUri = fileRepository.saveBitmapAndGetUri(
                    finger.ridgeImage,
                    "Slap_Finger_${finger.index + 1}_Ridges"
                )

                Log.d(
                    "DiagnosticImage",
                    "Finger ${finger.index + 1} Ridge URI = $ridgeUri"
                )
            }

            // 6. For this first test, return the first ridge image
            // as the final output.
            //
            // We are primarily interested in inspecting the
            // diagnostic images at this stage.
            val finalBitmap = colourCrop

            val segmentedBitmap = result.fingers
                .firstOrNull()
                ?.ridgeImage
                ?: colourCrop

            // 7. Save the current final output.
            val finalUri = fileRepository.saveBitmapAndGetUri(
                segmentedBitmap,
                "${handType.uppercase()}_SLAP_RIDGES"
            )

            Log.d(
                "DiagnosticImage",
                "FINAL RIDGE URI = $finalUri"
            )

            finalBitmap.toBase64()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to process Slap fingerprint image",
                e
            )

            // Keep the existing failure behaviour.
            colourCrop.toBase64()
        }
    }

    /**
     * TEST-ONLY entry point: bypasses live capture and blur checking
     * entirely. Feeds a gallery-picked bitmap directly into the same
     * SlapFingerprintProcessor pipeline processCapturedImage() already
     * uses, to isolate whether ridge extraction works given genuinely
     * sharp input -- removing camera blur as a variable.
     */
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