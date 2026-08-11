package app.gov.uidai.capture.usecase

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
import app.gov.uidai.capture.domain.model.FingerCheckMethodType
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
import app.gov.uidai.capture.usecase.runner.BlurCheckRunner
import app.gov.uidai.capture.usecase.runner.BrightnessCheckRunner
import app.gov.uidai.capture.usecase.runner.FingerCheckRunner
import app.gov.uidai.capture.usecase.runner.GlareCheckRunner
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
    }

    protected val blurCheck by lazy { blurCheckFactory.create() }
    private val laplacianBlurCheck by lazy {
        LaplacianBlurMethod(minVariance = preferenceStore.get(LaplacianBlurSettings.MIN_VARIANCE))
    }
    protected val segmentationCheck by lazy { segmentationFactory.create() }

    protected data class CaptureStrategyConfig(
        val useRollingConfidence: Boolean,
        val accumulationDelayMs: Long
    )

    private fun resolveLiveBlur(): ImageProcessingMethod<Unit> =
        when (preferenceStore.get(LiveCheckSettings.LIVE_BLUR_MODEL)) {
            LiveBlurMethodType.Densenet, LiveBlurMethodType.NewDensenet -> blurCheck
            LiveBlurMethodType.Laplacian -> laplacianBlurCheck
        }

    private fun resolveLiveFinger(): ImageProcessingMethod<FingerResultData> =
        fingerCheckFactory.create(
            getCutoutRect = provider::getCutoutRectInImageCoordinates,
            getPreviewSize = provider::previewSize,
            methodOverride = preferenceStore.get(LiveCheckSettings.LIVE_FINGER_MODEL)
        )

    protected val strategyConfig: CaptureStrategyConfig by lazy {
        when (preferenceStore.get(ProcessingSettings.CAPTURE_STRATEGY)) {
            CaptureStrategyType.StableStrategy -> CaptureStrategyConfig(false, 1_250L)
            CaptureStrategyType.FastStrategy -> CaptureStrategyConfig(true, 450L)
        }
    }

    protected val liveBlur: ImageProcessingMethod<Unit> by lazy { resolveLiveBlur() }
    protected val liveFinger: ImageProcessingMethod<FingerResultData> by lazy { resolveLiveFinger() }
    protected val mediapipeFinger: ImageProcessingMethod<FingerResultData> by lazy {
        fingerCheckFactory.create(
            getCutoutRect = provider::getCutoutRectInImageCoordinates,
            getPreviewSize = provider::previewSize,
            methodOverride = FingerCheckMethodType.MediapipeSelfieSegmenter
        )
    }

    // ---------------- Check runners ---------------- //
    private val glareCheck = GlareCheck(glareConfig)
    private val brightnessCheckMethod = BrightnessCheck(brightnessConfig)

    protected val stage1Methods: Map<String, ImageProcessingMethod<*>> = mapOf(
        GLARE_CHECK to glareCheck,
        BRIGHTNESS_CHECK to brightnessCheckMethod
    )

    private val glareRunner = GlareCheckRunner(glareCheck)
    private val brightnessRunner = BrightnessCheckRunner(brightnessCheckMethod)

    private val blurRunner: BlurCheckRunner by lazy {
        BlurCheckRunner(
            liveBlur = liveBlur,
            provider = provider,
            controller = controller,
            preferenceStore = preferenceStore,
            onBlurResult = ::onBlurResult
        )
    }
    private val fingerRunner: FingerCheckRunner by lazy {
        FingerCheckRunner(
            liveFinger = liveFinger,
            mediapipeFinger = mediapipeFinger,
            provider = provider,
            controller = controller,
            listener = listener,
            preferenceStore = preferenceStore,
            coroutineScope = coroutineScope
        )
    }

    private val latestRawFrame0 = AtomicReference<CameraFrame?>()
    private val latestRawFrame1 = AtomicReference<CameraFrame?>()
    private val latestRawFrame2 = AtomicReference<CameraFrame?>()
    private val latestRawFrame3 = AtomicReference<CameraFrame?>()
    private val latestRawFrame4 = AtomicReference<CameraFrame?>()
    private val capturedFrameBuffer = Collections.synchronizedList(mutableListOf<CameraFrame>())
    private val _capturedFrameFlow = MutableStateFlow<List<CameraFrame>?>(null)
    private val capturedFrameFlow = _capturedFrameFlow.asStateFlow()
    private val processingCounter = AtomicLong(0)
    private val finalBuffer = Collections.synchronizedList(mutableListOf<SegmentedFrame>())
    private val isCollectingImage = AtomicBoolean(false)
    private val isStage1Processing = AtomicBoolean(false)
    private val isMediapipeProcessing = AtomicBoolean(false)
    private val isAccumulatingFrames = AtomicBoolean(false)
    private val isStage2Processing = AtomicBoolean(false)
    private val isCaptured = AtomicBoolean(false)
    protected val isStage1Passed = AtomicBoolean(false)

    // Best-frame tracking is genuinely cross-cutting (needs blur's
    // confidence AND finger's pass state), stays here rather than in
    // either runner.
    private data class BestFrameCandidate(val frame: CameraFrame, val stage1BlurConfidence: Float)
    private val bestStage1Frame = AtomicReference<BestFrameCandidate?>(null)

    private fun onBlurResult(frame: CameraFrame, confidence: Float, passed: Boolean) {
        if (fingerRunner.passed) {
            val current = bestStage1Frame.get()
            if (current == null || confidence > current.stage1BlurConfidence) {
                bestStage1Frame.set(BestFrameCandidate(frame, confidence))
            }
        }
    }

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
    private var mediapipeCheckJob: Job? = null
    private var blurCheckJob: Job? = null
    private var accumulatorJob: Job? = null
    private var stage2Job: Job? = null

    private val captureStartTime = AtomicLong(0L)
    protected fun startCaptureTimer() = captureStartTime.compareAndSet(0L, SystemClock.elapsedRealtime())
    protected fun stopCaptureTimer() {
        val elapsed = SystemClock.elapsedRealtime() - captureStartTime.get()
        Log.i("CAPTURE_BENCHMARK", "Capture completed in ${elapsed}ms (${elapsed / 1000.0}s)")
        captureStartTime.set(0L)
    }

    private fun startProcessingLoop() {
        stage1Job = coroutineScope.launch(Dispatchers.Default) {
            while (true) {
                val frame = latestRawFrame0.getAndSet(null)
                val startTime = System.currentTimeMillis()
                if (frame != null && !isCaptured.get() && isStage1Processing.compareAndSet(false, true)) {
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

        // HSV -- fast, primary, ticks every frame.
        fingerCheckJob = coroutineScope.launch(Dispatchers.Default) {
            while (true) {
                val frame = latestRawFrame3.getAndSet(null)
                val startTime = System.currentTimeMillis()
                if (frame != null) fingerRunner.runHsv(frame, isCaptured.get())
                delay(max(1, 33 - (System.currentTimeMillis() - startTime)))
            }
        }

        // Mediapipe -- independent, slower, own cadence. Only ever surfaces
        // when it PASSES, or when it fails and HSV agrees -- see FingerCheckRunner.
        mediapipeCheckJob = coroutineScope.launch(Dispatchers.Default) {
            while (true) {
                val frame = latestRawFrame4.getAndSet(null)
                val startTime = System.currentTimeMillis()
                if (frame != null && !isCaptured.get() && isMediapipeProcessing.compareAndSet(false, true)) {
                    try {
                        fingerRunner.runMediapipe(frame, isCaptured.get())
                    } finally {
                        isMediapipeProcessing.set(false)
                    }
                }
                delay(max(1, 33 - (System.currentTimeMillis() - startTime)))
            }
        }

        blurCheckJob = coroutineScope.launch(Dispatchers.Default) {
            while (true) {
                val frame = latestRawFrame2.getAndSet(null)
                val startTime = System.currentTimeMillis()
                if (frame != null && !isCaptured.get()) {
                    logExecutionTime(TAG, "Blur") { blurRunner.run(frame) }
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
            capturedFrameFlow.collectLatest {
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
        try {
            if (System.currentTimeMillis() - frame.timestamp > 500) return@coroutineScope
            val cutoutRect = provider.getCutoutRectInImageCoordinates(Size(frame.width, frame.height), frame.rotationDegrees)
            if (!CutoutRectUtils.isValid(cutoutRect)) return@coroutineScope

            val (croppedByteArray, croppedByteArraySize) = frame.getByteArray(requiresCropping = true, cutoutRect = cutoutRect)
            val imageDataProvider = ImageDataProvider(croppedByteArray, croppedByteArraySize.width, croppedByteArraySize.height, frame.rotationDegrees)

            if (preferenceStore.get(ProcessingSettings.SAVE_STAGE1_IMAGE)) {
                controller.saveBitmap(imageDataProvider.getAsBitmap(), "Stage1Img")
            }
            if (preferenceStore.get(ProcessingSettings.SAVE_STAGE1_UPRIGHT_IMAGE)) {
                controller.saveBitmap(imageDataProvider.getAsUprightBitmap(), "Stage1UpImg")
            }

            val glareDeferred = async(stage1Dispatcher) { glareRunner.run(imageDataProvider) }
            val brightnessDeferred = async(stage1Dispatcher) { brightnessRunner.run(imageDataProvider) }
            val glare = glareDeferred.await()
            val brightness = brightnessDeferred.await()
            imageDataProvider.clearCache()

            val warnings = mutableListOf<Warning>()
            val passedProcessingStages = mutableListOf<ProcessingStage>()

            when (glare) {
                is ProcessingResult.Passed -> passedProcessingStages.add(ProcessingStage.GLARE)
                is ProcessingResult.Failed -> warnings.add(glare.cause.toUiFailureCause().toWarning())
            }
            when (brightness) {
                is ProcessingResult.Passed -> passedProcessingStages.add(ProcessingStage.BRIGHTNESS)
                is ProcessingResult.Failed -> warnings.add(brightness.cause.toUiFailureCause().toWarning())
            }

            if (blurRunner.isPassed) passedProcessingStages.add(ProcessingStage.BLUR) else warnings.add(Warning.Blur)

            val currentFingerResult = fingerRunner.result
            currentFingerResult?.let {
                when (it) {
                    is ProcessingResult.Passed -> passedProcessingStages.add(ProcessingStage.FINGER_DETECTION)
                    is ProcessingResult.Failed -> warnings.add(it.cause.toUiFailureCause().toWarning())
                }
            } ?: warnings.add(Warning.NoFinger)

            if (!provider.isFocusLockedForCapture) warnings.add(Warning.FocusNotLocked)
            else passedProcessingStages.add(ProcessingStage.NA)

            val isStage1PassedGate = if (strategyConfig.useRollingConfidence) {
                blurRunner.isConfident() && fingerRunner.isConfident() &&
                        glareRunner.isConfident() && brightnessRunner.isConfident() &&
                        provider.isFocusLockedForCapture
            } else {
                glare.passed && brightness.passed && blurRunner.isPassed &&
                        fingerRunner.passed && provider.isFocusLockedForCapture
            }
            isStage1Passed.set(isStage1PassedGate)

            if (isStage1PassedGate) {
                stage1PassedTime.compareAndSet(null, SystemClock.uptimeMillis())
            } else {
                listener.onStage1Error()
                stage1PassedTime.set(null)
                synchronized(capturedFrameBuffer) { capturedFrameBuffer.clear() }
            }

            val liveScores = LiveQualityScores(
                blur = LiveCheckScore(
                    label = "Blur",
                    currentValue = blurRunner.lastConfidence,
                    acceptedMin = if (liveBlur is LaplacianBlurMethod) blurRunner.currentThreshold() else preferenceStore.get(BlurSettings.THRESHOLD),
                    acceptedMax = if (liveBlur is LaplacianBlurMethod) Float.MAX_VALUE else 1.0f,
                    passed = blurRunner.isPassed
                ),
                brightness = LiveCheckScore(
                    label = "Brightness",
                    currentValue = brightness.confidence,
                    acceptedMin = 0f,
                    acceptedMax = max(preferenceStore.get(BrightnessSettings.DARK_PERCENT), preferenceStore.get(BrightnessSettings.BRIGHT_PERCENT)),
                    passed = brightness.passed
                ),
                glare = LiveCheckScore(
                    label = "Glare",
                    currentValue = glare.confidence,
                    acceptedMin = preferenceStore.get(GlareSettings.VARIANCE_MIN).toFloat() / preferenceStore.get(GlareSettings.MAX_GLARE_VALUE),
                    acceptedMax = preferenceStore.get(GlareSettings.VARIANCE_MAX).toFloat() / preferenceStore.get(GlareSettings.MAX_GLARE_VALUE),
                    passed = glare.passed
                ),
                fingerDetected = LiveCheckScore(
                    label = "Finger Detected",
                    currentValue = currentFingerResult?.confidence ?: 0f,
                    acceptedMin = if (liveFinger is FingerCheckPythonMethod) preferenceStore.get(FingerSettings.GOOD_AREA_MIN) else 1f,
                    acceptedMax = if (liveFinger is FingerCheckPythonMethod) preferenceStore.get(FingerSettings.GOOD_AREA_MAX) else 1f,
                    passed = fingerRunner.passed
                )
            )
            listener.onStage1ResultValues(liveScores)
            listener.onStage1Result(isStage1Passed.get(), warnings, passedProcessingStages)
        } catch (e: Exception) {
            Log.e(TAG, "Error in Stage 1 processing", e)
        }
    }

    private fun accumulateFrameForStage2(frame: CameraFrame) {
        try {
            if (System.currentTimeMillis() - frame.timestamp > 500) return
            val timePassed = stage1PassedTime.get()?.let { SystemClock.uptimeMillis() - it } ?: 0L
            if (timePassed < DELAY_IN_ACCUMULATION_OF_FRAMES) return

            synchronized(capturedFrameBuffer) {
                capturedFrameBuffer.add(frame)
                if (capturedFrameBuffer.size >= preferenceStore.get(ProcessingSettings.IMAGE_COUNT_FOR_STAGE2)) {
                    if (isCaptured.compareAndSet(false, true)) controller.triggerCapture()
                    val freshBatch = capturedFrameBuffer.toList()
                    capturedFrameBuffer.clear()
                    val storedBest = bestStage1Frame.get()?.frame
                    val finalBatch = if (storedBest != null) freshBatch + storedBest else freshBatch
                    _capturedFrameFlow.update { finalBatch }
                    val stage1Duration = SystemClock.elapsedRealtime() - captureStartTime.get()
                    Log.i("STAGE1_BENCHMARK", "Stage 1 completed in ${stage1Duration}ms (${stage1Duration / 1000.0}s)")
                    Log.i(TAG, "Dispatched a batch of ${finalBatch.size} frames to Stage 2.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in accumulator", e)
        }
    }

    abstract suspend fun processStage2(candidateBatch: List<CameraFrame>)

    override fun onImageAvailable(reader: ImageReader) {
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
                    latestRawFrame4.set(cameraFrame)
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

    internal fun addToFinalBuffer(image: SegmentedFrame) = synchronized(finalBuffer) { finalBuffer.add(image) }
    private fun getFinalBufferSize(): Int = synchronized(finalBuffer) { finalBuffer.size }
    abstract fun unlockAccumulator()
    fun getFinalFrame(): SegmentedFrame = synchronized(finalBuffer) { finalBuffer.first() }

    open fun reset() {
        isCaptured.set(true)
        latestRawFrame0.set(null); latestRawFrame1.set(null); latestRawFrame2.set(null)
        latestRawFrame3.set(null); latestRawFrame4.set(null)
        synchronized(finalBuffer) { finalBuffer.clear() }
        synchronized(capturedFrameBuffer) { capturedFrameBuffer.clear() }
        _capturedFrameFlow.update { null }
        isStage1Passed.set(false)
        stage1PassedTime.set(null)
        bestStage1Frame.set(null)
        blurRunner.reset()
        fingerRunner.reset()
        isCaptured.set(false)
    }

    open fun close() {
        stage1Job?.cancel(); fingerCheckJob?.cancel(); mediapipeCheckJob?.cancel(); blurCheckJob?.cancel()
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
        fun onStage1Result(passed: Boolean, warnings: List<Warning>, passedChecks: List<ProcessingStage>)
        fun onStage1ResultValues(values: LiveQualityScores)
        fun onStartStage2Processing()
        fun onStopStage2Processing()
        fun onStage2ProcessingStageUpdate(processingStage: ProcessingStage)
        fun onStage2ResultValues(stage2ResultValue: Stage2ResultValue)
        fun onStage2Result(passed: Boolean, errors: List<Error>)
    }
}