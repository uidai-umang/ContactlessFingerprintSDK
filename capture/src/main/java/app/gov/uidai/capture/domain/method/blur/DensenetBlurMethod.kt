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
    private val blurConfig: BlurConfig
) : ImageProcessingMethod<Unit> {
    companion object {
        private val TAG = DensenetBlurMethod::class.simpleName
    }

    private val densenetBlur by lazy {
        DensenetBlur(context, blurConfig.modelPath)
    }

    // NEW -- reuses the exact same finger-segmentation logic already
    // validated for the Laplacian check (see blur_detector_laplacian.py's
    // segment_finger / get_finger_bbox_from_rgba). This is DenseNet's
    // first-ever Python/Chaquopy dependency; previously this class was
    // pure Kotlin/TFLite -- a deliberate tradeoff to avoid a second,
    // possibly-drifting copy of the same segmentation logic. See the
    // reasoning in this session's conversation record for why this exists:
    // DenseNet resizes whatever it's given straight to 224x224 regardless
    // of source resolution; feeding it the WHOLE cutout (finger + a lot of
    // background, since the finger measured only ~47% of cutout width on
    // one real device) compounds that aggressive downsample, and was
    // observed to sometimes pass genuinely blurry captures.
    //
    // NOT YET EMPIRICALLY VALIDATED against the real .tflite model on
    // real device captures (no model file was available to test against
    // when this was written) -- confirm on-device with known-sharp and
    // known-blurry real captures before trusting this in production, and
    // recalibrate BlurSettings.THRESHOLD if needed -- cropping before
    // resize changes what the model actually sees, so the 0.85 default
    // that was tuned against the OLD (whole-frame) preprocessing may not
    // be the right cutoff anymore.
    private val py by lazy { Python.getInstance() }
    private val fingerSegmentModule by lazy { py.getModule("blur_detector_laplacian") }

    override fun run(provider: ImageDataProvider): ProcessingResult<Unit> {
        if (!blurConfig.enabled) {
            return ProcessingResult.Passed(data = Unit, confidence = 1.0f)
        }
        try {
            val bitmap = provider.getAsUprightBitmap()
            val croppedBitmap = cropToFingerIfPossible(bitmap)
            val blurResult = densenetBlur.detectBlur(
                croppedBitmap,
                blurConfig.threshold
            )
            val passed = blurResult?.isSharp ?: false
            val confidence = blurResult?.confidence ?: 0f
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

    /**
     * Crops [bitmap] to the segmented finger region before it reaches
     * DenseNet's own fixed 224x224 resize, so that resize is spent
     * entirely on ridge content instead of partly on background. Falls
     * back to the original, uncropped bitmap on ANY failure -- a failed
     * crop attempt should never be worse than the old (whole-frame)
     * behavior, only potentially no better than it.
     */
    private fun cropToFingerIfPossible(bitmap: Bitmap): Bitmap {
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
                return bitmap
            }

            val x = bboxList[0].toInt().coerceIn(0, argbBitmap.width - 1)
            val y = bboxList[1].toInt().coerceIn(0, argbBitmap.height - 1)
            val w = bboxList[2].toInt().coerceAtMost(argbBitmap.width - x)
            val h = bboxList[3].toInt().coerceAtMost(argbBitmap.height - y)
            if (w <= 0 || h <= 0) {
                Log.w(TAG, "FINGER_CROP -- degenerate bbox ($x,$y,$w,$h), scoring whole frame")
                return bitmap
            }

            Log.d(TAG, "FINGER_CROP -- cropped to finger bbox=($x,$y,${w}x$h) from ${argbBitmap.width}x${argbBitmap.height}")
            Bitmap.createBitmap(argbBitmap, x, y, w, h)
        } catch (e: Exception) {
            Log.w(TAG, "FINGER_CROP -- failed, scoring whole frame", e)
            bitmap
        }
    }
}