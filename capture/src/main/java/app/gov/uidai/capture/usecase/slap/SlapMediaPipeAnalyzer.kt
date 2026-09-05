package app.gov.uidai.capture.usecase.slap

import android.graphics.RectF
import android.util.Log
import app.gov.uidai.capture.domain.method.hand.SlapHandLandmarker
import app.gov.uidai.capture.domain.model.CameraFrame
import app.gov.uidai.capture.domain.model.SlapFrameResult
import app.gov.uidai.capture.utils.extension.rotate
import app.gov.uidai.capture.utils.extension.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SlapMediaPipeAnalyzer @Inject constructor(
    private val handLandmarker: SlapHandLandmarker
) {
    companion object {
        private val TAG = SlapMediaPipeAnalyzer::class.simpleName
    }

    suspend fun analyze(frame: CameraFrame): SlapFrameResult =
        withContext(Dispatchers.Default) {
            try {
                val (byteArray, size) = frame.getByteArray(requiresCropping = false, cutoutRect = RectF())
                val bitmap = byteArray.toBitmap(size).rotate(frame.rotationDegrees)
                handLandmarker.detectHand(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Error in MediaPipe frame analysis", e)
                SlapFrameResult(handDetected = false, areaRatio = 0f, fingertips = emptyList(), box = null)
            }
        }
}