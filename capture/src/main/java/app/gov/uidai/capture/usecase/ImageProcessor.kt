package app.gov.uidai.capture.usecase

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.RectF
import android.media.ImageReader
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.util.Size
import app.gov.uidai.capture.domain.config.BlurSettings
import app.gov.uidai.capture.domain.config.BrightnessConfig
import app.gov.uidai.capture.domain.config.BrightnessSettings
import app.gov.uidai.capture.domain.config.FingerSettings
import app.gov.uidai.capture.domain.config.GlareConfig
import app.gov.uidai.capture.domain.config.GlareSettings
import app.gov.uidai.capture.domain.config.LaplacianBlurSettings
import app.gov.uidai.capture.domain.config.LiveCheckSettings
import app.gov.uidai.capture.domain.method.blur.LaplacianBlurMethod
import app.gov.uidai.capture.domain.method.brightness.BrightnessCheck
import app.gov.uidai.capture.domain.method.finger.FingerCheckPythonMethod
import app.gov.uidai.capture.domain.method.glare.GlareCheck
import app.gov.uidai.capture.domain.model.CameraFrame
import app.gov.uidai.capture.domain.model.CaptureStrategyType
import app.gov.uidai.capture.domain.model.FingerResultData
import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.domain.model.ImageProcessingMethod
import app.gov.uidai.capture.domain.model.LiveBlurMethodType
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
import app.gov.uidai.capture.utils.BlurGate
import app.gov.uidai.capture.utils.RollingConfidence
import app.gov.uidai.capture.utils.extension.toByteArray
import app.gov.uidai.capture.utils.logExecutionTime
import kotlinx.coroutines.CoroutineScope
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
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

abstract class ImageProcessor(
    protected val coroutineScope: CoroutineScope,
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

    protected val stage1Methods: Map<String, ImageProcessingMethod<*>> = mapOf(
        GLARE_CHECK to GlareCheck(glareConfig),
        BRIGHTNESS_CHECK to BrightnessCheck(brightnessConfig)
    )

    // DenseNet instance — used by Stage 2's dedicated re-verify (always,
    // regardless of experiment) AND as one of the live-blur options below.
    protected val blurCheck by lazy {
        blurCheckFactory.create()
    }

    private val laplacianBlurCheck by lazy {
        LaplacianBlurMethod(minVariance = preferenceStore.get(LaplacianBlurSettings.MIN_VARIANCE))
    }

    protected val segmentationCheck by lazy {
        segmentationFactory.create()
    }

    // Per-strategy check configuration. Rolling-vs-instantaneous stays
    // strategy-wide; model choice for blur/finger is independently
    // configurable per strategy via Debug Settings, letting heavy/light
    // models be tested against either gating mechanism.
    protected data class CaptureStrategyConfig(
        val useRollingConfidence: Boolean,
        val accumulationDelayMs: Long
    )

    // Model resolution is now flat — read ONCE, shared by whichever
// strategy is active. Rolling vs instantaneous is the only thing
// strategyConfig still varies by strategy.
    private fun resolveLiveBlur(): ImageProcessingMethod<Unit> {
        return when (preferenceStore.get(LiveCheckSettings.LIVE_BLUR_MODEL)) {
            LiveBlurMethodType.Densenet, LiveBlurMethodType.NewDensenet -> blurCheck
            LiveBlurMethodType.Laplacian -> laplacianBlurCheck
        }
    }

    private fun resolveLiveFinger(): ImageProcessingMethod<FingerResultData> {
        return fingerCheckFactory.create(
            getCutoutRect = provider::getCutoutRectInImageCoordinates,
            getPreviewSize = provider::previewSize,
            methodOverride = preferenceStore.get(LiveCheckSettings.LIVE_FINGER_MODEL)
        )
    }

    protected val strategyConfig: CaptureStrategyConfig by lazy {
        when (preferenceStore.get(ProcessingSettings.CAPTURE_STRATEGY)) {
            CaptureStrategyType.StableStrategy -> CaptureStrategyConfig(
                useRollingConfidence = false,
                accumulationDelayMs = 1_250L
            )

            CaptureStrategyType.FastStrategy -> CaptureStrategyConfig(
                useRollingConfidence = true,
                accumulationDelayMs = 450L
            )
        }
    }

    private val blurGate = BlurGate(
        targetThreshold = 370f,
        fallbackThreshold = 330f,
        maxWaitMs = 3_000L
    )

    protected val liveBlur: ImageProcessingMethod<Unit> by lazy { resolveLiveBlur() }
    protected val liveFinger: ImageProcessingMethod<FingerResultData> by lazy { resolveLiveFinger() }

    private val latestRawFrame0 = AtomicReference<CameraFrame?>()
    private val latestRawFrame1 = AtomicReference<CameraFrame?>()
    private val latestRawFrame2 = AtomicReference<CameraFrame?>()
    private val latestRawFrame3 = AtomicReference<CameraFrame?>()

    private val capturedFrameBuffer = Collections.synchronizedList(mutableListOf<CameraFrame>())
    private val _capturedFrameFlow = MutableStateFlow<List<CameraFrame>?>(null)
    private val capturedFrameFlow = _capturedFrameFlow.asStateFlow()

    private val processingCounter = AtomicLong(0)
    private val finalBuffer = Collections.synchronizedList(mutableListOf<SegmentedFrame>())

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

    // Shared trackers — same window/rate regardless of which model is
    // plugged in for that check. Finger's window is deliberately small
    // (4 samples) since Mediapipe can cost ~2-2.5s/call; a shared window
    // must stay small enough that Mediapipe's fill time is survivable,
    // per the fairness principle: heavy models run at their real cost,
    // not hidden behind async caching.
    private val blurConfidence = RollingConfidence(windowSize = 5, requiredPassRate = 0.7f)
    private val fingerConfidence = RollingConfidence(windowSize = 4, requiredPassRate = 0.6f)
    private val glareConfidence = RollingConfidence(windowSize = 10, requiredPassRate = 0.9f)
    private val brightnessConfidence = RollingConfidence(windowSize = 10, requiredPassRate = 0.7f)

    private data class BestFrameCandidate(val frame: CameraFrame, val stage1BlurConfidence: Float)
    private val bestStage1Frame = AtomicReference<BestFrameCandidate?>(null)

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
        Log.i("CAPTURE_BENCHMARK", "✅ Capture completed in ${elapsed} ms (${elapsed / 1000.0}s)")
        captureStartTime.set(0L)
    }

    private fun startProcessingLoop() {
        stage1Job = coroutineScope.launch(Dispatchers.Default) {
            while (true) {
                val frame = latestRawFrame0.getAndSet(null)
                val startTime = System.currentTimeMillis()
                if (frame != null && !isCaptured.get() && isStage1Processing.compareAndSet(
                        false,
                        true
                    )
                ) {
                    try {
                        ensureActive()
                        logExecutionTime(TAG, "STAGE.1") { processStage1(frame) }
                    } finally {
                        isStage1Processing.set(false)
                    }
                }
                delay(max(1, 33 - (System.currentTimeMillis() - startTime)))
            }
        }

        // Finger check — ONE loop, always, regardless of strategy or
        // model. Cost varies (HSV ~100-300ms, Mediapipe ~2-2.5s) but the
        // architecture is now uniform: whichever model strategyConfig
        // resolves to runs here, feeding the shared fingerConfidence
        // tracker AND focus-lock targeting unconditionally.
        fingerCheckJob = coroutineScope.launch(Dispatchers.Default) {
            while (true) {
                val frame = latestRawFrame3.getAndSet(null)
                val startTime = System.currentTimeMillis()
                if (frame != null && !isCaptured.get() && isFingerProcessing.compareAndSet(
                        false,
                        true
                    )
                ) {
                    try {
                        logExecutionTime(TAG, "FingerCheck") { processFinger(frame) }
                    } finally {
                        isFingerProcessing.set(false)
                    }
                }
                delay(max(1, 33 - (System.currentTimeMillis() - startTime)))
            }
        }

        blurCheckJob = coroutineScope.launch(Dispatchers.Default) {
            while (true) {
                val frame = latestRawFrame2.getAndSet(null)
                val startTime = System.currentTimeMillis()
                if (frame != null && !isCaptured.get() && isBlurProcessing.compareAndSet(
                        false,
                        true
                    )
                ) {
                    try {
                        logExecutionTime(TAG, "Blur") { processBlur(frame) }
                    } finally {
                        isBlurProcessing.set(false)
                    }
                }
                delay(max(1, 33 - (System.currentTimeMillis() - startTime)))
            }
        }

        accumulatorJob = coroutineScope.launch(Dispatchers.Default) {
            while (true) {
                val frame = latestRawFrame1.getAndSet(null)
                val startTime = System.currentTimeMillis()
                if (frame != null && !isCaptured.get() && isReadyForAccumulation &&
                    isAccumulatingFrames.compareAndSet(false, true)
                ) {
                    try {
                        listener.onStartAccumulation()
                        accumulateFrameForStage2(frame)
                    } finally {
                        isAccumulatingFrames.set(false)
                    }
                }
                delay(max(1, 33 - (System.currentTimeMillis() - startTime)))
            }
        }

        stage2Job = coroutineScope.launch(Dispatchers.Default) {
            capturedFrameFlow.collectLatest { it ->
                if (it != null && getFinalBufferSize() < REQUIRED_SUCCESSFUL_IMAGES &&
                    isStage2Processing.compareAndSet(false, true)
                ) {
                    try {
                        listener.onStartStage2Processing()
                        logExecutionTime(TAG, "STAGE.2") { processStage2(it) }
                    } finally {
                        isStage2Processing.set(false)
                        listener.onStopStage2Processing()
                    }
                }
            }
        }
    }

    private suspend fun processStage1(frame: CameraFrame) = coroutineScope {
        val processingId = frame.processingId
        try {
            val currentTime = System.currentTimeMillis()
            if (currentTime - frame.timestamp > 500) return@coroutineScope

            val cutoutRect = provider.getCutoutRectInImageCoordinates(
                Size(frame.width, frame.height), frame.rotationDegrees
            )

            Log.d(TAG, "CUTOUT_CHECK -- rect=$cutoutRect valid=${isCutoutRectValid(cutoutRect)} previewSize=${provider.previewSize}, rotation=${frame.rotationDegrees}")
            if (!isCutoutRectValid(cutoutRect)) return@coroutineScope

            val (croppedByteArray, croppedByteArraySize) = frame.getByteArray(
                requiresCropping = true, cutoutRect = cutoutRect
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
                controller.saveBitmap(imageDataProvider.getAsUprightBitmap(), "Stage1UpImg")
            }

            val deferredResults = stage1Methods.map { (name, method) ->
                name to async(stage1Dispatcher) { method.run(imageDataProvider) }
            }.toMap()
            val results = deferredResults.mapValues { (_, deferred) -> deferred.await() }
            imageDataProvider.clearCache()

            val warnings = mutableListOf<Warning>()
            val passedProcessingStages = mutableListOf<ProcessingStage>()

            results.forEach { (name, result) ->
                when (result) {
                    is ProcessingResult.Passed<*> -> {
                        when (name) {
                            GLARE_CHECK -> {
                                passedProcessingStages.add(ProcessingStage.GLARE); glareConfidence.record(
                                    true
                                )
                            }

                            BRIGHTNESS_CHECK -> {
                                passedProcessingStages.add(ProcessingStage.BRIGHTNESS); brightnessConfidence.record(
                                    true
                                )
                            }

                            LIVENESS_CHECK -> passedProcessingStages.add(ProcessingStage.LIVENESS)
                        }
                    }

                    is ProcessingResult.Failed -> {
                        warnings.add(result.cause.toUiFailureCause().toWarning())
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

            val currentFingerResult = fingerResult.get()
            val isFingerPassed = currentFingerResult?.passed ?: false
            currentFingerResult?.let {
                when (it) {
                    is ProcessingResult.Passed -> passedProcessingStages.add(ProcessingStage.FINGER_DETECTION)
                    is ProcessingResult.Failed -> warnings.add(
                        it.cause.toUiFailureCause().toWarning()
                    )
                }
            } ?: warnings.add(Warning.NoFinger)

            if (!provider.isFocusLockedForCapture) {
                warnings.add(Warning.FocusNotLocked)
            } else {
                passedProcessingStages.add(ProcessingStage.NA)
            }

            // Unified gate — symmetric for blur AND finger now, since
            // both are resolved per-strategy the same way. Rolling:
            // both trackers must be confident. Instantaneous: both
            // must have passed on THIS exact cached read.
            val isStage1PassedGate = if (strategyConfig.useRollingConfidence) {
                blurConfidence.isConfident() &&
                        fingerConfidence.isConfident() &&
                        glareConfidence.isConfident() &&
                        brightnessConfidence.isConfident() &&
                        provider.isFocusLockedForCapture
            } else {
                results.values.all { it.passed } &&
                        isBlurPassed.get() &&
                        isFingerPassed &&
                        provider.isFocusLockedForCapture
            }

            isStage1Passed.set(isStage1PassedGate)

            Log.d(
                "ROLLING",
                "Blur=${blurConfidence.isConfident()} Brightness=${brightnessConfidence.isConfident()} " +
                        "Glare=${glareConfidence.isConfident()} Finger=${fingerConfidence.isConfident()} " +
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

            val liveScores = LiveQualityScores(
                blur = LiveCheckScore(
                    label = "Blur",
                    currentValue = lastBlurConfidence.get(),
                    acceptedMin = if (liveBlur is LaplacianBlurMethod)
                        blurGate.currentThreshold()
                    else preferenceStore.get(BlurSettings.THRESHOLD),
                    acceptedMax = if (liveBlur is LaplacianBlurMethod) Float.MAX_VALUE else 1.0f,
                    passed = isBlurPassed.get()
                ),
                brightness = LiveCheckScore(
                    label = "Brightness",
                    currentValue = brightnessResult?.confidence ?: 0f,
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
                    acceptedMin = preferenceStore.get(GlareSettings.VARIANCE_MIN)
                        .toFloat() / preferenceStore.get(GlareSettings.MAX_GLARE_VALUE),
                    acceptedMax = preferenceStore.get(GlareSettings.VARIANCE_MAX)
                        .toFloat() / preferenceStore.get(GlareSettings.MAX_GLARE_VALUE),
                    passed = glareResult?.passed ?: false
                ),
                fingerDetected = LiveCheckScore(
                    label = "Finger Detected",
                    currentValue = currentFingerResult?.confidence ?: 0f,
                    acceptedMin = if (liveFinger is FingerCheckPythonMethod)
                        preferenceStore.get(FingerSettings.GOOD_AREA_MIN) else 1f,
                    acceptedMax = if (liveFinger is FingerCheckPythonMethod)
                        preferenceStore.get(FingerSettings.GOOD_AREA_MAX) else 1f,
                    passed = isFingerPassed
                )
            )
            listener.onStage1ResultValues(liveScores)
            listener.onStage1Result(isStage1Passed.get(), warnings, passedProcessingStages)
        } catch (e: Exception) {
            Log.e(TAG, "Error in Stage 1 processing", e)
        }
    }

    private suspend fun processFinger(frame: CameraFrame) {
        try {
            val cutoutRect = provider.getCutoutRectInImageCoordinates(
                Size(frame.width, frame.height), frame.rotationDegrees
            )

            if (!isCutoutRectValid(cutoutRect)) return

            val (byteArray, byteArraySize) = frame.getByteArray(
                requiresCropping = liveFinger is FingerCheckPythonMethod,
                cutoutRect = cutoutRect
            )
            val imageDataProvider = ImageDataProvider(
                byteArray, byteArraySize.width, byteArraySize.height, frame.rotationDegrees
            )
            if (preferenceStore.get(ProcessingSettings.SAVE_FINGER_CHECK_INPUT)) {
                controller.saveBitmap(imageDataProvider.getAsBitmap(), "FingerCheckInput")
            }
            val result = logExecutionTime(TAG, "FingerCheck.ActualRun") {
                runInterruptible { liveFinger.run(imageDataProvider) }
            }
            imageDataProvider.clearCache()

            // Focus-lock targeting ALWAYS runs, regardless of which
            // model produced this result — targeting is decoupled from
            // the gating experiment.
            when (result) {
                is ProcessingResult.Passed -> {
                    listener.onFingerMaskResult(result.data.mask, imageDataProvider.rotationDegrees)
                    if (!isCaptured.get()) {
                        controller.triggerFocusLock(
                            result.data.box,
                            cutoutRect,
                            Size(frame.width, frame.height)
                        )
                    }
                }

                is ProcessingResult.Failed -> {
                    result.data?.let { data ->
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
            fingerResult.set(result)
            fingerConfidence.record(result.passed)
            Log.d("FINGER_TUNE", "passed=${result.passed} confidence=${result.confidence}")
        } catch (e: Exception) {
            Log.e(TAG, "FINGER -- Error in Finger Check processing", e)
        }
    }

    @SuppressLint("DefaultLocale")
    private suspend fun processBlur(frame: CameraFrame) {
        try {
            // Guard against the startup race: skip frames until the overlay's
            // real screen position and the preview's real measured size are
            // both known. Before that, getCutoutRectInImageCoordinates()
            // divides against zero-valued placeholders and produces NaN/Infinity.
            if (provider.previewSize.width == 0 || provider.previewSize.height == 0) return

            val cutoutRect = provider.getCutoutRectInImageCoordinates(
                Size(frame.width, frame.height), frame.rotationDegrees
            )

            if (!isCutoutRectValid(cutoutRect)) return

            val (croppedByteArray, croppedByteArraySize) = frame.getByteArray(
                requiresCropping = true, cutoutRect = cutoutRect
            )

            Log.d(TAG, "BLUR_CRASH_CHECK -- cutoutRect=$cutoutRect frameSize=${frame.width}x${frame.height} croppedSize=${croppedByteArraySize.width}x${croppedByteArraySize.height} arrayLen=${croppedByteArray.size}")

            val imageDataProvider = ImageDataProvider(
                croppedByteArray,
                croppedByteArraySize.width,
                croppedByteArraySize.height,
                frame.rotationDegrees
            )
            val blurResult = runInterruptible { liveBlur.run(imageDataProvider) }

            if (preferenceStore.get(ProcessingSettings.SAVE_BLUR_INPUT)) {
                val confFormatted = String.format("%.2f", blurResult.confidence).removePrefix("0")
                controller.saveBitmap(
                    imageDataProvider.getAsUprightBitmap(),
                    "BlurInput($confFormatted)"
                )
            }
            if (preferenceStore.get(ProcessingSettings.SAVE_SHARP_IMAGES) &&
                blurResult.confidence >= preferenceStore.get(BlurSettings.THRESHOLD)
            ) {
                val confFormatted = String.format("%.2f", blurResult.confidence).removePrefix("0")
                controller.saveBitmap(
                    imageDataProvider.getAsUprightBitmap(),
                    "SharpImage($confFormatted)"
                )
            }
            imageDataProvider.clearCache()
            isBlurPassed.set(blurResult.passed)
            lastBlurConfidence.set(blurResult.confidence)

            // BlurGate only supplies the threshold — degrading from target
            // to fallback after maxWaitMs. The actual pass/fail check and
            // the rolling confidence window are the SAME mechanism as
            // before, just checked against a threshold that can relax
            // over time instead of a fixed constant.
            val currentThreshold = blurGate.currentThreshold()
            val passed = blurResult.confidence >= currentThreshold
            isBlurPassed.set(passed)
            blurConfidence.record(passed)

            // NEW — update the single best-frame tracker, gated on finger
            // check having passed for the CURRENT cached finger result.
            // Not gated on isBlurPassed itself — we want the genuinely
            // best-scoring frame tracked even if it's still below Stage 1's
            // own live threshold, since Stage 2 applies its own, separate,
            // stricter thresholds later.
            val fingerPassed = fingerResult.get()?.passed == true
            if (fingerPassed) {
                val current = bestStage1Frame.get()
                if (current == null || blurResult.confidence > current.stage1BlurConfidence) {
                    bestStage1Frame.set(BestFrameCandidate(frame, blurResult.confidence))
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in Blur processing", e)
        }
    }

    private fun accumulateFrameForStage2(frame: CameraFrame) {
        val processingId = frame.processingId
        try {
            val currentTime = System.currentTimeMillis()
            if (currentTime - frame.timestamp > 500) return

            val timePassed = stage1PassedTime.get()?.let { SystemClock.uptimeMillis() - it } ?: 0L
            if (timePassed < DELAY_IN_ACCUMULATION_OF_FRAMES) return

            synchronized(capturedFrameBuffer) { capturedFrameBuffer.add(frame) }

            synchronized(capturedFrameBuffer) {
                if (capturedFrameBuffer.size >= preferenceStore.get(ProcessingSettings.IMAGE_COUNT_FOR_STAGE2)) {
                    if (isCaptured.compareAndSet(false, true)) controller.triggerCapture()
                    val freshBatch = capturedFrameBuffer.toList()
                    capturedFrameBuffer.clear()

                    // NEW — append the tracked best Stage 1 frame as a 4th candidate,
                    // if one exists. Stage 2 treats it identically to the 3 fresh
                    // frames — no special-casing, no carried-over score.
                    val storedBest = bestStage1Frame.get()?.frame
                    val finalBatch = if (storedBest != null) freshBatch + storedBest else freshBatch

                    _capturedFrameFlow.update { finalBatch }

                    // NEW — Stage 1 duration: from first frame arriving to batch dispatch
                    val stage1Duration = SystemClock.elapsedRealtime() - captureStartTime.get()
                    Log.i("STAGE1_BENCHMARK", "✅ Stage 1 completed in $stage1Duration ms (${stage1Duration / 1000.0}s)")

                    Log.i(TAG, "Dispatched a batch of ${finalBatch.size} frames to Stage 2.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in accumulator", e)
        }
    }

    abstract suspend fun processStage2(candidateBatch: List<CameraFrame>)

    override fun onImageAvailable(reader: ImageReader) {
        Log.d(TAG, "IMAGE_AVAILABLE_TICK -- ${SystemClock.uptimeMillis()}")
        if (isCollectingImage.compareAndSet(false, true)) {
            try {
                val image = reader.acquireLatestImage() ?: return
                startCaptureTimer()
                val processingId = processingCounter.incrementAndGet()
                image.use {
                    val cameraFrame = CameraFrame(
                        processingId = processingId,
                        byteArray = it.toByteArray(),
                        width = it.width,
                        height = it.height,
                        timestamp = System.currentTimeMillis(),
                        rotationDegrees = provider.totalRotation,
                        yRowStride = it.planes[0].rowStride
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

    private fun isCutoutRectValid(rect: RectF): Boolean {
        return !rect.left.isNaN() && !rect.top.isNaN() &&
                !rect.right.isNaN() && !rect.bottom.isNaN() &&
                rect.left.isFinite() && rect.top.isFinite() &&
                rect.right.isFinite() && rect.bottom.isFinite() &&
                rect.width() > 0f && rect.height() > 0f
    }

    internal fun addToFinalBuffer(image: SegmentedFrame) {
        synchronized(finalBuffer) { finalBuffer.add(image) }
    }

    private fun getFinalBufferSize(): Int = synchronized(finalBuffer) { finalBuffer.size }

    abstract fun unlockAccumulator()
    fun getFinalFrame(): SegmentedFrame = synchronized(finalBuffer) { finalBuffer.first() }

    open fun reset() {
        latestRawFrame0.set(null); latestRawFrame1.set(null); latestRawFrame2.set(null); latestRawFrame3.set(
            null
        )
        synchronized(finalBuffer) { finalBuffer.clear() }
        synchronized(capturedFrameBuffer) { capturedFrameBuffer.clear() }
        _capturedFrameFlow.update { null }
        isStage1Passed.set(false)
        stage1PassedTime.set(null)
        isBlurPassed.set(false)
        isCaptured.set(false)
        fingerResult.set(null)
        bestStage1Frame.set(null)
        blurGate.reset()
    }

    open fun close() {
        stage1Job?.cancel(); fingerCheckJob?.cancel(); blurCheckJob?.cancel()
        accumulatorJob?.cancel(); stage2Job?.cancel()
        stage1Dispatcher.close()
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
        fun onStage1Result(
            passed: Boolean,
            warnings: List<Warning>,
            passedChecks: List<ProcessingStage>
        )

        fun onStage1ResultValues(values: LiveQualityScores)
        fun onStartStage2Processing()
        fun onStopStage2Processing()
        fun onStage2ProcessingStageUpdate(processingStage: ProcessingStage)
        fun onStage2ResultValues(stage2ResultValue: Stage2ResultValue)
        fun onStage2Result(passed: Boolean, errors: List<Error>)
    }
}