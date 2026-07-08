package app.gov.uidai.capture.domain.method.segmentation

import android.content.Context
import android.graphics.RectF
import app.gov.uidai.capture.domain.config.SegmentationConfig
import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.domain.model.ImageProcessingMethod
import app.gov.uidai.capture.domain.model.ProcessingResult
import app.gov.uidai.capture.domain.model.SegmentationResultData

class U2NetInt8Method(
    private val context: Context,
    private val segmentationConfig: SegmentationConfig
) : ImageProcessingMethod<SegmentationResultData> {

    private val u2NetInt8 by lazy {
        U2NetInt8(context, segmentationConfig.modelPath)
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
            val detectedRect = u2NetInt8.analyse(bitmap)

            return detectedRect?.let {
                ProcessingResult.Passed(
                    data = it,
                    confidence = it.confidence
                )
            } ?: ProcessingResult.Failed(SegError.Segmentation, -1, 0f)

        } catch (e: Exception) {
            return ProcessingResult.Failed(SegError.SomethingWentWrong, -1, 0f, exception = e)
        }
    }

}