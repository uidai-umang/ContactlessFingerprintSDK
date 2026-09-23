package app.gov.uidai.capture.usecase.slap

import android.graphics.RectF
import android.util.Log
import app.gov.uidai.capture.domain.model.CameraFrame
import app.gov.uidai.capture.domain.model.SlapFrameResult
import app.gov.uidai.capture.slap.processing.SlapFingerBandDetector
import app.gov.uidai.capture.utils.extension.rotate
import app.gov.uidai.capture.utils.extension.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Live-loop finger detection. Deliberately does NOT use MediaPipe hand
 * landmarks -- that model needs the palm/wrist/MCP joints in frame to
 * detect anything, which this app's close, palm-not-shown framing never
 * shows it. Uses SlapFingerBandDetector instead: the same classical
 * Otsu-threshold + row-projection detector SlapFingerprintProcessor already
 * uses at capture time, downscaled here for per-frame speed. No palm
 * required -- only the finger crop itself, same as the reference app this
 * framing was modeled on.
 */
class SlapFrameAnalyzer @Inject constructor() {

    companion object {
        private val TAG = SlapFrameAnalyzer::class.simpleName

        // Otsu + row projection are O(width * height); this runs on a
        // ~100ms throttle, so the live pass works on a downscaled copy.
        // SlapFingerBandDetector maps results back to the full-res bitmap's
        // coordinate space regardless -- callers here never see the
        // downscale.
        private const val LIVE_ANALYSIS_MAX_WIDTH = 320
    }

    private val bandDetector = SlapFingerBandDetector()

    suspend fun analyze(frame: CameraFrame, expectedHandType: String): SlapFrameResult =
        withContext(Dispatchers.Default) {
            try {
                val (byteArray, size) = frame.getByteArray(requiresCropping = false, cutoutRect = RectF())
                val bitmap = byteArray.toBitmap(size).rotate(frame.rotationDegrees)

                val handType = if (expectedHandType.equals("Left", ignoreCase = true)) {
                    SlapFingerBandDetector.HandType.LEFT
                } else {
                    SlapFingerBandDetector.HandType.RIGHT
                }

                val detection = bandDetector.detect(
                    bitmap = bitmap,
                    handType = handType,
                    maxAnalysisWidth = LIVE_ANALYSIS_MAX_WIDTH
                )

                // fingerprintRegion is the tip-anchored ROI box (covers the
                // actual print-bearing area), NOT fingerBand (a full-width
                // horizontal strip used only internally for row-projection
                // banding). Drawing/cropping fingerBand was the source of
                // the "box only covers the fingertip" complaint -- the
                // overlay was never drawing a box at all, just the small
                // per-fingertip point marker.
                val fingerBoxes = detection.bands.map { it.fingerprintRegion }
                val fingertips = detection.bands.map { it.fingertip.point }

                // Union of all detected fingerprint ROIs -- used for
                // focus-lock and as the capture crop box. Tighter than the
                // old full-width-band union, so the blur check and
                // focus-lock target the actual finger content instead of
                // a wide strip that includes background on both sides.
                val unionBox = fingerBoxes.fold<RectF, RectF?>(null) { acc, box ->
                    if (acc == null) RectF(box) else acc.apply { union(box) }
                }

                SlapFrameResult(
                    // Only "ready" once ALL 4 fingers are individually
                    // detected -- partial counts (1-3) are surfaced via
                    // fingerBoxes.size so the UI/status message can say
                    // "align all 4 fingers" rather than just "no hand".
                    handDetected = fingerBoxes.size == SlapFingerBandDetector.FINGER_COUNT,
                    areaRatio = fingerBoxes.size.toFloat() / SlapFingerBandDetector.FINGER_COUNT,
                    fingertips = fingertips,
                    box = unionBox,
                    fingerBoxes = fingerBoxes
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in slap frame analysis", e)
                SlapFrameResult(handDetected = false, areaRatio = 0f, fingertips = emptyList(), box = null)
            }
        }
}