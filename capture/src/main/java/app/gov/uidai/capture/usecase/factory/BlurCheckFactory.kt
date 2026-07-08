package app.gov.uidai.capture.usecase.factory

import android.content.Context
import app.gov.uidai.capture.domain.config.BlurConfig
import app.gov.uidai.capture.domain.config.BlurSettings
import app.gov.uidai.capture.domain.method.blur.DensenetBlurMethod
import app.gov.uidai.capture.domain.model.BlurCheckMethodType
import app.gov.uidai.capture.domain.model.ImageProcessingMethod
import app.gov.uidai.capture.pref.PreferenceStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class BlurCheckFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceStore: PreferenceStore
) {

    companion object {
        private const val DENSENET_BLUR = "best_densenet121_blur_model_float16.tflite"
        private const val DENSENET_BLUR_NEW = "new_best_densenet121_blur_model_float16.tflite"
    }

    fun create(): ImageProcessingMethod<Unit> {
        val modelPath = when(preferenceStore.get(BlurSettings.MODEL)) {
            BlurCheckMethodType.Densenet -> DENSENET_BLUR
            BlurCheckMethodType.NewDensenet -> DENSENET_BLUR_NEW
        }

        val blurConfig = BlurConfig(
            enabled = preferenceStore.get(BlurSettings.ENABLED),
            modelPath = modelPath,
            threshold = preferenceStore.get(BlurSettings.THRESHOLD)
        )
        return DensenetBlurMethod(context = context, blurConfig = blurConfig)
    }
}