package app.gov.uidai.capture.usecase.slap

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import app.gov.uidai.capture.domain.config.BlurSettings
import app.gov.uidai.capture.domain.method.blur.DensenetBlur
import app.gov.uidai.capture.domain.model.BlurCheckMethodType
import app.gov.uidai.capture.pref.PreferenceStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SlapBlurChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceStore: PreferenceStore
) {
    companion object {
        private val TAG = SlapBlurChecker::class.simpleName
        private const val DENSENET_THRESHOLD = 0.85f
    }

    data class Result(
        val passed: Boolean,
        val densenetConfidence: Float
    )

    private val densenet by lazy {
        val modelPath = when (preferenceStore.get(BlurSettings.MODEL)) {
            BlurCheckMethodType.Densenet -> "best_densenet121_blur_model_float16.tflite"
            BlurCheckMethodType.NewDensenet -> "new_best_densenet121_blur_model_float16.tflite"
        }
        DensenetBlur(context, modelPath)
    }

    /**
     * Laplacian was dropped for Slap (was: run first, DenseNet as fallback).
     * It shares blur_detector_laplacian.py with single-finger capture, whose
     * segment_finger() hard-rejects any region with height/width < 1.0 -- a
     * 4-finger slap crop is landscape, so it always fell back to scoring the
     * whole passed-in region. LAPLACIAN_MIN_VARIANCE (300, later bumped to
     * 500) was also that module's pre-recalibration whole-cutout-era
     * threshold, not its current segmented-finger scale (~13-14) -- so even
     * the fallback score was being checked against the wrong number. Net
     * effect: it never meaningfully passed or failed anything for Slap, it
     * just always deferred to DenseNet -- confirmed independently (commit
     * db1e235, "using densenet for blur check").
     *
     * Signature takes just the Bitmap now (dropped the unused
     * ImageDataProvider param) -- caller passes the CROPPED hand region
     * (see SlapCaptureListener.attemptCapture), so DenseNet itself no
     * longer gets diluted by background. A real Slap-shaped
     * blur/segmentation check belongs with the item-4 ROI work, not a
     * patched threshold here.
     */
    fun check(bitmap: Bitmap): Result {
        val densenetResult = try {
            densenet.detectBlur(bitmap, DENSENET_THRESHOLD)
        } catch (e: Exception) {
            Log.e(TAG, "DenseNet check failed", e)
            null
        }
        val passed = densenetResult?.isSharp ?: false
        val confidence = densenetResult?.confidence ?: 0f

        Log.d(TAG, "Slap blur check ${if (passed) "passed" else "failed"} (densenet=$confidence)")

        return Result(passed = passed, densenetConfidence = confidence)
    }
}