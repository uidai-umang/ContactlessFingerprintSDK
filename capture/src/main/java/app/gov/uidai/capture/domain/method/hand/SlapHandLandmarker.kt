package app.gov.uidai.capture.domain.method.hand

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import app.gov.uidai.capture.domain.model.SlapFrameResult
import app.gov.uidai.capture.utils.logExecutionTime
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class SlapHandLandmarker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val TAG = SlapHandLandmarker::class.simpleName
        private const val MODEL_ASSET_PATH = "hand_landmarker.task"
        private val FINGERTIP_LANDMARK_INDICES = listOf(8, 12, 16, 20)
        private val EMPTY_RESULT = SlapFrameResult(handDetected = false, areaRatio = 0f, fingertips = emptyList(), box = null)
    }

    private fun String.toMirroredHandType(): String = when (this) {
        "Left" -> "Right"
        "Right" -> "Left"
        else -> this
    }

    private val handLandmarker: HandLandmarker? by lazy { setupHandLandmarker() }

    private fun setupHandLandmarker(): HandLandmarker? {
        return try {
            val baseOptions = BaseOptions.builder()
                .setDelegate(Delegate.CPU)
                .setModelAssetPath(MODEL_ASSET_PATH)
                .build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()
            HandLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "HandLandmarker failed to load model with error: " + e.message)
            null
        } catch (e: RuntimeException) {
            Log.e(TAG, "HandLandmarker failed to load model with error: " + e.message)
            null
        }
    }

    fun detectHand(bitmap: Bitmap, expectedHandType: String): SlapFrameResult {
        val landmarker = handLandmarker ?: return EMPTY_RESULT

        val mirroredExpectedHandType = expectedHandType.toMirroredHandType()

        val mpImage = logExecutionTime(TAG, "BitmapImageBuilder") {
            BitmapImageBuilder(bitmap).build()
        }

        val result = try {
            logExecutionTime(TAG, "HandLandmarker Inference Time") {
                landmarker.detect(mpImage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "HandLandmarker.detect() failed", e)
            return EMPTY_RESULT
        }

        if(result == null) {
            Log.d(TAG, "FailureState -- result $result")
            return EMPTY_RESULT
        }

        val handednesses = result.handednesses()
        val landmarksPerHand = result.landmarks()

        val matchingIndex = handednesses.indexOfFirst { categories ->
            categories.firstOrNull()?.categoryName() == mirroredExpectedHandType
        }

        if (matchingIndex == -1 || matchingIndex >= landmarksPerHand.size) {
            Log.d(TAG, "FailureState -- matchingIndex= $matchingIndex")
            return EMPTY_RESULT
        }

        val landmarks = landmarksPerHand[matchingIndex]
        val width = bitmap.width
        val height = bitmap.height

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        landmarks.forEach { landmark ->
            val px = landmark.x() * width
            val py = landmark.y() * height
            minX = min(minX, px)
            minY = min(minY, py)
            maxX = max(maxX, px)
            maxY = max(maxY, py)
        }
        val box = RectF(minX, minY, maxX, maxY)

        val frameArea = max(1f, (width * height).toFloat())
        val boxArea = max(0f, box.width()) * max(0f, box.height())
        val areaRatio = boxArea / frameArea

        val fingertips = FINGERTIP_LANDMARK_INDICES.map { index ->
            val landmark = landmarks[index]
            PointF(landmark.x() * width, landmark.y() * height)
        }

        return SlapFrameResult(
            handDetected = true,
            areaRatio = areaRatio,
            fingertips = fingertips,
            box = box
        )
    }
}