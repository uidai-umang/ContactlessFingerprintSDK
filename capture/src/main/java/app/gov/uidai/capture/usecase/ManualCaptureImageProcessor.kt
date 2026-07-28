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
import app.gov.uidai.capture.utils.logExecutionTime
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
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
            coroutineScope: CoroutineScope,
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
        val stage2StartTime = System.currentTimeMillis()
        val processingId = candidateBatch.first().processingId
        Log.d(TAG, "Processing Stage 2 for batch #$processingId")
        Log.d("FLOW_TRACE", "processStage2() entered")

        try {
            val cameraFrame = candidateBatch.last()

            // ----------------------------------------------------------------------
            // SEGMENTATION — DISABLED per product decision.
            // The verification/matching flow that required precise finger-boundary
            // masking no longer exists. Current flow is "capture high-quality RGB
            // image, crop, validate quality, send" — segmentation's actual mask
            // output was never used downstream anyway (SegmentedFrame.finalBitmap
            // already used the plain croppedBitmap, not segmentation's cropped
            // result). Measured cost: ~6,577ms of the ~7,881ms total Manual mode
            // post-capture time. Kept here, commented, for reference / possible
            // future reinstatement if a verification flow is reintroduced.
            // ----------------------------------------------------------------------

            Log.d("FLOW_TRACE", "Building segmentation crop input")
            val (segCroppedByteArray, segCroppedByteArraySize) = logExecutionTime(TAG, "ManualStage2.SegmentationCropPrep") {
                cameraFrame.getByteArray(
                    requiresCropping = preferenceStore.get(ProcessingSettings.CROPPED_INPUT_TO_SEGMENTATION_MODEL),
                    cutoutRect = provider.getCutoutRectInImageCoordinates(
                        Size(cameraFrame.width, cameraFrame.height),
                        cameraFrame.rotationDegrees
                    )
                )
            }
            val segmentationProvider = ImageDataProvider(
                segCroppedByteArray,
                segCroppedByteArraySize.width,
                segCroppedByteArraySize.height,
                cameraFrame.rotationDegrees
            )

            listener.onStage2ProcessingStageUpdate(ProcessingStage.SEGMENTATION)

            Log.d("FLOW_TRACE", "Running segmentation model")
            val segmentationResult = logExecutionTime(TAG, "ManualStage2.SegmentationInference") {
                runInterruptible {
                    segmentationCheck.run(segmentationProvider)
                }
            }

            if (preferenceStore.get(ProcessingSettings.SAVE_SEGMENTATION_INPUT)) {
                controller.saveBitmap(segmentationProvider.getAsUprightBitmap(), "SegInput")
            }


            Log.d("FLOW_TRACE", "Building bitmaps directly from cutout crop (no segmentation)")

            // fullBitmap — the entire uncropped frame
            val (fullByteArray, fullByteArraySize) = logExecutionTime(TAG, "ManualStage2.FullBitmapCropPrep") {
                cameraFrame.getByteArray(
                    requiresCropping = false,
                    cutoutRect = provider.getCutoutRectInImageCoordinates(
                        Size(cameraFrame.width, cameraFrame.height),
                        cameraFrame.rotationDegrees
                    )
                )
            }
            val fullBitmap = logExecutionTime(TAG, "ManualStage2.FullBitmapDecode") {
                fullByteArray.toBitmap(fullByteArraySize).rotate(cameraFrame.rotationDegrees)
            }

            // croppedBitmap — plain cutout-rectangle crop, this is what actually
            // gets sent as the final image now
            val (croppedByteArray, croppedByteArraySize) = logExecutionTime(TAG, "ManualStage2.CroppedBitmapCropPrep") {
                cameraFrame.getByteArray(
                    requiresCropping = true,
                    cutoutRect = provider.getCutoutRectInImageCoordinates(
                        Size(cameraFrame.width, cameraFrame.height),
                        cameraFrame.rotationDegrees
                    )
                )
            }
            val croppedBitmap = logExecutionTime(TAG, "ManualStage2.CroppedBitmapDecode") {
                croppedByteArray.toBitmap(croppedByteArraySize).rotate(cameraFrame.rotationDegrees)
            }

            val segmentedFrame = SegmentedFrame(
                processingId = processingId,
                finalBitmap = croppedBitmap,
                fullBitmap = fullBitmap,
                croppedBitmap = croppedBitmap,
                timestamp = cameraFrame.timestamp,
                finalMask = null   // was segmentationResult.data.mask — no longer produced
            )

            // Step 2: Quality validation on the same cropped image that will be sent
            Log.d("FLOW_TRACE", "Building finalImageProvider for quality re-validation")
            val (segCroppedByteArray2, segCroppedByteArray2Size) = logExecutionTime(TAG, "ManualStage2.FinalImageProviderCropPrep") {
                cameraFrame.getByteArray(
                    requiresCropping = true,
                    cutoutRect = provider.getCutoutRectInImageCoordinates(
                        Size(cameraFrame.width, cameraFrame.height),
                        cameraFrame.rotationDegrees
                    )
                )
            }

            val finalImageProvider = ImageDataProvider(
                segCroppedByteArray2,
                segCroppedByteArray2Size.width,
                segCroppedByteArray2Size.height,
                cameraFrame.rotationDegrees
            )

            listener.onStage2ProcessingStageUpdate(ProcessingStage.BLUR)

            Log.d("FLOW_TRACE", "Starting brightness/glare/blur re-validation (sequential)")
            val brightnessResult = logExecutionTime(TAG, "ManualStage2.Brightness") {
                brightnessCheckStage2.run(finalImageProvider)
            }
            val glareResult = logExecutionTime(TAG, "ManualStage2.Glare") {
                (stage1Methods[GLARE_CHECK]!!).run(finalImageProvider)
            }
            val blurResult = logExecutionTime(TAG, "ManualStage2.Blur") {
                runInterruptible {
                    blurCheck.run(finalImageProvider)
                }
            }
            finalImageProvider.clearCache()

            Log.d("FLOW_TRACE", "All checks complete — evaluating pass/fail")
            val blurThreshold = preferenceStore.get(BlurSettings.THRESHOLD)
            val minBrightness = preferenceStore.get(BrightnessSettings.DARK_THRESHOLD) / 255f
            val maxBrightness = preferenceStore.get(BrightnessSettings.BRIGHT_THRESHOLD) / 255f
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

            Log.d("FLOW_TRACE", "onStage2Result about to fire — passed=$allPassed")
            listener.onStage2Result(
                passed = allPassed,
                errors = errors
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error in Stage 2 processing", e)
            listener.onStage2Result(
                passed = false, listOf()
            )
        } finally {
            Log.d(TAG, "Execution time -- ManualStage2.TOTAL: ${System.currentTimeMillis() - stage2StartTime}ms")
            Log.d("FLOW_TRACE", "processStage2() finished")
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