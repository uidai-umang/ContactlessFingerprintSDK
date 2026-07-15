package app.gov.uidai.capture.usecase

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.RectF
import android.media.ImageReader
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.lifecycle.LifecycleCoroutineScope
import app.gov.uidai.capture.domain.config.BlurSettings
import app.gov.uidai.capture.domain.config.BrightnessConfig
import app.gov.uidai.capture.domain.config.BrightnessSettings
import app.gov.uidai.capture.domain.config.FingerSettings
import app.gov.uidai.capture.domain.config.GlareConfig
import app.gov.uidai.capture.domain.config.GlareSettings
import app.gov.uidai.capture.domain.config.LaplacianBlurSettings
import app.gov.uidai.capture.domain.method.blur.LaplacianBlurMethod
import app.gov.uidai.capture.domain.method.brightness.BrightnessCheck
import app.gov.uidai.capture.domain.method.finger.FingerCheckPythonMethod
import app.gov.uidai.capture.domain.method.glare.GlareCheck
import app.gov.uidai.capture.domain.model.CameraFrame
import app.gov.uidai.capture.domain.model.CaptureStrategyType
import app.gov.uidai.capture.domain.model.FingerResultData
import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.domain.model.ImageProcessingMethod
import app.gov.uidai.capture.domain.model.LiveCheckScore
import app.gov.uidai.capture.domain.model.LiveQualityScores
import app.gov.uidai.capture.domain.model.ProcessingResult
import app.gov.uidai.capture.domain.model.ProcessingStage
import app.gov.uidai.capture.domain.model.SegmentedFrame
import app.gov.uidai.capture.pref.PreferenceStore
import app.gov.uidai.capture.ui.camera.mapper.toUiFailureCause
import app.gov.uidai.capture.ui.camera.model.Error
import app.gov.uidai.capture.ui.camera.model.Stage2ResultValue
import app.gov.uidai.capture.ui.camera.model.Warning
import app.gov.uidai.capture.usecase.factory.BlurCheckFactory
import app.gov.uidai.capture.usecase.factory.FingerCheckFactory
import app.gov.uidai.capture.usecase.factory.SegmentationFactory
import app.gov.uidai.capture.utils.RollingConfidence
import app.gov.uidai.capture.utils.extension.toByteArray
import app.gov.uidai.capture.utils.logExecutionTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

/**
 * Buffer-less Image Processor that always processes the latest available frame
 *
 * Key Features:
 * - No buffering between stages
 * - Pull-based processing (stages request latest frame when ready)
 * - Zero memory overhead for frame queuing
 * - Always processes fresh data
 */
abstract class ImageProcessor(
    protected val coroutineScope: LifecycleCoroutineScope,
    protected val preferenceStore: PreferenceStore,
    protected val segmentationFactory: SegmentationFactory,
    protected val fingerCheckFactory: FingerCheckFactory,
    protected val blurCheckFactory: BlurCheckFactory,
    protected val glareConfig: GlareConfig,
    protected val brightnessConfig: BrightnessConfig,
    protected val provider: Provider,
    protected val controller: Controller,
    protected val listener: Listener
) : ImageReader.OnImageAvailableListener {
    companion object {
        private val TAG = ImageProcessor::class.java.simpleName
        private const val REQUIRED_SUCCESSFUL_IMAGES = 1
        internal const val GLARE_CHECK = "GlareCheck"
        internal const val BRIGHTNESS_CHECK = "BrightnessCheck"
        internal const val LIVENESS_CHECK = "LivenessCheck"
    }

    // Processing methods
    protected val stage1Methods: Map<String, ImageProcessingMethod<*>> = mapOf(
        GLARE_CHECK to GlareCheck(glareConfig),
        BRIGHTNESS_CHECK to BrightnessCheck(brightnessConfig)
    )

    private val fingerCheck by lazy {
        fingerCheckFactory.create(
            getCutoutRect = provider::getCutoutRectInImageCoordinates,
            getPreviewSize = provider::previewSize
        )
    }

    protected val blurCheck by lazy {
        blurCheckFactory.create()
    }

    private val liveBlurCheck by lazy {
        LaplacianBlurMethod(minVariance = preferenceStore.get(LaplacianBlurSettings.MIN_VARIANCE))
    }

    protected val segmentationCheck by lazy {
        segmentationFactory.create()
    }

    // Capture strategy — selects which combination of gate logic, finger
    // inclusion, live blur engine, and accumulation delay is active.
    // StableStrategy mirrors original behavior; FastStrategy is today's
    // validated, optimized result. See CaptureStrategyType.
    protected data class CaptureStrategyConfig(
        val useRollingConfidence: Boolean,
        val includeFingerInGate: Boolean,
        val liveBlur: ImageProcessingMethod<Unit>,
        val accumulationDelayMs: Long
    )

    protected val strategyConfig: CaptureStrategyConfig by lazy {
        when (preferenceStore.get(ProcessingSettings.CAPTURE_STRATEGY)) {
            CaptureStrategyType.StableStrategy -> CaptureStrategyConfig(
                useRollingConfidence = false,
                includeFingerInGate = true,
                liveBlur = blurCheck,
                accumulationDelayMs = 1_250L
            )
            CaptureStrategyType.FastStrategy -> CaptureStrategyConfig(
                useRollingConfidence = true,
                includeFingerInGate = false,
                liveBlur = liveBlurCheck,
                accumulationDelayMs = 450L
            )
        }
    }

    // Latest frame holders - no buffering, just latest reference
    private val latestRawFrame0 = AtomicReference<CameraFrame?>()
    private val latestRawFrame1 = AtomicReference<CameraFrame?>()
    private val latestRawFrame2 = AtomicReference<CameraFrame?>()
    private val latestRawFrame3 = AtomicReference<CameraFrame?>()

    private val capturedFrameBuffer = Collections.synchronizedList(
        mutableListOf<CameraFrame>()
    )
    private val _capturedFrameFlow = MutableStateFlow<List<CameraFrame>?>(null)
    private val capturedFrameFlow = _capturedFrameFlow.asStateFlow()

    // Processing state tracking
    private val processingCounter = AtomicLong(0)
    private val finalBuffer = Collections.synchronizedList(
        mutableListOf<SegmentedFrame>()
    )

    // Processing flags
    private val isCollectingImage = AtomicBoolean(false)
    private val isStage1Processing = AtomicBoolean(false)
    private val isBlurProcessing = AtomicBoolean(false)
    private val isFingerProcessing = AtomicBoolean(false)
    private val isAccumulatingFrames = AtomicBoolean(false)
    private val isStage2Processing = AtomicBoolean(false)

    private val fingerResult = AtomicReference<ProcessingResult<FingerResultData>?>()
    private val isBlurPassed = AtomicBoolean(false)
    private val isCaptured = AtomicBoolean(false)
    protected val isStage1Passed = AtomicBoolean(false)

    private val lastBlurConfidence = AtomicReference(0f)
    private val blurConfidence = RollingConfidence(windowSize = 5, requiredPassRate = 0.7f)
    private val fingerConfidence = RollingConfidence(windowSize = 4, requiredPassRate = 0.60f)
    private val glareConfidence = RollingConfidence(windowSize = 10, requiredPassRate = 0.9f)
    private val brightnessConfidence = RollingConfidence(windowSize = 10, requiredPassRate = 0.7f)

    private val stage1PassedTime = AtomicReference<Long?>(null)

    abstract val DELAY_IN_ACCUMULATION_OF_FRAMES: Long
    abstract val isReadyForAccumulation: Boolean

    private val stage1Dispatcher = Executors.newFixedThreadPool(3).asCoroutineDispatcher()

    init {
        startProcessingLoop()
        Log.i(TAG, "Image Processor Initialized")
    }

    private var stage1Job: Job? = null
    private var fingerCheckJob: Job? = null
    private var blurCheckJob: Job? = null
    private var accumulatorJob: Job? = null
    private var stage2Job: Job? = null

    private val captureStartTime = AtomicLong(0L)

    protected fun startCaptureTimer() {
        captureStartTime.compareAndSet(0L, SystemClock.elapsedRealtime())
    }

    protected fun stopCaptureTimer() {
        val elapsed = SystemClock.elapsedRealtime() - captureStartTime.get()
        Log.i(
            "CAPTURE_BENCHMARK",
            "✅ Capture completed in ${elapsed} ms (${elapsed / 1000.0}s)"
        )
        captureStartTime.set(0L)
    }

    /**
     * Start the processing loop that continuously pulls latest frames for processing
     */
    private fun startProcessingLoop() {
        // Stage 1: Image analysis
        stage1Job = coroutineScope.launch(Dispatchers.Default) {
            while (true) {
                val frame = latestRawFrame0.getAndSet(null)
                val startTime = System.currentTimeMillis()
                if (
                    frame != null &&
                    !isCaptured.get() &&
                    isStage1Processing.compareAndSet(false, true)
                ) {
                    try {
                        ensureActive()
                        logExecutionTime(TAG, "STAGE.1") {
                            processStage1(frame)
                        }
                    } finally {
                        isStage1Processing.set(false)
                    }
                }
                delay(max(1, 33 - (System.currentTimeMillis() - startTime))) // 30 fps
            }
        }

        fingerCheckJob = coroutineScope.launch(Dispatchers.Default) {
            while (true) {
                val frame = latestRawFrame3.getAndSet(null)
                val startTime = System.currentTimeMillis()
                if (
                    frame != null &&
                    !isCaptured.get() &&
                    isFingerProcessing.compareAndSet(false, true)
                ) {
                    try {
                        logExecutionTime(TAG, "FingerCheck") {
                            processFinger(frame)
                        }
                    } finally {
                        isFingerProcessing.set(false)
                    }
                }
                delay(max(1, 33 - (System.currentTimeMillis() - startTime))) // 30 fps
            }
        }

        blurCheckJob = coroutineScope.launch(Dispatchers.Default) {
            while (true) {
                val frame = latestRawFrame2.getAndSet(null)
                val startTime = System.currentTimeMillis()
                if (
                    frame != null &&
                    !isCaptured.get() &&
                    isBlurProcessing.compareAndSet(false, true)
                ) {
                    try {
                        logExecutionTime(TAG, "Blur") {
                            processBlur(frame)
                        }
                    } finally {
                        isBlurProcessing.set(false)
                    }
                }
                delay(max(1, 33 - (System.currentTimeMillis() - startTime))) // 30 fps
            }
        }

        // Accumulation of image for next stage
        accumulatorJob = coroutineScope.launch(Dispatchers.Default) {
            while (true) {
                val frame = latestRawFrame1.getAndSet(null)
                val startTime = System.currentTimeMillis()
                if (frame != null) {
                    Log.d(
                        "ACCUM_DEBUG",
                        "frame!=null=true isCaptured=${isCaptured.get()} " +
                                "isReadyForAccumulation=$isReadyForAccumulation " +
                                "isAccumulating=${isAccumulatingFrames.get()}"
                    )
                }
                if (
                    frame != null &&
                    !isCaptured.get() &&
                    isReadyForAccumulation &&
                    isAccumulatingFrames.compareAndSet(false, true)
                ) {
                    try {
                        Log.d("ACCUM_DEBUG", "ENTERED accumulation block — calling onStartAccumulation")
                        listener.onStartAccumulation()
                        accumulateFrameForStage2(frame)
                    } finally {
                        isAccumulatingFrames.set(false)
                    }
                }
                delay(max(1, 33 - (System.currentTimeMillis() - startTime))) // 30 fps
            }
        }

        // Stage 2: Process latest candidate frame batch
        stage2Job = coroutineScope.launch(Dispatchers.Default) {
            capturedFrameFlow.collectLatest { it ->
                if (
                    it != null &&
                    getFinalBufferSize() < REQUIRED_SUCCESSFUL_IMAGES &&
                    isStage2Processing.compareAndSet(false, true)
                ) {
                    try {
                        Log.d("FLOW_TRACE", "4. processStage2() starting now")
                        listener.onStartStage2Processing()
                        logExecutionTime(TAG, "STAGE.2") {
                            processStage2(it)
                        }
                    } finally {
                        isStage2Processing.set(false)
                        listener.onStopStage2Processing()
                    }
                }
            }
        }
    }

    /**
     * Stage 1 Processing
     */
    private suspend fun processStage1(frame: CameraFrame) = coroutineScope {
        val processingId = frame.processingId
        Log.d(
            TAG,
            "Processing Stage 1 for frame #$processingId (timestamp: ${frame.timestamp})"
        )
        try {
            // Check if we should skip this frame (too old)
            val currentTime = System.currentTimeMillis()
            if (currentTime - frame.timestamp > 500) { // Skip frames older than 500ms
                Log.d(
                    TAG,
                    "Skipping old frame #$processingId (age: ${currentTime - frame.timestamp}ms)"
                )
                return@coroutineScope
            }

            // Get cutout rectangle for focused lighting analysis
            val cutoutRect = provider.getCutoutRectInImageCoordinates(
                Size(frame.width, frame.height),
                frame.rotationDegrees
            )
            val (croppedByteArray, croppedByteArraySize) = frame.getByteArray(
                requiresCropping = true,
                cutoutRect = cutoutRect
            )
            val imageDataProvider = ImageDataProvider(
                croppedByteArray,
                croppedByteArraySize.width,
                croppedByteArraySize.height,
                frame.rotationDegrees
            )

            if (preferenceStore.get(ProcessingSettings.SAVE_STAGE1_IMAGE)) {
                controller.saveBitmap(imageDataProvider.getAsBitmap(), "Stage1Img")
            }
            if (preferenceStore.get(ProcessingSettings.SAVE_STAGE1_UPRIGHT_IMAGE)) {
                controller.saveBitmap(
                    imageDataProvider.getAsUprightBitmap(),
                    "Stage1UpImg"
                )
            }

            val deferredResults = stage1Methods.map { (name, method) ->
                name to async(stage1Dispatcher) {
                    method.run(imageDataProvider)
                }
            }.toMap()

            val results = deferredResults.mapValues { (_, deferred) -> deferred.await() }
            imageDataProvider.clearCache()

            // Warnings and Passed Processing Stages
            val warnings = mutableListOf<Warning>()
            val passedProcessingStages = mutableListOf<ProcessingStage>()

            results.forEach { (name, result) ->
                when (result) {
                    is ProcessingResult.Passed<*> -> {
                        when (name) {
                            GLARE_CHECK -> {
                                passedProcessingStages.add(ProcessingStage.GLARE)
                                glareConfidence.record(true)
                            }
                            BRIGHTNESS_CHECK -> {
                                passedProcessingStages.add(ProcessingStage.BRIGHTNESS)
                                brightnessConfidence.record(true)
                            }
                            LIVENESS_CHECK -> passedProcessingStages.add(ProcessingStage.LIVENESS)
                        }
                    }
                    is ProcessingResult.Failed -> {
                        val cause = result.cause.toUiFailureCause()
                        warnings.add(cause.toWarning())
                        when (name) {
                            GLARE_CHECK -> glareConfidence.record(false)
                            BRIGHTNESS_CHECK -> brightnessConfidence.record(false)
                        }
                    }
                }
            }

            when (isBlurPassed.get()) {
                true -> passedProcessingStages.add(ProcessingStage.BLUR)
                false -> warnings.add(Warning.Blur)
            }

            val fingerResult = fingerResult.get()
            val isFingerPassed = fingerResult?.passed ?: false
            fingerResult?.let {
                when (it) {
                    is ProcessingResult.Passed -> passedProcessingStages.add(ProcessingStage.FINGER_DETECTION)
                    is ProcessingResult.Failed -> warnings.add(it.cause.toUiFailureCause().toWarning())
                }
            } ?: {
                warnings.add(Warning.NoFinger)
            }

            // Check Focus Lock
            if (!provider.isFocusLockedForCapture) {
                warnings.add(Warning.FocusNotLocked)
            } else {
                passedProcessingStages.add(ProcessingStage.NA)
            }

            // Unified gate — behavior fully determined by strategyConfig.
            // StableStrategy: instantaneous per-frame AND, finger included.
            // FastStrategy: rolling-confidence windows, finger excluded
            // (still tracked/recorded for the debug panel and future
            // reinstatement once a lightweight live finger model exists).
            val isStage1PassedGate = if (strategyConfig.useRollingConfidence) {
                blurConfidence.isConfident() &&
                        (!strategyConfig.includeFingerInGate || fingerConfidence.isConfident()) &&
                        glareConfidence.isConfident() &&
                        brightnessConfidence.isConfident() &&
                        provider.isFocusLockedForCapture
            } else {
                results.values.all { it.passed } &&
                        isBlurPassed.get() &&
                        (!strategyConfig.includeFingerInGate || isFingerPassed) &&
                        provider.isFocusLockedForCapture
            }

            isStage1Passed.set(isStage1PassedGate)

            Log.d(
                "ROLLING",
                "Blur=${blurConfidence.isConfident()} " +
                        "Brightness=${brightnessConfidence.isConfident()} " +
                        "Glare=${glareConfidence.isConfident()} " +
                        "Finger=${fingerConfidence.isConfident()} " +
                        "Focus=${provider.isFocusLockedForCapture}"
            )

            if (isStage1PassedGate) {
                stage1PassedTime.compareAndSet(null, SystemClock.uptimeMillis())
            } else {
                listener.onStage1Error()
                stage1PassedTime.set(null)
                synchronized(capturedFrameBuffer) { capturedFrameBuffer.clear() }
            }

            val glareResult = results[GLARE_CHECK]
            val brightnessResult = results[BRIGHTNESS_CHECK]
            val currentFingerResult = fingerResult

            val liveScores = LiveQualityScores(
                blur = LiveCheckScore(
                    label = "Blur",
                    currentValue = lastBlurConfidence.get(),
                    acceptedMin = preferenceStore.get(BlurSettings.THRESHOLD),
                    acceptedMax = 1.0f,
                    passed = isBlurPassed.get()
                ),
                brightness = LiveCheckScore(
                    label = "Brightness",
                    currentValue = brightnessResult?.confidence ?: 0f,
                    // Confidence is a pixel-ratio fraction (dark_ratio/bright_ratio, or their
                    // average on pass) — lower is better, unlike blur/glare. There isn't a
                    // single clean pass/fail bound (dark and bright each have their own percent
                    // threshold), so this is the closest single-range approximation.
                    acceptedMin = 0f,
                    acceptedMax = max(
                        preferenceStore.get(BrightnessSettings.DARK_PERCENT),
                        preferenceStore.get(BrightnessSettings.BRIGHT_PERCENT)
                    ),
                    passed = brightnessResult?.passed ?: false
                ),
                glare = LiveCheckScore(
                    label = "Glare",
                    currentValue = glareResult?.confidence ?: 0f,
                    // confidence is normalized to 0-1 (variance / maxGlareValue), so the
                    // accepted range must be normalized the same way to stay on the same scale.
                    acceptedMin = preferenceStore.get(GlareSettings.VARIANCE_MIN)
                        .toFloat() / preferenceStore.get(GlareSettings.MAX_GLARE_VALUE),
                    acceptedMax = preferenceStore.get(GlareSettings.VARIANCE_MAX)
                        .toFloat() / preferenceStore.get(GlareSettings.MAX_GLARE_VALUE),
                    passed = glareResult?.passed ?: false
                ),
                fingerDetected = LiveCheckScore(
                    label = "Finger Detected",
                    currentValue = currentFingerResult?.confidence ?: 0f,
                    // HSV method reports a continuous area-ratio confidence; the default
                    // Mediapipe method reports an effectively binary 0f/1f signal.
                    acceptedMin = if (fingerCheck is FingerCheckPythonMethod)
                        preferenceStore.get(FingerSettings.GOOD_AREA_MIN) else 1f,
                    acceptedMax = if (fingerCheck is FingerCheckPythonMethod)
                        preferenceStore.get(FingerSettings.GOOD_AREA_MAX) else 1f,
                    passed = currentFingerResult?.passed ?: false
                )
            )
            listener.onStage1ResultValues(liveScores)

            listener.onStage1Result(
                passed = isStage1Passed.get(),
                warnings = warnings,
                passedChecks = passedProcessingStages
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in Stage 1 processing", e)
        }
    }

    private suspend fun processFinger(frame: CameraFrame) {
        try {
            // Get cutout rectangle for focused lighting analysis
            val cutoutRect = provider.getCutoutRectInImageCoordinates(
                Size(frame.width, frame.height),
                frame.rotationDegrees
            )
            val (byteArray, byteArraySize) = frame.getByteArray(
                requiresCropping = fingerCheck is FingerCheckPythonMethod,
                cutoutRect = cutoutRect
            )
            val imageDataProvider = ImageDataProvider(
                byteArray,
                byteArraySize.width,
                byteArraySize.height,
                frame.rotationDegrees
            )

            if (preferenceStore.get(ProcessingSettings.SAVE_FINGER_CHECK_INPUT)) {
                controller.saveBitmap(
                    imageDataProvider.getAsBitmap(),
                    "FingerCheckInput"
                )
            }

            val fingerResult = logExecutionTime(TAG, "FingerCheck.ActualRun") {
                runInterruptible {
                    fingerCheck.run(imageDataProvider)
                }
            }
            imageDataProvider.clearCache()

            when (fingerResult) {
                is ProcessingResult.Passed -> {
                    val data = fingerResult.data
                    listener.onFingerMaskResult(data.mask, imageDataProvider.rotationDegrees)
                    if (!isCaptured.get()) {
                        controller.triggerFocusLock(
                            data.box,
                            cutoutRect,
                            Size(frame.width, frame.height)
                        )
                    }
                }
                is ProcessingResult.Failed -> {
                    fingerResult.data?.let { data ->
                        listener.onFingerMaskResult(null, imageDataProvider.rotationDegrees)
                        if (!isCaptured.get()) {
                            controller.triggerFocusLock(
                                data.box,
                                cutoutRect,
                                Size(frame.width, frame.height)
                            )
                        }
                    }
                }
            }
            this.fingerResult.set(fingerResult)
            fingerConfidence.record(fingerResult.passed)
            Log.d("FINGER_DEBUG", "fingerResult.passed=${fingerResult.passed}, raw result=$fingerResult")
        } catch (e: Exception) {
            Log.e(TAG, "FINGER -- Error in Finger Check processing", e)
        }
    }

    @SuppressLint("DefaultLocale")
    private suspend fun processBlur(frame: CameraFrame) {
        try {
            // Get cutout rectangle for focused lighting analysis
            val cutoutRect = provider.getCutoutRectInImageCoordinates(
                Size(frame.width, frame.height),
                frame.rotationDegrees
            )
            val (croppedByteArray, croppedByteArraySize) = frame.getByteArray(
                requiresCropping = true,
                cutoutRect = cutoutRect
            )
            val imageDataProvider = ImageDataProvider(
                croppedByteArray,
                croppedByteArraySize.width,
                croppedByteArraySize.height,
                frame.rotationDegrees
            )

            val blurResult = runInterruptible {
                strategyConfig.liveBlur.run(imageDataProvider)
            }

            if (preferenceStore.get(ProcessingSettings.SAVE_BLUR_INPUT)) {
                val conf = blurResult.confidence
                val confFormatted = String.format("%.2f", conf).removePrefix("0")
                controller.saveBitmap(
                    imageDataProvider.getAsUprightBitmap(),
                    "BlurInput($confFormatted)"
                )
            }
            if (preferenceStore.get(ProcessingSettings.SAVE_SHARP_IMAGES)) {
                val conf = blurResult.confidence
                val confFormatted = String.format("%.2f", conf).removePrefix("0")
                if (conf >= preferenceStore.get(BlurSettings.THRESHOLD)) {
                    controller.saveBitmap(
                        imageDataProvider.getAsUprightBitmap(),
                        "SharpImage($confFormatted)"
                    )
                }
            }

            imageDataProvider.clearCache()
            isBlurPassed.set(blurResult.passed)
            lastBlurConfidence.set(blurResult.confidence)
            blurConfidence.record(blurResult.passed)
        } catch (e: Exception) {
            Log.e(TAG, "Error in Blur processing", e)
        }
    }

    /**
     * Frame Accumulation for Stage 2
     */
    private fun accumulateFrameForStage2(frame: CameraFrame) {
        val processingId = frame.processingId
        try {
            // Check if we should skip this frame (too old)
            val currentTime = System.currentTimeMillis()
            if (currentTime - frame.timestamp > 500) { // Skip frames older than 500ms
                Log.d(
                    TAG,
                    "Skipping old frame (age: ${currentTime - frame.timestamp}ms)"
                )
                return
            }

            val timePassedAfterPreCaptureSuccess = stage1PassedTime.get()?.let {
                SystemClock.uptimeMillis() - it
            } ?: 0L
            if (timePassedAfterPreCaptureSuccess < DELAY_IN_ACCUMULATION_OF_FRAMES) {
                Log.d(
                    TAG,
                    "Skipping accumulator frame (time passed after success: ${timePassedAfterPreCaptureSuccess})"
                )
                return
            }

            synchronized(capturedFrameBuffer) {
                capturedFrameBuffer.add(frame)
            }
            Log.d(
                TAG,
                "Added candidate frame #${processingId}. Batch size: ${capturedFrameBuffer.size}"
            )

            synchronized(capturedFrameBuffer) {
                val isEnoughFrames =
                    capturedFrameBuffer.size >= preferenceStore.get(ProcessingSettings.IMAGE_COUNT_FOR_STAGE2)
                if (isEnoughFrames) {
                    if (isCaptured.compareAndSet(false, true)) {
                        Log.d("FLOW_TRACE", "2. isCaptured flipped true — firing controller.triggerCapture()")
                        controller.triggerCapture()
                    }
                    val batch = synchronized(capturedFrameBuffer) {
                        val batchToProcess = capturedFrameBuffer.toList()
                        capturedFrameBuffer.clear()
                        batchToProcess
                    }
                    _capturedFrameFlow.update { batch }
                    Log.d("FLOW_TRACE", "3. Batch of ${batch.size} frames dispatched to Stage 2 flow — processStage2 about to start")
                    Log.i(TAG, "Dispatched a batch of ${batch.size} frames to Stage 2.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in accumulator", e)
        }
    }

    /**
     * Stage 2 Processing
     */
    abstract suspend fun processStage2(candidateBatch: List<CameraFrame>)

    override fun onImageAvailable(reader: ImageReader) {
        if (isCollectingImage.compareAndSet(false, true)) {
            try {
                val image = reader.acquireLatestImage() ?: return
                // Start timing only once for this capture session
                startCaptureTimer()
                val processingId = processingCounter.incrementAndGet()
                image.use {
                    val yRowStride = it.planes[0].rowStride
                    val byteArray = it.toByteArray()
                    val cameraFrame = CameraFrame(
                        processingId = processingId,
                        byteArray = byteArray,
                        width = it.width,
                        height = it.height,
                        timestamp = System.currentTimeMillis(),
                        rotationDegrees = provider.totalRotation,
                        yRowStride = yRowStride
                    )
                    latestRawFrame3.set(cameraFrame)
                    latestRawFrame2.set(cameraFrame)
                    latestRawFrame1.set(cameraFrame)
                    latestRawFrame0.set(cameraFrame)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to acquire image: ${e.message}")
            } finally {
                isCollectingImage.set(false)
            }
        }
    }

    /**
     * Add processed image to final buffer with best-N selection
     */
    internal fun addToFinalBuffer(image: SegmentedFrame) {
        synchronized(finalBuffer) {
            finalBuffer.add(image)
            Log.d(TAG, "Final buffer size: ${finalBuffer.size}")
            if (finalBuffer.size >= REQUIRED_SUCCESSFUL_IMAGES) {
                Log.i(TAG, "Collection complete! ${finalBuffer.size} high-quality images collected")
            }
        }
    }

    private fun getFinalBufferSize(): Int = synchronized(finalBuffer) { finalBuffer.size }

    // Public interface methods
    abstract fun unlockAccumulator()

    fun getFinalFrame(): SegmentedFrame =
        synchronized(finalBuffer) { finalBuffer.first() }

    open fun reset() {
        latestRawFrame0.set(null)
        latestRawFrame1.set(null)
        latestRawFrame2.set(null)
        latestRawFrame3.set(null)
        synchronized(finalBuffer) { finalBuffer.clear() }
        synchronized(capturedFrameBuffer) { capturedFrameBuffer.clear() }
        _capturedFrameFlow.update { null }
        isStage1Passed.set(false)
        stage1PassedTime.set(null)
        isBlurPassed.set(false)
        isCaptured.set(false)
        fingerResult.set(null)
        Log.i(TAG, "reset()")
    }

    open fun close() {
        stage1Job?.cancel()
        fingerCheckJob?.cancel()
        blurCheckJob?.cancel()
        accumulatorJob?.cancel()
        stage2Job?.cancel()
        stage1Dispatcher.close()
        Log.i(TAG, "close()")
    }

    interface Provider {
        val totalRotation: Int
        val isFocusLockedForCapture: Boolean
        val previewSize: Size
        fun getCutoutRectInImageCoordinates(imageSize: Size, rotation: Int): RectF
    }

    interface Controller {
        fun triggerFocusLock(fingerRect: RectF, cutoutRect: RectF, imageSize: Size)
        fun triggerFocusUnlock()
        fun triggerCapture()
        suspend fun saveBitmap(bitmap: Bitmap, fileName: String): Uri
    }

    interface Listener {
        fun onFingerMaskResult(mask: Bitmap?, rotation: Int)
        fun onStartAccumulation()
        fun onStage1Error()
        fun onStage1Result(passed: Boolean, warnings: List<Warning>, passedChecks: List<ProcessingStage>)
        fun onStage1ResultValues(values: LiveQualityScores)
        fun onStartStage2Processing()
        fun onStopStage2Processing()
        fun onStage2ProcessingStageUpdate(processingStage: ProcessingStage)
        fun onStage2ResultValues(stage2ResultValue: Stage2ResultValue)
        fun onStage2Result(passed: Boolean, errors: List<Error>)
    }
}