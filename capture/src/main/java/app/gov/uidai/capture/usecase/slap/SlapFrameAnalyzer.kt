package app.gov.uidai.capture.usecase.slap

import android.content.Context
import android.graphics.RectF
import android.util.Log
import app.gov.uidai.capture.domain.model.CameraFrame
import app.gov.uidai.capture.domain.model.SlapFrameResult
import app.gov.uidai.capture.slap.processing.SlapFingerBandDetector
import app.gov.uidai.capture.utils.extension.rotate
import app.gov.uidai.capture.utils.extension.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SlapFrameAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val TAG = SlapFrameAnalyzer::class.simpleName
        private const val LIVE_ANALYSIS_MAX_WIDTH = 320
        private const val PROBE_EVERY_N_FRAMES = 2
    }

    private val bandDetector = SlapFingerBandDetector()

    private val yoloProbe by lazy {
        try { SlapYoloProbe(context) } catch (e: Throwable) {
            Log.e(TAG, "SlapYoloProbe init failed", e); null
        }
    }
    private var probeCounter = 0

    suspend fun analyze(frame: CameraFrame, expectedHandType: String): SlapFrameResult =
        withContext(Dispatchers.Default) {
            try {
                val (byteArray, size) = frame.getByteArray(requiresCropping = false, cutoutRect = RectF())
                val bitmap = byteArray.toBitmap(size).rotate(frame.rotationDegrees)

                if (probeCounter++ % PROBE_EVERY_N_FRAMES == 0) yoloProbe?.probe(bitmap)

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

                val fingerBoxes = detection.bands.map { it.fingerprintRegion }
                val fingertips = detection.bands.map { it.fingertip.point }

                val unionBox = fingerBoxes.fold<RectF, RectF?>(null) { acc, box ->
                    if (acc == null) RectF(box) else acc.apply { union(box) }
                }

                SlapFrameResult(
                    handDetected = fingerBoxes.isNotEmpty(),
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