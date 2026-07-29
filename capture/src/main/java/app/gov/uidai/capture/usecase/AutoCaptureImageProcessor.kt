package app.gov.uidai.capture.usecase

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import app.gov.uidai.capture.domain.config.BlurSettings
import app.gov.uidai.capture.domain.config.BrightnessConfig
import app.gov.uidai.capture.domain.config.GlareConfig
import app.gov.uidai.capture.domain.method.blur.LaplacianBlurMethod
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
import app.gov.uidai.capture.utils.extension.rotate
import app.gov.uidai.capture.utils.extension.toBitmap
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class AutoCaptureImageProcessor @AssistedInject constructor(
    segmentationFactory: SegmentationFactory,
    fingerCheckFactory: FingerCheckFactory,
    blurCheckFactory: BlurCheckFactory,
    brightnessConfig: BrightnessConfig,
    glareConfig: GlareConfig,
    settingsManager: PreferenceStore,
    @Assisted coroutineScope: CoroutineScope,
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
        ): AutoCaptureImageProcessor
    }

    companion object {
        private val TAG = AutoCaptureImageProcessor::class.simpleName
    }

    private val blurExecutor = Executors.newFixedThreadPool(4) { r ->
        Thread(r, "BlurCheckThread")
    }
    private val stage2LaplacianCheck by lazy {
        LaplacianBlurMethod(minVariance = 370f)
    }

    override val DELAY_IN_ACCUMULATION_OF_FRAMES: Long
        get() {
            val override = preferenceStore.get(ProcessingSettings.ACCUMULATION_DELAY_OVERRIDE_MS)
            return if (override >= 0) override.toLong() else strategyConfig.accumulationDelayMs
        }

    override val isReadyForAccumulation: Boolean
        get() = isStage1Passed.get() && provider.isFocusLockedForCapture

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
            // Step 1: Run blur check on all frames in parallel — now DUAL:
            // DenseNet (blurCheck) AND Stage 2's own, independent, stricter
            // Laplacian check. Both must pass for a candidate to be eligible.
            // This closes the exact gap found earlier this session — DenseNet's
            // 224x224 resize destroys full-resolution sharpness signal that
            // Laplacian, run at full crop resolution, still catches.
            data class DualBlurResult(
                val denseNet: ProcessingResult<Unit>,
                val laplacian: ProcessingResult<Unit>
            ) {
                val bothPassed: Boolean
                    get() = denseNet is ProcessingResult.Passed && denseNet.confidence >= blurThreshold &&
                            laplacian.passed
            }
            val blurResults = withContext(blurExecutor.asCoroutineDispatcher()) {
                imageDataProviders.map { provider ->
                    async {
                        val denseNet = blurCheck.run(provider)
                        val laplacian = stage2LaplacianCheck.run(provider)
                        DualBlurResult(denseNet, laplacian)
                    }
                }.awaitAll()
            }
            val isBlurPassed = blurResults.any { it.bothPassed }
            if (preferenceStore.get(ProcessingSettings.SAVE_BLUR_INPUT)) {
                imageDataProviders.forEachIndexed { i, provider ->
                    val conf = blurResults[i].denseNet.confidence
                    val confFormatted = String.format("%.2f", conf).removePrefix("0")
                    controller.saveBitmap(
                        provider.getAsUprightBitmap(),
                        "BlurInput($confFormatted)"
                    )
                }
            }
            imageDataProviders.forEach { it.clearCache() }
            if (!isBlurPassed) {
                Log.w(
                    TAG,
                    "STAGE2_REJECT -- Blur failed. DenseNet confidences: ${blurResults.map { it.denseNet.confidence }}, Laplacian passed: ${blurResults.map { it.laplacian.passed }}"
                )
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
            // Only rank among candidates where BOTH checks passed
            val blurSortedIndices = blurResults.indices
                .filter { blurResults[it].bothPassed }
                .sortedByDescending { blurResults[it].denseNet.confidence }
            // Use the sharpest (dual-passed) frame directly
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
            // Compute real quality scores for the DELIVERED image — this is the
            // ground truth sent to the backend as metadata. Blur is now RE-SCORED
            // directly on croppedByteArray (the exact bytes that get saved and
            // sent), instead of reusing the earlier ranking-stage score computed
            // on a separately-decoded candidate. That earlier version could
            // disagree with the delivered image's real sharpness — this is what
            // let visibly blurry images through with a high reported blur_score
            // (e.g. 0.969 on a genuinely blurry capture). Brightness/glare were
            // already correctly scored on the final crop; only blur had this gap.
            // Final re-score is ALSO now dual — DenseNet AND Laplacian, both
            // must pass on the actually-delivered bytes, same as the ranking step.
            val finalScoreProvider = ImageDataProvider(
                croppedByteArray,
                croppedByteArraySize.width,
                croppedByteArraySize.height,
                bestFrame.rotationDegrees
            )
            val finalDenseNetResult = blurCheck.run(finalScoreProvider)
            val finalLaplacianResult = stage2LaplacianCheck.run(finalScoreProvider)
            val finalBlurConfidence = finalDenseNetResult.confidence
            Log.i(
                TAG,
                "FINAL_BLUR_RESCORE -- denseNet=$finalBlurConfidence laplacianPassed=${finalLaplacianResult.passed} (this IS the delivered image)"
            )
            Log.i(
                TAG,
                "BLUR_INPUT_SIZE -- crop before resize: ${croppedByteArraySize.width}x${croppedByteArraySize.height}"
            )
            // Final authoritative check — the delivered image itself must clear
            // BOTH thresholds, not just whichever candidate won the earlier ranking.
            if (finalBlurConfidence < blurThreshold || !finalLaplacianResult.passed) {
                Log.w(
                    TAG,
                    "STAGE2_REJECT -- Final delivered crop failed dual re-check: denseNet=$finalBlurConfidence laplacianPassed=${finalLaplacianResult.passed} (ranking-stage had suggested denseNet=${blurResults[blurSortedIndices.first()].denseNet.confidence})"
                )
                finalScoreProvider.clearCache()
                listener.onStage2Result(
                    passed = false,
                    errors = listOf(Error.Blur)
                )
                return
            }
            val finalBrightnessResult = stage1Methods[BRIGHTNESS_CHECK]!!.run(finalScoreProvider)
            val finalGlareResult = stage1Methods[GLARE_CHECK]!!.run(finalScoreProvider)
            finalScoreProvider.clearCache()
            val segmentedFrame = SegmentedFrame(
                processingId = processingId,
                finalBitmap = croppedBitmap,
                fullBitmap = fullBitmap,
                croppedBitmap = croppedBitmap,
                timestamp = bestFrame.timestamp,
                finalMask = null,
                blurScore = finalBlurConfidence,
                brightnessScore = finalBrightnessResult.confidence,
                glareScore = finalGlareResult.confidence
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

    private fun drawScoreOverlay(
        source: Bitmap,
        blur: Float,
        brightness: Float,
        glare: Float
    ): Bitmap {
        val overlay = source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(overlay)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.YELLOW
            textSize = overlay.width * 0.045f
            isAntiAlias = true
            setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
        }
        val lineHeight = paint.textSize * 1.3f
        var y = lineHeight
        canvas.drawText(
            "Strategy: ${preferenceStore.get(ProcessingSettings.CAPTURE_STRATEGY)}",
            20f,
            y,
            paint
        )
        y += lineHeight
        canvas.drawText(String.format("Blur: %.3f", blur), 20f, y, paint)
        y += lineHeight
        canvas.drawText(String.format("Brightness: %.3f", brightness), 20f, y, paint)
        y += lineHeight
        canvas.drawText(String.format("Glare: %.3f", glare), 20f, y, paint)
        return overlay
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