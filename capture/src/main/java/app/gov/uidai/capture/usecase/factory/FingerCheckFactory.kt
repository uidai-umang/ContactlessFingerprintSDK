package app.gov.uidai.capture.usecase.factory

import android.content.Context
import android.graphics.RectF
import android.util.Size
import app.gov.uidai.capture.domain.config.FingerConfig
import app.gov.uidai.capture.domain.config.FingerSettings
import app.gov.uidai.capture.domain.method.finger.FingerCheckPythonMethod
import app.gov.uidai.capture.domain.method.finger.MediapipeSegmenterMethod
import app.gov.uidai.capture.domain.model.FingerCheckMethodType
import app.gov.uidai.capture.domain.model.FingerResultData
import app.gov.uidai.capture.domain.model.ImageProcessingMethod
import app.gov.uidai.capture.pref.PreferenceStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class FingerCheckFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceStore: PreferenceStore
) {
    fun create(
        getCutoutRect: (Size, Int) -> RectF,
        getPreviewSize: () -> Size
    ): ImageProcessingMethod<FingerResultData> {

        val method = preferenceStore.get(FingerSettings.METHOD)

        val modelPath = when(method) {
            FingerCheckMethodType.MediapipeSelfieSegmenter -> "selfie_segmenter.tflite"
            FingerCheckMethodType.MediapipeDeepLabV3 -> "deeplabv3.tflite"
            FingerCheckMethodType.PythonHSVContourDetection -> ""
        }

        val fingerCategoryIds = when(method) {
            FingerCheckMethodType.MediapipeSelfieSegmenter -> listOf(0U)
            FingerCheckMethodType.MediapipeDeepLabV3 -> listOf(15U)
            FingerCheckMethodType.PythonHSVContourDetection -> listOf()
        }

        val fingerConfig = FingerConfig(
            enabled = preferenceStore.get(FingerSettings.ENABLED),
            modelPath = modelPath,
            fingerCategoryIds = fingerCategoryIds,
            minFingerArea = preferenceStore.get(FingerSettings.MIN_FINGER_AREA),
            maxFingerArea = preferenceStore.get(FingerSettings.MAX_FINGER_AREA),
            goodAreaMin = preferenceStore.get(FingerSettings.GOOD_AREA_MIN),
            goodAreaMax = preferenceStore.get(FingerSettings.GOOD_AREA_MAX),
            textureThresholdMin = preferenceStore.get(FingerSettings.TEXTURE_THRESHOLD_MIN),
            textureThresholdMax = preferenceStore.get(FingerSettings.TEXTURE_THRESHOLD_MAX),
            edgeDensityThresholdMin = preferenceStore.get(FingerSettings.EDGE_DENSITY_THRESHOLD_MIN),
            edgeDensityThresholdMax = preferenceStore.get(FingerSettings.EDGE_DENSITY_THRESHOLD_MAX)
        )

        return when (method) {
            FingerCheckMethodType.PythonHSVContourDetection -> FingerCheckPythonMethod(
                fingerConfig = fingerConfig
            )

            else -> MediapipeSegmenterMethod(
                context = context,
                fingerConfig = fingerConfig,
                getCutoutRect = getCutoutRect,
                getPreviewSize = getPreviewSize
            )
        }
    }
}