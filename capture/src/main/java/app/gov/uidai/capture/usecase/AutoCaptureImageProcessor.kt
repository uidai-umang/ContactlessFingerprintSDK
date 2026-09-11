package app.gov.uidai.capture.usecase

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.util.Size
import app.gov.uidai.capture.R
import app.gov.uidai.capture.domain.config.BrightnessConfig
import app.gov.uidai.capture.domain.config.GlareConfig
import app.gov.uidai.capture.domain.method.blur.LaplacianBlurMethod
import app.gov.uidai.capture.domain.model.CameraFrame
import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.domain.model.ImageProcessingMethod
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import app.gov.uidai.capture.utils.extension.crop
import app.gov.uidai.capture.utils.extension.rotate
import app.gov.uidai.capture.utils.extension.toBitmap

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
        LaplacianBlurMethod(minVariance = 300f)
    }

    private val stage2BlurChecks: List<ImageProcessingMethod<Unit>> by lazy {
        listOf(
            blurCheck
//        ,stage2LaplacianCheck
        )
    }

    /**
     * Bundles one candidate's results across every check in
     * [stage2BlurChecks], in the same order. [allPassed] is the ONLY
     * pass/fail decision made about blur in Stage 2 -- no separate
     * threshold comparison happens anywhere else.
     */
    private data class Stage2BlurResult(val results: List<ProcessingResult<Unit>>) {
        val allPassed: Boolean get() = results.all { it.passed }

        // Reporting/ranking confidence -- see stage2BlurChecks doc above
        // for the "index 0 is primary" convention.
        val primaryConfidence: Float get() = results.firstOrNull()?.confidence ?: 0f
    }

    /** One line per configured check, e.g. "DensenetBlurMethod=0.94(passed=true), LaplacianBlurMethod=310.2(passed=true)" -- for logging only. */
    private fun Stage2BlurResult.describe(): String =
        stage2BlurChecks.indices.joinToString(", ") { i ->
            "${stage2BlurChecks[i]::class.simpleName}=${results[i].confidence}(passed=${results[i].passed})"
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

            val imageDataProviders = candidateBatch.map { frame ->
                val (byteArray, byteArraySize) = frame.getByteArray(
                    requiresCropping = preferenceStore.get(
                        ProcessingSettings.CROPPED_INPUT_TO_BLUR_MODEL
                    ),
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

            val blurResults = withContext(blurExecutor.asCoroutineDispatcher()) {
                imageDataProviders.map { dataProvider ->
                    async {
                        Stage2BlurResult(
                            stage2BlurChecks.map { check ->
                                check.run(dataProvider)
                            }
                        )
                    }
                }.awaitAll()
            }

            val isBlurPassed = blurResults.any { it.allPassed }

            if (preferenceStore.get(ProcessingSettings.SAVE_BLUR_INPUT)) {
                imageDataProviders.forEachIndexed { i, dataProvider ->
                    val conf = blurResults[i].primaryConfidence
                    val confFormatted =
                        String.format("%.2f", conf).removePrefix("0")

                    controller.saveBitmap(
                        dataProvider.getAsUprightBitmap(),
                        "BlurInput($confFormatted)"
                    )
                }
            }

            imageDataProviders.forEach { it.clearCache() }

            if (!isBlurPassed) {
                Log.w(
                    TAG,
                    "STAGE2_REJECT -- Blur failed. Per-candidate: " +
                            blurResults.mapIndexed { i, r ->
                                "candidate$i=[${r.describe()}]"
                            }.joinToString(" | ")
                )

                listener.onStage2Result(
                    passed = false,
                    errors = listOf(Error.Blur)
                )
                return
            }

            // ----------------------------------------------------------------------
            // SEGMENTATION
            // ----------------------------------------------------------------------

            listener.onStage2ProcessingStageUpdate(ProcessingStage.SEGMENTATION)

            val blurSortedIndices = blurResults.indices
                .filter { blurResults[it].allPassed }
                .sortedByDescending {
                    blurResults[it].primaryConfidence
                }

            val bestFrame = candidateBatch[blurSortedIndices.first()]

            val (segCroppedByteArray, segCroppedByteArraySize) =
                bestFrame.getByteArray(
                    requiresCropping = preferenceStore.get(
                        ProcessingSettings.CROPPED_INPUT_TO_SEGMENTATION_MODEL
                    ),
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

            val segmentationResult = segmentationCheck.run(
                segmentationProvider
            )

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
                        errors = listOf(
                            segmentationResult.cause as Error
                        )
                    )
                    return
                }

                is ProcessingResult.Passed -> {

                    val boundingBox = segmentationResult.data.box

                    // IMPORTANT:
                    // Crop the ORIGINAL bitmap using the segmentation bounding box.
                    // This is what gives us the actual finger image containing
                    // the fingerprint ridges.
                    val finalBitmap = segmentationProvider
                        .getAsUprightBitmap()
                        .crop(boundingBox)

                    val (fullByteArray, fullByteArraySize) =
                        bestFrame.getByteArray(
                            requiresCropping = false,
                            cutoutRect = provider.getCutoutRectInImageCoordinates(
                                Size(bestFrame.width, bestFrame.height),
                                bestFrame.rotationDegrees
                            )
                        )

                    val fullBitmap = fullByteArray
                        .toBitmap(fullByteArraySize)
                        .rotate(bestFrame.rotationDegrees)

                    val (croppedByteArray, croppedByteArraySize) =
                        bestFrame.getByteArray(
                            requiresCropping = true,
                            cutoutRect = provider.getCutoutRectInImageCoordinates(
                                Size(bestFrame.width, bestFrame.height),
                                bestFrame.rotationDegrees
                            )
                        )

                    val croppedBitmap = croppedByteArray
                        .toBitmap(croppedByteArraySize)
                        .rotate(bestFrame.rotationDegrees)

                    if (preferenceStore.get(ProcessingSettings.SAVE_FINAL_OUTPUT)) {
                        controller.saveBitmap(
                            finalBitmap,
                            "FinalOutput"
                        )

                        segmentationResult.data.mask?.let {
                            controller.saveBitmap(
                                it,
                                "FinalOutputMask"
                            )
                        }
                    }

                    SegmentedFrame(
                        processingId = processingId,
                        finalBitmap = finalBitmap,
                        fullBitmap = fullBitmap,
                        croppedBitmap = croppedBitmap,
                        timestamp = bestFrame.timestamp,
                        finalMask = segmentationResult.data.mask
                    )
                }
            }

            segmentationProvider.clearCache()

            blurSortedIndices.forEach { _ ->
                addToFinalBuffer(segmentedFrame)
            }

            // ----------------------------------------------------------------------
            // FINGER DETECTION
            // ----------------------------------------------------------------------

            listener.onStage2ProcessingStageUpdate(
                ProcessingStage.FINGER_DETECTION
            )

            val finalScoreProvider = ImageDataProvider(
                segmentedFrame.croppedBitmap.let {
                    // Keep the existing final scoring input unchanged.
                    val (byteArray, size) = bestFrame.getByteArray(
                        requiresCropping = true,
                        cutoutRect = provider.getCutoutRectInImageCoordinates(
                            Size(bestFrame.width, bestFrame.height),
                            bestFrame.rotationDegrees
                        )
                    )
                    byteArray
                },
                segmentedFrame.croppedBitmap.width,
                segmentedFrame.croppedBitmap.height,
                bestFrame.rotationDegrees
            )

            val finalFingerCheckProvider = ImageDataProvider(
                bestFrame.getByteArray(
                    requiresCropping = false,
                    cutoutRect = provider.getCutoutRectInImageCoordinates(
                        Size(bestFrame.width, bestFrame.height),
                        bestFrame.rotationDegrees
                    )
                ).first,
                bestFrame.width,
                bestFrame.height,
                bestFrame.rotationDegrees
            )

            val (finalBlurResult, finalFingerResult) =
                withContext(blurExecutor.asCoroutineDispatcher()) {

                    val blurDeferreds = stage2BlurChecks.map { check ->
                        async {
                            check.run(finalScoreProvider)
                        }
                    }

                    val fingerDeferred = async {
                        mediapipeFinger.run(finalFingerCheckProvider)
                    }

                    Stage2BlurResult(
                        blurDeferreds.awaitAll()
                    ) to fingerDeferred.await()
                }

            val finalBlurConfidence =
                finalBlurResult.primaryConfidence

            Log.i(
                TAG,
                "FINAL_BLUR_RESCORE -- ${finalBlurResult.describe()} " +
                        "(this IS the delivered image)"
            )

            Log.i(
                TAG,
                "BLUR_INPUT_SIZE -- crop before resize: " +
                        "${segmentedFrame.croppedBitmap.width}x" +
                        "${segmentedFrame.croppedBitmap.height}"
            )

            Log.i(
                TAG,
                "FINAL_FINGER_RESCORE -- passed=${finalFingerResult.passed} " +
                        "confidence=${finalFingerResult.confidence} " +
                        "status=${(finalFingerResult as? ProcessingResult.Failed)?.status} " +
                        "(this IS the delivered image)"
            )

            if (preferenceStore.get(ProcessingSettings.SAVE_FINAL_OUTPUT)) {
                val passLabel =
                    if (finalFingerResult.passed) "PASS" else "FAIL"

                val confFormatted =
                    String.format(
                        "%.2f",
                        finalFingerResult.confidence
                    ).removePrefix("0")

                controller.saveBitmap(
                    finalFingerCheckProvider.getAsUprightBitmap(),
                    "FinalFingerCheckInput_$passLabel($confFormatted)"
                )
            }

            finalFingerCheckProvider.clearCache()

            if (!finalBlurResult.allPassed) {
                Log.w(
                    TAG,
                    "STAGE2_REJECT -- Final delivered crop failed " +
                            "blur re-check: ${finalBlurResult.describe()} " +
                            "(ranking-stage had suggested " +
                            "primaryConfidence=${blurResults[blurSortedIndices.first()].primaryConfidence})"
                )

                finalScoreProvider.clearCache()

                listener.onStage2Result(
                    passed = false,
                    errors = listOf(Error.Blur)
                )
                return
            }

            if (!finalFingerResult.passed) {
                Log.w(
                    TAG,
                    "STAGE2_REJECT -- Final delivered crop failed " +
                            "finger-presence re-check: " +
                            "confidence=${finalFingerResult.confidence}"
                )

                finalScoreProvider.clearCache()

                listener.onStage2Result(
                    passed = false,
                    errors = listOf(
                        Error.New(
                            titleRes = R.string.error_title_finger,
                            descriptionRes = R.string.error_desc_finger,
                            imageRes = R.drawable.ic_android_black_24dp,
                            processingStage = ProcessingStage.FINGER_DETECTION
                        )
                    )
                )
                return
            }

            val (finalBrightnessResult, finalGlareResult) =
                coroutineScope {

                    val brightnessDeferred = async {
                        stage1Methods[BRIGHTNESS_CHECK]!!
                            .run(finalScoreProvider)
                    }

                    val glareDeferred = async {
                        stage1Methods[GLARE_CHECK]!!
                            .run(finalScoreProvider)
                    }

                    brightnessDeferred.await() to glareDeferred.await()
                }

            finalScoreProvider.clearCache()

            val finalSegmentedFrame = segmentedFrame.copy(
                blurScore = finalBlurConfidence,
                brightnessScore = finalBrightnessResult.confidence,
                glareScore = finalGlareResult.confidence
            )

            stopCaptureTimer()

            listener.onStage2Result(
                passed = true,
                listOf()
            )

        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error in Stage 2 processing",
                e
            )

            listener.onStage2Result(
                passed = false,
                listOf(Error.SomethingWentWrong)
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
        val canvas = Canvas(overlay)
        val paint = Paint().apply {
            color = Color.YELLOW
            textSize = overlay.width * 0.045f
            isAntiAlias = true
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
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