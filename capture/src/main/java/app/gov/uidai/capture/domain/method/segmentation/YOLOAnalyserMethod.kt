package app.gov.uidai.capture.domain.method.segmentation

import android.content.Context
import android.graphics.RectF
import android.util.Log
import app.gov.uidai.capture.domain.config.SegmentationConfig
import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.domain.model.ImageProcessingMethod
import app.gov.uidai.capture.domain.model.ProcessingResult
import app.gov.uidai.capture.domain.model.SegmentationResultData

class YOLOAnalyserMethod(
    private val context: Context,
    private val segmentationConfig: SegmentationConfig
) : ImageProcessingMethod<SegmentationResultData> {

    companion object {
        private val TAG = YOLOAnalyserMethod::class.simpleName
    }

    private val yoloAnalyser by lazy {
        YOLOAnalyser(context, segmentationConfig.modelPath)
    }

    override fun run(provider: ImageDataProvider): ProcessingResult<SegmentationResultData> {
        val bitmap = provider.getAsUprightBitmap()
        if (!segmentationConfig.enabled) {
            val fullRect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            return ProcessingResult.Passed(
                data = SegmentationResultData(box = fullRect),
                confidence = 1f
            )
        }

        try {
            val confidenceThreshold = segmentationConfig.yoloThreshold
            val detectionResult = yoloAnalyser.analyse(bitmap, confidenceThreshold)

            return detectionResult?.let {
                val isPassed = it.confidence >= confidenceThreshold

                if (!isPassed) {
                    ProcessingResult.Failed(SegError.Segmentation, -1, it.confidence)
                } else {
                    ProcessingResult.Passed(it, it.confidence)
                }
            } ?: ProcessingResult.Failed(SegError.Segmentation, -1, 0f)

        } catch (e: Exception) {
            Log.d(TAG, "$e")
            return ProcessingResult.Failed(SegError.SomethingWentWrong, -1, 0f, exception = e)
        }
    }
}