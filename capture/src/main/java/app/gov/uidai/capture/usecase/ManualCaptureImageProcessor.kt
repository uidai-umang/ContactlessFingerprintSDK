package app.gov.uidai.capture.usecase

import android.util.Log
import android.util.Size
import androidx.lifecycle.LifecycleCoroutineScope
import app.gov.uidai.capture.domain.config.BlurSettings
import app.gov.uidai.capture.domain.config.BrightnessConfig
import app.gov.uidai.capture.domain.config.BrightnessSettings
import app.gov.uidai.capture.domain.config.GlareConfig
import app.gov.uidai.capture.domain.config.GlareSettings
import app.gov.uidai.capture.domain.method.brightness.BrightnessCheckStage2
import app.gov.uidai.capture.domain.model.CameraFrame
import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.domain.model.ProcessingResult
import app.gov.uidai.capture.domain.model.ProcessingStage
import app.gov.uidai.capture.domain.model.SegmentedFrame
import app.gov.uidai.capture.pref.PreferenceStore
import app.gov.uidai.capture.ui.camera.model.Error
import app.gov.uidai.capture.ui.camera.model.Stage2ResultValue
import app.gov.uidai.capture.usecase.factory.BlurCheckFactory
import app.gov.uidai.capture.usecase.factory.FingerCheckFactory
import app.gov.uidai.capture.usecase.factory.SegmentationFactory
import app.gov.uidai.capture.utils.extension.crop
import app.gov.uidai.capture.utils.extension.rotate
import app.gov.uidai.capture.utils.extension.toBitmap
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.atomic.AtomicBoolean

class ManualCaptureImageProcessor @AssistedInject constructor(
    settingsManager: PreferenceStore,
    segmentationFactory: SegmentationFactory,
    fingerCheckFactory: FingerCheckFactory,
    blurCheckFactory: BlurCheckFactory,
    brightnessConfig: BrightnessConfig,
    glareConfig: GlareConfig,
    @Assisted coroutineScope: LifecycleCoroutineScope,
    @Assisted provider: Provider,
    @Assisted controller: Controller,
    @Assisted listener: Listener
) : ImageProcessor(
    preferenceStore = settingsManager,
    segmentationFactory = segmentationFactory,
    fingerCheckFactory = fingerCheckFactory,
    blurCheckFactory = blurCheckFactory,
    brightnessConfig = brightnessConfig,
    glareConfig = glareConfig,
    coroutineScope = coroutineScope,
    provider = provider,
    controller = controller,
    listener = listener
) {

    @AssistedFactory
    interface Factory {
        fun create(
            coroutineScope: LifecycleCoroutineScope,
            provider: Provider,
            controller: Controller,
            listener: Listener
        ): ManualCaptureImageProcessor
    }

    companion object {
        private val TAG = ManualCaptureImageProcessor::class.simpleName
    }

    private val isAccumulatorLocked = AtomicBoolean(true)
    private val brightnessCheckStage2 = BrightnessCheckStage2()

    override val DELAY_IN_ACCUMULATION_OF_FRAMES: Long
        get() = 0L


    override val isReadyForAccumulation: Boolean
        get() = !isAccumulatorLocked.get()


    override suspend fun processStage2(candidateBatch: List<CameraFrame>) {
        val processingId = candidateBatch.first().processingId
        Log.d(TAG, "Processing Stage 2 for batch #$processingId")

        try {
            // Step 1: Perform segmentation on the last frame
            val cameraFrame = candidateBatch.last()
            val (segCroppedByteArray, segCroppedByteArraySize) = cameraFrame.getByteArray(
                requiresCropping = preferenceStore.get(ProcessingSettings.CROPPED_INPUT_TO_SEGMENTATION_MODEL),
                cutoutRect = provider.getCutoutRectInImageCoordinates(
                    Size(cameraFrame.width, cameraFrame.height),
                    cameraFrame.rotationDegrees
                )
            )
            val segmentationProvider = ImageDataProvider(
                segCroppedByteArray,
                segCroppedByteArraySize.width,
                segCroppedByteArraySize.height,
                cameraFrame.rotationDegrees
            )

            listener.onStage2ProcessingStageUpdate(ProcessingStage.SEGMENTATION)

            val segmentationResult = runInterruptible {
                segmentationCheck.run(segmentationProvider)
            }

            if (preferenceStore.get(ProcessingSettings.SAVE_SEGMENTATION_INPUT)) {
                controller.saveBitmap(segmentationProvider.getAsUprightBitmap(), "SegInput")
            }

            val segmentedFrame = when (segmentationResult) {
                is ProcessingResult.Failed -> {
                    listener.onStage2Result(
                        passed = false, errors = listOf(segmentationResult.cause as Error)
                    )
                    return
                }

                is ProcessingResult.Passed -> {
                    val boundingBox = segmentationResult.data.box
                    val finalBitmap = segmentationProvider.getAsUprightBitmap().crop(boundingBox)
                    if (preferenceStore.get(ProcessingSettings.SAVE_FINAL_OUTPUT)) {
                        controller.saveBitmap(finalBitmap, "FinalOutput")
                        segmentationResult.data.mask?.let {
                            controller.saveBitmap(it, "FinalOutputMask")
                        }
                    }

                    val (fullByteArray, fullByteArraySize) = cameraFrame.getByteArray(
                        requiresCropping = false,
                        cutoutRect = provider.getCutoutRectInImageCoordinates(
                            Size(cameraFrame.width, cameraFrame.height),
                            cameraFrame.rotationDegrees
                        )
                    )

                    val fullBitmap = fullByteArray.toBitmap(fullByteArraySize)
                        .rotate(cameraFrame.rotationDegrees)

                    val (croppedByteArray, croppedByteArraySize) = cameraFrame.getByteArray(
                        requiresCropping = true,
                        cutoutRect = provider.getCutoutRectInImageCoordinates(
                            Size(cameraFrame.width, cameraFrame.height),
                            cameraFrame.rotationDegrees
                        )
                    )

                    val croppedBitmap = croppedByteArray.toBitmap(croppedByteArraySize)
                        .rotate(cameraFrame.rotationDegrees)

                    SegmentedFrame(
                        processingId = processingId,
                        finalBitmap = croppedBitmap,
                        fullBitmap = fullBitmap,
                        croppedBitmap = croppedBitmap,
                        timestamp = cameraFrame.timestamp,
                        finalMask = segmentationResult.data.mask
                    )
                }
            }

            segmentationProvider.clearCache()

            val (segCroppedByteArray2, segCroppedByteArray2Size) = cameraFrame.getByteArray(
                requiresCropping = true,
                cutoutRect = provider.getCutoutRectInImageCoordinates(
                    Size(cameraFrame.width, cameraFrame.height),
                    cameraFrame.rotationDegrees
                )
            )

            // Step 2: Run other checks on the segmented bitmap
            val finalImageProvider = ImageDataProvider(
                segCroppedByteArray2,
                segCroppedByteArray2Size.width,
                segCroppedByteArray2Size.height,
                cameraFrame.rotationDegrees
            )

            listener.onStage2ProcessingStageUpdate(ProcessingStage.BLUR)

            val brightnessResult = brightnessCheckStage2.run(finalImageProvider)
            val glareResult = (stage1Methods[GLARE_CHECK]!!).run(finalImageProvider)
            val blurResult = runInterruptible {
                blurCheck.run(finalImageProvider)
            }

            finalImageProvider.clearCache()

            val blurThreshold = preferenceStore.get(BlurSettings.THRESHOLD)

            val minBrightness =
                preferenceStore.get(BrightnessSettings.DARK_THRESHOLD) / 255f

            val maxBrightness =
                preferenceStore.get(BrightnessSettings.BRIGHT_THRESHOLD) / 255f

            val glareThresholdMin = preferenceStore.get(GlareSettings.VARIANCE_MIN)
                .toFloat() / preferenceStore.get(GlareSettings.MAX_GLARE_VALUE)

            val glareThresholdMax = preferenceStore.get(GlareSettings.VARIANCE_MAX)
                .toFloat() / preferenceStore.get(GlareSettings.MAX_GLARE_VALUE)

            val brightnessPassed = brightnessResult.passed &&
                    brightnessResult.confidence in minBrightness..maxBrightness

            val glarePassed = glareResult.passed

            val blurPassed = blurResult.passed

            val allPassed = brightnessPassed && glarePassed && blurPassed

            val errors = if (allPassed) {
                listOf<Error>()
            } else {
                listOf(Error.ManualFailure)
            }

            listener.onStage2ResultValues(
                Stage2ResultValue(
                    blurResult = blurResult,
                    brightnessResult = brightnessResult,
                    glareResult = glareResult,
                    blurThreshold = blurThreshold,
                    glareThresholdMin = glareThresholdMin,
                    glareThresholdMax = glareThresholdMax,
                    brightnessThresholdMin = minBrightness,
                    brightnessThresholdMax = maxBrightness
                )
            )

            if (allPassed) {
                addToFinalBuffer(segmentedFrame)
            }

            listener.onStage2Result(
                passed = allPassed, errors = errors
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error in Stage 2 processing", e)
            listener.onStage2Result(
                passed = false, listOf()
            )
        }
    }

    override fun unlockAccumulator() {
        isAccumulatorLocked.set(false)
    }

    override fun reset() {
        super.reset()
        isAccumulatorLocked.set(true)
        Log.i(TAG, "reset()")
    }
}