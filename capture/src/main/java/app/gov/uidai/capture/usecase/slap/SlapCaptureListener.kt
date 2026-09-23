package app.gov.uidai.capture.usecase.slap

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.media.ImageReader
import android.os.SystemClock
import android.util.Log
import android.util.Size
import app.gov.uidai.capture.domain.model.CameraFrame
import app.gov.uidai.capture.domain.model.SlapFrameResult
import app.gov.uidai.capture.slap.processing.SlapFingerBandDetector
import app.gov.uidai.capture.utils.extension.crop
import app.gov.uidai.capture.utils.extension.inflatedByPercent
import app.gov.uidai.capture.utils.extension.rotate
import app.gov.uidai.capture.utils.extension.toBitmap
import app.gov.uidai.capture.utils.extension.toByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class SlapLiveState(
    val frameId: Long = 0L,
    val handDetected: Boolean = false,
    val areaRatio: Float = 0f,
    val fingertips: List<PointF> = emptyList(),
    // Individual per-finger boxes -- draw one rect per detected finger,
    // matching the reference app's "4 boxes over each finger ROI" UI.
    val fingerBoxes: List<RectF> = emptyList(),
    val uprightFrameWidth: Int = 0,
    val uprightFrameHeight: Int = 0,
    val isReady: Boolean = false,
    val statusMessage: String = "Place all 4 fingers in frame",
    // Live estimate of how far the hand is from the camera, for
    // diagnostics/future UI -- null whenever no box has been detected yet.
    val handDistanceMM: Float? = null
)

/**
 * Live capture gate. Finger detection is SlapFingerBandDetector (classical
 * Otsu + row-projection, no palm needed) via SlapFrameAnalyzer -- NOT
 * MediaPipe hand-landmarks. MediaPipe was tried here and reverted: it
 * requires the palm/wrist/MCP joints to detect anything, and this app's
 * close, palm-not-shown framing never provides that, so it would never
 * pass. Do not reintroduce it for gating.
 */
class SlapCaptureListener(
    private val expectedHandType: String,
    private val analyzer: SlapFrameAnalyzer,
    private val blurChecker: SlapBlurChecker,
    private val coroutineScope: CoroutineScope,
    private val getRotationDegrees: () -> Int,
    private val triggerFocus: (handBoxUpright: RectF, uprightImageSize: Size, rotationDegrees: Int) -> Unit,
    // Decoupled from triggerFocus on purpose -- triggerFocus goes through
    // FocusManager.lock(), which is throttled by whichever focus strategy
    // is active and, under the new default (ManualFocusAtFixedDistance),
    // doesn't even look at the box width anymore (see CameraSettings.
    // FOCUS_TYPE's kdoc). Guidance needs the distance number every frame
    // regardless of what the focus strategy does with it.
    private val getHandDistanceMM: (handBoxUpright: RectF, uprightImageSize: Size, rotationDegrees: Int) -> Float,
    private val targetHandDistanceMM: Float,
    private val handDistanceToleranceMM: Float
) : ImageReader.OnImageAvailableListener {

    companion object {
        private val TAG = SlapCaptureListener::class.simpleName
        private const val THROTTLE_MS = 100L
        // Kept at 4 (bumped from 2 on origin, commit b80eadd: "avoid
        // clicking if hand moves") -- that tuning is independent of the
        // MediaPipe/skin-blob vs finger-band gating change and still applies.
        private const val REQUIRED_CONSECUTIVE_PASSES = 4
        private const val CROP_PADDING_PERCENT = 0.08f
    }

    private val processingCounter = AtomicLong(0)
    private val lastProcessedAt = AtomicLong(0L)
    private val isProcessing = AtomicBoolean(false)
    private val isCaptured = AtomicBoolean(false)

    @Volatile
    private var consecutivePasses = 0

    @Volatile
    private var lastAttemptBlurFailed = false

    private val _liveState = MutableStateFlow(SlapLiveState())
    val liveState = _liveState.asStateFlow()

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap = _capturedBitmap.asStateFlow()

    override fun onImageAvailable(reader: ImageReader) {
        val image = try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire image", e)
            null
        } ?: return

        if (isCaptured.get()) {
            image.close()
            return
        }

        val rotationDegrees = getRotationDegrees()
        val now = SystemClock.uptimeMillis()

        val eligible = now - lastProcessedAt.get() >= THROTTLE_MS && isProcessing.compareAndSet(false, true)

        if (!eligible) {
            image.close()
            return
        }

        val frame = try {
            image.use {
                CameraFrame(
                    processingId = processingCounter.incrementAndGet(),
                    byteArray = it.toByteArray(),
                    width = it.width,
                    height = it.height,
                    timestamp = System.currentTimeMillis(),
                    rotationDegrees = rotationDegrees,
                    yRowStride = it.planes[0].rowStride
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build CameraFrame", e)
            isProcessing.set(false)
            return
        }

        lastProcessedAt.set(now)
        coroutineScope.launch {
            try {
                processFrame(frame, rotationDegrees)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "processFrame failed unexpectedly", e)
            } finally {
                isProcessing.set(false)
            }
        }
    }

    private suspend fun processFrame(frame: CameraFrame, rotationDegrees: Int) {
        val result = analyzer.analyze(frame, expectedHandType)

        val uprightWidth = if (rotationDegrees == 90 || rotationDegrees == 270) frame.height else frame.width
        val uprightHeight = if (rotationDegrees == 90 || rotationDegrees == 270) frame.width else frame.height

        val detectedCount = result.fingerBoxes.size
        // result.handDetected already means "all 4 found" (see
        // SlapFrameAnalyzer), kept explicit here for clarity at the call site.
        val framePassed = result.handDetected

        if (!framePassed) {
            lastAttemptBlurFailed = false
        }

        consecutivePasses = if (framePassed) consecutivePasses + 1 else 0

        // Distance guidance -- computed independently of triggerFocus (see
        // constructor kdoc): with the lens now locked to a fixed distance
        // (ManualFocusAtFixedDistance), the USER has to be the one who
        // moves, so tell them which way. Null whenever there's no box to
        // measure, or the measured distance is within tolerance of the
        // target (nothing to say -- falls through to the existing
        // areaOk-based message below).
        val handDistanceMM = result.box?.let { box ->
            try {
                getHandDistanceMM(box, Size(uprightWidth, uprightHeight), rotationDegrees)
                    .takeIf { it > 0f }
            } catch (e: Exception) {
                Log.e(TAG, "getHandDistanceMM failed -- continuing without distance guidance", e)
                null
            }
        }

        val distanceGuidance = handDistanceMM?.let { distanceMM ->
            when {
                distanceMM > targetHandDistanceMM + handDistanceToleranceMM -> "Move hand closer to the camera"
                distanceMM < targetHandDistanceMM - handDistanceToleranceMM -> "Move hand farther from the camera"
                else -> null
            }
        }

        val statusMessage = when {
            detectedCount == 0 -> "Place all 4 fingers in frame"
            detectedCount < SlapFingerBandDetector.FINGER_COUNT ->
                "Only $detectedCount/${SlapFingerBandDetector.FINGER_COUNT} fingers detected — reposition hand"
            lastAttemptBlurFailed -> "Too blurry — hold steady"
            consecutivePasses < REQUIRED_CONSECUTIVE_PASSES -> "Hold steady"
            else -> "Capturing automatically..."
        }

        result.box?.let { box ->
            try {
                triggerFocus(box, Size(uprightWidth, uprightHeight), rotationDegrees)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "triggerFocus failed -- continuing without it", e)
            }
        }

        _liveState.update {
            it.copy(
                frameId = frame.processingId,
                handDetected = result.handDetected,
                areaRatio = result.areaRatio,
                fingertips = result.fingertips,
                fingerBoxes = result.fingerBoxes,
                uprightFrameWidth = uprightWidth,
                uprightFrameHeight = uprightHeight,
                isReady = framePassed && consecutivePasses >= REQUIRED_CONSECUTIVE_PASSES,
                statusMessage = statusMessage,
                handDistanceMM = handDistanceMM
            )
        }

        if (framePassed && consecutivePasses >= REQUIRED_CONSECUTIVE_PASSES &&
            isCaptured.compareAndSet(false, true)
        ) {
            attemptCapture(frame, result)
        }
    }

    private suspend fun attemptCapture(frame: CameraFrame, result: SlapFrameResult) {
        try {
            val (byteArray, size) = frame.getByteArray(requiresCropping = false, cutoutRect = RectF())
            val uprightBitmap = byteArray.toBitmap(size).rotate(frame.rotationDegrees)

            // result.box is the union of the 4 detected finger bands (see
            // SlapFrameAnalyzer) -- a real finger-shaped region, not a
            // skin-color blob.
            val box = result.box ?: RectF(0f, 0f, uprightBitmap.width.toFloat(), uprightBitmap.height.toFloat())
            val paddedBox = box.inflatedByPercent(CROP_PADDING_PERCENT)
            val croppedBitmap = uprightBitmap.crop(paddedBox)

            // Blur check runs on the CROPPED hand region, not the full
            // frame -- scoring the whole frame let a sharp background
            // offset genuine motion blur on the hand itself.
            val blurResult = blurChecker.check(croppedBitmap)

            if (!blurResult.passed) {
                Log.w(
                    TAG,
                    "Slap capture blur check failed (densenet=${blurResult.densenetConfidence}) " +
                            "-- resetting, keep looping"
                )
                lastAttemptBlurFailed = true
                consecutivePasses = 0
                isCaptured.set(false)
                return
            }

            _capturedBitmap.value = croppedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error finishing slap capture", e)
            consecutivePasses = 0
            isCaptured.set(false)
        }
    }

    fun reset() {
        consecutivePasses = 0
        lastAttemptBlurFailed = false
        isCaptured.set(false)
        _capturedBitmap.value = null
        _liveState.value = SlapLiveState()
    }
}