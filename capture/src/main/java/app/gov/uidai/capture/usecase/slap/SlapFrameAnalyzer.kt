package app.gov.uidai.capture.usecase.slap

import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import app.gov.uidai.capture.domain.method.hand.SlapHandLandmarker
import app.gov.uidai.capture.domain.model.CameraFrame
import app.gov.uidai.capture.domain.model.SlapFrameResult
import app.gov.uidai.capture.utils.extension.rotate
import app.gov.uidai.capture.utils.extension.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Bridges a raw CameraFrame into SlapHandLandmarker's native MediaPipe
 * Tasks call -- decodes the frame's NV21 bytes into a Bitmap (reusing the
 * existing ByteArray.toBitmap() extension, same one ImageDataProvider uses),
 * rotates it upright, and hands it off. Public signature is unchanged from
 * the earlier Python-backed version, so SlapCaptureListener needed no
 * further changes beyond dropping its own now-redundant point rotation
 * (see SlapCaptureListener.kt).
 *
 * The rotate() call matters: the raw NV21 frame is in sensor-native
 * orientation (e.g. landscape even in portrait use), and MediaPipe's
 * HandLandmarker is not rotation-tolerant -- feeding it a sideways hand
 * silently returns no detection rather than erroring, which is why hand
 * detection wasn't working at all before this fix. The returned
 * SlapFrameResult's box/fingertips are therefore in UPRIGHT bitmap
 * coordinate space (bitmap.width x bitmap.height post-rotation), not raw
 * sensor space -- see SlapCaptureListener.kt for how that's consumed.
 */
class SlapFrameAnalyzer @Inject constructor(
    private val slapHandLandmarker: SlapHandLandmarker
) {

    companion object {
        private val TAG = SlapFrameAnalyzer::class.simpleName
    }

    suspend fun analyze(frame: CameraFrame, expectedHandType: String): SlapFrameResult =
        withContext(Dispatchers.Default) {
            try {
                // requiresCropping = false -- cutoutRect is ignored in that branch
                // (CameraFrame.getByteArray), so RectF() is just a throwaway arg.
                val (byteArray, size) = frame.getByteArray(requiresCropping = false, cutoutRect = RectF())
                val bitmap = byteArray.toBitmap(size).rotate(frame.rotationDegrees)

                val callStart = SystemClock.uptimeMillis()
                val result = slapHandLandmarker.detectHand(bitmap, expectedHandType)
                val callDuration = SystemClock.uptimeMillis() - callStart
                Log.d(TAG, "CALL_DURATION=${callDuration}ms")

                result
            } catch (e: Exception) {
                Log.e(TAG, "Error in slap frame analysis", e)
                SlapFrameResult(handDetected = false, areaRatio = 0f, fingertips = emptyList(), box = null)
            }
        }
}
