package app.gov.uidai.capture.usecase.single

import android.content.Context
import android.graphics.RectF
import android.util.Log
import app.gov.uidai.capture.domain.model.CameraFrame
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FingerYoloAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "finger-yolo") }.asCoroutineDispatcher()
    private var probe: FingerYoloProbe? = null
    private var initTried = false
    private var warmedUp = false

    private fun probe(): FingerYoloProbe? {
        if (!initTried) {
            initTried = true
            probe = try {
                FingerYoloProbe(context)
            } catch (e: Throwable) {
                Log.e("FingerYoloAnalyzer", "YOLO init failed", e)
                null
            }
        }
        return probe
    }

    private fun doWarmUp() {
        if (warmedUp) return
        try {
            val detector = probe() ?: return
            detector.warmUp()
            warmedUp = true
        } catch (e: Throwable) {
            Log.e("FingerYoloAnalyzer", "Finger YOLO warm-up failed", e)
        }
    }

    suspend fun warmUp() = withContext(dispatcher) { doWarmUp() }

    /** Finger boxes in pixels of the UPRIGHT frame. */
    suspend fun detect(frame: CameraFrame): List<RectF> = withContext(dispatcher) {
        try {
            val (nv21, size) = frame.getByteArray(requiresCropping = false, cutoutRect = RectF())
            probe()?.detect(nv21, size.width, size.height, frame.rotationDegrees).orEmpty()
        } catch (e: Exception) {
            Log.e("FingerYoloAnalyzer", "Error in finger YOLO detect", e)
            emptyList()
        }
    }
}