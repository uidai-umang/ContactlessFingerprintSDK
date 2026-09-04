package app.gov.uidai.capture.usecase.slap

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import app.gov.uidai.capture.domain.config.BlurSettings
import app.gov.uidai.capture.domain.method.blur.DensenetBlur
import app.gov.uidai.capture.domain.method.blur.LaplacianBlurMethod
import app.gov.uidai.capture.domain.model.BlurCheckMethodType
import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.pref.PreferenceStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SlapBlurChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceStore: PreferenceStore
) {
    companion object {
        private val TAG = SlapBlurChecker::class.simpleName
        private const val LAPLACIAN_MIN_VARIANCE = 300f
        private const val DENSENET_THRESHOLD = 0.85f
    }

    data class Result(
        val passed: Boolean,
        val laplacianVariance: Float,
        val densenetConfidence: Float
    )

    private val laplacian by lazy { LaplacianBlurMethod(minVariance = LAPLACIAN_MIN_VARIANCE) }

    private val densenet by lazy {
        val modelPath = when (preferenceStore.get(BlurSettings.MODEL)) {
            BlurCheckMethodType.Densenet -> "best_densenet121_blur_model_float16.tflite"
            BlurCheckMethodType.NewDensenet -> "new_best_densenet121_blur_model_float16.tflite"
        }
        DensenetBlur(context, modelPath)
    }

    fun check(provider: ImageDataProvider, bitmap: Bitmap): Result {
        val laplacianResult = try {
            laplacian.run(provider)
        } catch (e: Exception) {
            Log.e(TAG, "Laplacian check failed", e)
            null
        }
        val laplacianVariance = laplacianResult?.confidence ?: 0f

        if (laplacianResult?.passed == true) {
            Log.d(TAG, "Blur check passed via Laplacian (variance=$laplacianVariance)")
            return Result(passed = true, laplacianVariance = laplacianVariance, densenetConfidence = 0f)
        }

        val densenetResult = try {
            densenet.detectBlur(bitmap, DENSENET_THRESHOLD)
        } catch (e: Exception) {
            Log.e(TAG, "DenseNet check failed", e)
            null
        }
        val densenetPassed = densenetResult?.isSharp ?: false
        val densenetConfidence = densenetResult?.confidence ?: 0f

        if (densenetPassed) {
            Log.d(TAG, "Blur check passed via DenseNet (confidence=$densenetConfidence)")
        } else {
            Log.d(
                TAG,
                "Blur check failed on both -- laplacianVariance=$laplacianVariance densenetConfidence=$densenetConfidence"
            )
        }

        return Result(passed = densenetPassed, laplacianVariance = laplacianVariance, densenetConfidence = densenetConfidence)
    }
}