package app.gov.uidai.capture.usecase.factory

import android.content.Context
import app.gov.uidai.capture.domain.config.SegmentationConfig
import app.gov.uidai.capture.domain.config.SegmentationSettings
import app.gov.uidai.capture.domain.method.segmentation.U2NetFloat32Method
import app.gov.uidai.capture.domain.method.segmentation.U2NetInt8Method
import app.gov.uidai.capture.domain.method.segmentation.YOLOAnalyserMethod
import app.gov.uidai.capture.domain.model.ImageProcessingMethod
import app.gov.uidai.capture.domain.model.SegmentationMethodType
import app.gov.uidai.capture.domain.model.SegmentationResultData
import app.gov.uidai.capture.pref.PreferenceStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SegmentationFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceStore: PreferenceStore
) {

    companion object {
        private const val YOLO = "best_float32.tflite"
        private const val U2NET_INT8 = "u2net_quantized_int8.tflite"
        private const val U2NET_FLOAT32 = "u2net_320x320_float32.tflite"
        private const val U2NET_FLOAT32_FINAL = "u2net_320x320_float32_final.tflite"
    }

    fun create(): ImageProcessingMethod<SegmentationResultData> {
        val model = preferenceStore.get(SegmentationSettings.MODEL)

        val modelPath = when(model){
            SegmentationMethodType.YOLO -> YOLO
            SegmentationMethodType.U2NetInt8 -> U2NET_INT8
            SegmentationMethodType.U2NetFloat32 -> U2NET_FLOAT32
            SegmentationMethodType.U2NetFloat32Final -> U2NET_FLOAT32_FINAL
        }

        val segmentationConfig = SegmentationConfig(
            enabled = preferenceStore.get(SegmentationSettings.ENABLED),
            modelPath = modelPath,
            yoloThreshold = preferenceStore.get(SegmentationSettings.YOLO_THRESHOLD)
        )
        return when (model) {
            SegmentationMethodType.YOLO -> YOLOAnalyserMethod(context, segmentationConfig)
            SegmentationMethodType.U2NetInt8 -> U2NetInt8Method(context, segmentationConfig)
            SegmentationMethodType.U2NetFloat32 -> U2NetFloat32Method(context, segmentationConfig)
            SegmentationMethodType.U2NetFloat32Final -> U2NetFloat32Method(
                context,
                segmentationConfig
            )
        }
    }
}