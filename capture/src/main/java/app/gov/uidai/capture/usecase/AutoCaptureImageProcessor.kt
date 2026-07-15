package app.gov.uidai.capture.usecase

import android.annotation.SuppressLint
import android.util.Log
import android.util.Size
import androidx.lifecycle.LifecycleCoroutineScope
import app.gov.uidai.capture.domain.config.BlurSettings
import app.gov.uidai.capture.domain.config.BrightnessConfig
import app.gov.uidai.capture.domain.config.GlareConfig
import app.gov.uidai.capture.domain.model.CameraFrame
import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.domain.model.ProcessingResult
import app.gov.uidai.capture.domain.model.ProcessingStage
import app.gov.uidai.capture.domain.model.SegmentedFrame
import app.gov.uidai.capture.pref.PreferenceStore
import app.gov.uidai.capture.ui.camera.model.Error
import app.gov.uidai.capture.usecase.factory.BlurCheckFactory
import app.gov.uidai.capture.usecase.factory.FingerCheckFactory
import app.gov.uidai.capture.usecase.factory.SegmentationFactory
import app.gov.uidai.capture.utils.extension.crop
import app.gov.uidai.capture.utils.extension.rotate
import app.gov.uidai.capture.utils.extension.toBitmap
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class AutoCaptureImageProcessor @AssistedInject constructor(
    segmentationFactory: SegmentationFactory,
    fingerCheckFactory: FingerCheckFactory,
    blurCheckFactory: BlurCheckFactory,
    brightnessConfig: BrightnessConfig,
    glareConfig: GlareConfig,
    settingsManager: PreferenceStore,
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
        ): AutoCaptureImageProcessor
    }

    companion object {
        private val TAG = AutoCaptureImageProcessor::class.simpleName
    }

    private val blurExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "BlurCheckThread")
    }

    override val DELAY_IN_ACCUMULATION_OF_FRAMES: Long
        get() {
            val override = preferenceStore.get(ProcessingSettings.ACCUMULATION_DELAY_OVERRIDE_MS)
            return if (override >= 0) override.toLong() else strategyConfig.accumulationDelayMs
        }

    override val isReadyForAccumulation: Boolean
        get() =  isStage1Passed.get() && provider.isFocusLockedForCapture

    @SuppressLint("DefaultLocale")
    override suspend fun processStage2(candidateBatch: List<CameraFrame>) {
        val processingId = candidateBatch.first().processingId
        Log.d(TAG, "Processing Stage 2 for batch #$processingId")

        try {
            listener.onStage2ProcessingStageUpdate(ProcessingStage.BLUR)
            val blurThreshold = preferenceStore.get(BlurSettings.THRESHOLD)

            val imageDataProviders = candidateBatch.map { frame ->
                val (byteArray, byteArraySize) = frame.getByteArray(
                    requiresCropping = preferenceStore.get(ProcessingSettings.CROPPED_INPUT_TO_BLUR_MODEL),
                    cutoutRect = provider.getCutoutRectInImageCoordinates(
                        Size(frame.width, frame.height),
                        frame.rotationDegrees
                    )
                )
                ImageDataProvider(
                    byteArray,
                    byteArraySize.width,
                    byteArraySize.height,
                    frame.rotationDegrees
                )
            }

            // Step 1: Run blur check on all frames in parallel
            val blurResults = withContext(blurExecutor.asCoroutineDispatcher()) {
                imageDataProviders.mapIndexed { i, provider ->
                    async {
                        blurCheck.run(provider)
                    }
                }.awaitAll()
            }

            val isBlurPassed = blurResults.any {
                it is ProcessingResult.Passed && it.confidence >= blurThreshold
            }

            if (preferenceStore.get(ProcessingSettings.SAVE_BLUR_INPUT)) {
                imageDataProviders.forEachIndexed { i, provider ->
                    val conf = blurResults[i].confidence
                    val confFormatted = String.format("%.2f", conf).removePrefix("0")
                    controller.saveBitmap(
                        provider.getAsUprightBitmap(),
                        "BlurInput($confFormatted)"
                    )
                }
            }

            imageDataProviders.forEach { it.clearCache() }

            if (!isBlurPassed) {
                listener.onStage2Result(
                    passed = false,
                    errors = listOf(Error.Blur)
                )
                return
            }

            // ----------------------------------------------------------------------
            // SEGMENTATION DISABLED (Testing)
            //
            // Temporarily bypassing segmentation to evaluate AutoCapture speed and
            // overall capture experience. The current flow only requires selecting
            // the best quality frame, cropping it using the capture cutout and
            // returning it as the final image. Segmentation can be re-enabled later
            // if required for finger-boundary validation.
            // ----------------------------------------------------------------------

            /*
            listener.onStage2ProcessingStageUpdate(ProcessingStage.SEGMENTATION)

            val blurSortedIndices = blurResults.indices.sortedByDescending {
                blurResults[it].confidence
            }

            // Step 2: Perform segmentation on the best frame
            val bestFrame = candidateBatch[blurSortedIndices.first()]
            val (segCroppedByteArray, segCroppedByteArraySize) = bestFrame.getByteArray(
                requiresCropping = preferenceStore.get(ProcessingSettings.CROPPED_INPUT_TO_SEGMENTATION_MODEL),
                cutoutRect = provider.getCutoutRectInImageCoordinates(
                    Size(bestFrame.width, bestFrame.height),
                    bestFrame.rotationDegrees
                )
            )
            val segmentationProvider = ImageDataProvider(
                segCroppedByteArray,
                segCroppedByteArraySize.width,
                segCroppedByteArraySize.height,
                bestFrame.rotationDegrees
            )
            val segmentationResult = runInterruptible {
                segmentationCheck.run(segmentationProvider)
            }

            if (preferenceStore.get(ProcessingSettings.SAVE_SEGMENTATION_INPUT)) {
                controller.saveBitmap(
                    segmentationProvider.getAsUprightBitmap(),
                    "SegInput"
                )
            }

            Log.d(TAG, "Segmentation Result: $segmentationResult")

            val segmentedFrame = when (segmentationResult) {
                is ProcessingResult.Failed -> {
                    listener.onStage2Result(
                        passed = false,
                        errors = listOf(segmentationResult.cause as Error)
                    )
                    return
                }

                is ProcessingResult.Passed -> {
                    val boundingBox = segmentationResult.data.box
                    val finalBitmap = segmentationProvider.getAsUprightBitmap().crop(boundingBox)

                    val (fullByteArray, fullByteArraySize) = bestFrame.getByteArray(
                        requiresCropping = false,
                        cutoutRect = provider.getCutoutRectInImageCoordinates(
                            Size(bestFrame.width, bestFrame.height),
                            bestFrame.rotationDegrees
                        )
                    )

                    val fullBitmap = fullByteArray.toBitmap(fullByteArraySize)
                        .rotate(bestFrame.rotationDegrees)

                    val (croppedByteArray, croppedByteArraySize) = bestFrame.getByteArray(
                        requiresCropping = true,
                        cutoutRect = provider.getCutoutRectInImageCoordinates(
                            Size(bestFrame.width, bestFrame.height),
                            bestFrame.rotationDegrees
                        )
                    )

                    val croppedBitmap = croppedByteArray.toBitmap(croppedByteArraySize)
                        .rotate(bestFrame.rotationDegrees)

                    if (preferenceStore.get(ProcessingSettings.SAVE_FINAL_OUTPUT)) {
                        controller.saveBitmap(finalBitmap, "FinalOutput")
                        segmentationResult.data.mask?.let {
                            controller.saveBitmap(it, "FinalOutputMask")
                        }
                    }

                    SegmentedFrame(
                        processingId = processingId,
                        finalBitmap = croppedBitmap,
                        fullBitmap = fullBitmap,
                        croppedBitmap = croppedBitmap,
                        timestamp = bestFrame.timestamp,
                        finalMask = segmentationResult.data.mask
                    )
                }
            }

            segmentationProvider.clearCache()
            */


            val blurSortedIndices = blurResults.indices.sortedByDescending {
                blurResults[it].confidence
            }

            // Use the sharpest frame directly
            val bestFrame = candidateBatch[blurSortedIndices.first()]

            // Full image
            val (fullByteArray, fullByteArraySize) = bestFrame.getByteArray(
                requiresCropping = false,
                cutoutRect = provider.getCutoutRectInImageCoordinates(
                    Size(bestFrame.width, bestFrame.height),
                    bestFrame.rotationDegrees
                )
            )

            val fullBitmap = fullByteArray
                .toBitmap(fullByteArraySize)
                .rotate(bestFrame.rotationDegrees)

            // Cropped image (this is the final image that will be uploaded)
            val (croppedByteArray, croppedByteArraySize) = bestFrame.getByteArray(
                requiresCropping = true,
                cutoutRect = provider.getCutoutRectInImageCoordinates(
                    Size(bestFrame.width, bestFrame.height),
                    bestFrame.rotationDegrees
                )
            )

            val croppedBitmap = croppedByteArray
                .toBitmap(croppedByteArraySize)
                .rotate(bestFrame.rotationDegrees)

            // Save debug output if enabled
            if (preferenceStore.get(ProcessingSettings.SAVE_FINAL_OUTPUT)) {
                controller.saveBitmap(croppedBitmap, "FinalOutput")
            }

            val segmentedFrame = SegmentedFrame(
                processingId = processingId,
                finalBitmap = croppedBitmap,
                fullBitmap = fullBitmap,
                croppedBitmap = croppedBitmap,
                timestamp = bestFrame.timestamp,
                finalMask = null
            )

            blurSortedIndices.forEach { _ ->
                addToFinalBuffer(segmentedFrame)
            }

            stopCaptureTimer()
            listener.onStage2Result(
                passed = true,
                listOf()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in Stage 2 processing", e)
            listener.onStage2Result(
                passed = false,
                listOf()
            )
        }
    }

    override fun unlockAccumulator() {
        // DO Nothing
    }

    override fun close() {
        super.close()
        blurExecutor.shutdown()
        Log.i(TAG, "close()")
    }
}