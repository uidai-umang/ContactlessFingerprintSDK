package app.gov.uidai.capture.ui.camera.slap

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
import app.gov.uidai.capture.usecase.factory.SegmentationFactory
import app.gov.uidai.capture.utils.KotlinUtils
import app.gov.uidai.capture.utils.extension.toBase64
import app.gov.uidai.capture.utils.extension.toByteArray
import `in`.gov.uidai.network.data.local.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.getValue

@HiltViewModel
class SlapCaptureViewModel @Inject constructor(
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
    suspend fun processCapturedImage(
        colourCrop: Bitmap,
        handType: String
    ): String = withContext(Dispatchers.Default) {
        try {
            // 1. Save the exact image going INTO U2Net
            val inputUri = fileRepository.saveBitmapAndGetUri(
                colourCrop,
                "ColourCrop_Image_Before_Segmentation"
            )
            Log.d(
                "DiagnosticImage",
                "BEFORE SEGMENTATION URI = $inputUri"
            )

            val bytes = colourCrop.toByteArray()

            val provider = ImageDataProvider(
                bytes,
                colourCrop.width,
                colourCrop.height,
                0
            )

            // 2. Run segmentation
            val segResult = segmentationCheck.run(provider)

            Log.d(
                TAG,
                "Segmentation result = $segResult"
            )

            val mask =
                (segResult as? ProcessingResult.Passed)
                    ?.data
                    ?.mask

            if (mask == null) {
                Log.w(
                    TAG,
                    "Segmentation returned no mask"
                )
            }

            // 3. Current final processing
            val enhanced = FinalProcessingU2Net.run(
                colourCrop,
                mask
            )

            provider.clearCache()

            // 4. Save final result
            val uri = fileRepository.saveBitmapAndGetUri(
                enhanced,
                "${handType.uppercase()}_SLAP_RIDGES"
            )

            Log.d(
                "DiagnosticImage",
                "FINAL IMAGE URI = $uri"
            )

            enhanced.toBase64()

        } catch (e: Exception) {
            Log.e(
                TAG,
                "Failed to process slap capture",
                e
            )

            colourCrop.toBase64()
        }
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