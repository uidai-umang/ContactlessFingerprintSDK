package app.gov.uidai.capture.domain.method.blur

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import app.gov.uidai.capture.domain.config.BlurConfig
import app.gov.uidai.capture.domain.model.Error
import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.domain.model.ImageProcessingMethod
import app.gov.uidai.capture.domain.model.ProcessingResult
import app.gov.uidai.capture.domain.model.Warning
import com.chaquo.python.Python
import java.nio.ByteBuffer

class DensenetBlurMethod(
    private val context: Context,
    private val blurConfig: BlurConfig,
    private val onDebugCrop: ((bitmap: Bitmap, callId: Int, label: String) -> Unit)? = null
) : ImageProcessingMethod<Unit> {
    companion object {
        private val TAG = DensenetBlurMethod::class.simpleName
    }

    private val densenetBlur by lazy {
        DensenetBlur(context, blurConfig.modelPath)
    }

    private val py by lazy { Python.getInstance() }
    private val fingerSegmentModule by lazy { py.getModule("blur_detector_laplacian") }

    // Debug: id counter so each call's saved crop is traceable back to
    // logs by number, since this runs once per candidate per Stage 2 call.
    private var callCounter = 0

    override fun run(provider: ImageDataProvider): ProcessingResult<Unit> {
        if (!blurConfig.enabled) {
            return ProcessingResult.Passed(data = Unit, confidence = 1.0f)
        }
        try {
            val callId = callCounter++
            val bitmap = provider.getAsUprightBitmap()
            val croppedBitmap = cropToFingerIfPossible(bitmap, callId)
            val blurResult = densenetBlur.detectBlur(
                croppedBitmap,
                blurConfig.threshold
            )
            val passed = blurResult?.isSharp ?: false
            val confidence = blurResult?.confidence ?: 0f
            Log.i(TAG, "STAGE2_DEBUG [DenseNet call=$callId] confidence=$confidence passed=$passed")
            return if (passed) {
                ProcessingResult.Passed(data = Unit, confidence = confidence)
            } else {
                ProcessingResult.Failed(cause = Warning.Blur, status = -1, confidence = confidence)
            }
        } catch (e: Exception) {
            return ProcessingResult.Failed(
                cause = Error.SomethingWentWrong,
                status = -1,
                confidence = 0f,
                exception = e
            )
        }
    }

    private fun cropToFingerIfPossible(bitmap: Bitmap, callId: Int): Bitmap {
        return try {
            val argbBitmap = if (bitmap.config == Bitmap.Config.ARGB_8888) {
                bitmap
            } else {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            }
            val buffer = ByteBuffer.allocate(argbBitmap.byteCount)
            argbBitmap.copyPixelsToBuffer(buffer)
            val rgbaBytes = buffer.array()

            val bboxResult = fingerSegmentModule.callAttr(
                "get_finger_bbox_from_rgba", rgbaBytes, argbBitmap.width, argbBitmap.height
            )
            val bboxList = bboxResult.asList()
            if (bboxList.size < 4) {
                Log.d(TAG, "FINGER_CROP -- segmentation unavailable, scoring whole frame")
                saveDebugCrop(bitmap, callId, "wholeFrame")
                return bitmap
            }

            val x = bboxList[0].toInt().coerceIn(0, argbBitmap.width - 1)
            val y = bboxList[1].toInt().coerceIn(0, argbBitmap.height - 1)
            val w = bboxList[2].toInt().coerceAtMost(argbBitmap.width - x)
            val h = bboxList[3].toInt().coerceAtMost(argbBitmap.height - y)
            if (w <= 0 || h <= 0) {
                Log.w(TAG, "FINGER_CROP -- degenerate bbox ($x,$y,$w,$h), scoring whole frame")
                saveDebugCrop(bitmap, callId, "degenerateBbox")
                return bitmap
            }

            Log.d(TAG, "FINGER_CROP -- cropped to finger bbox=($x,$y,${w}x$h) from ${argbBitmap.width}x${argbBitmap.height}")
            val cropped = Bitmap.createBitmap(argbBitmap, x, y, w, h)
            saveDebugCrop(cropped, callId, "bbox_${x}_${y}_${w}x$h")
            cropped
        } catch (e: Exception) {
            Log.w(TAG, "FINGER_CROP -- failed, scoring whole frame", e)
            saveDebugCrop(bitmap, callId, "cropFailed")
            bitmap
        }
    }

    private fun saveDebugCrop(bitmap: Bitmap, callId: Int, label: String) {
        try {
            onDebugCrop?.invoke(bitmap, callId, label)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log debug crop", e)
        }
    }
}