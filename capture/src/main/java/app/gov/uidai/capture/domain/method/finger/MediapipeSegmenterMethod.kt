package app.gov.uidai.capture.domain.method.finger

import android.content.Context
import android.graphics.RectF
import android.util.Size
import androidx.core.graphics.toRectF
import app.gov.uidai.capture.domain.config.FingerConfig
import app.gov.uidai.capture.domain.config.FingerResultStatus
import app.gov.uidai.capture.domain.model.FingerResultData
import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.domain.model.ImageProcessingMethod
import app.gov.uidai.capture.domain.model.ProcessingResult
import app.gov.uidai.capture.domain.model.Warning

class MediapipeSegmenterMethod(
    context: Context,
    private val fingerConfig: FingerConfig,
    private val getCutoutRect: (Size, Int) -> RectF,
    private val getPreviewSize: () -> Size
) : ImageProcessingMethod<FingerResultData> {

    private val mediapipeSegmenter = MediapipeSegmenter(
        context = context,
        modelPath = fingerConfig.modelPath,
        fingerCategoryId = fingerConfig.fingerCategoryIds
    )

    override fun run(provider: ImageDataProvider): ProcessingResult<FingerResultData> {
        if (!fingerConfig.enabled) {
            return ProcessingResult.Passed(
                FingerResultData(RectF()), 1f
            )
        }

        val bitmap = provider.getAsBitmap()
        val startTime = System.currentTimeMillis()
        val cutoutRect = getCutoutRect(Size(bitmap.width, bitmap.height), provider.rotationDegrees)
        val previewSize = getPreviewSize()

        try {
            val res = mediapipeSegmenter.segmentLiveStreamFrame(
                bitmap = bitmap,
                cutoutRect = cutoutRect,
                previewSize = previewSize
            )

            val fingerBox = res.box
            val status: Int

            if (fingerBox == null || !res.isValid) {
                status = FingerResultStatus.RESULT_FINGER_NOT_DETECTED
            } else {
                // Calculate area ratio
                val frameArea = (cutoutRect.width() * cutoutRect.height())
                val fingerArea = (fingerBox.width() * fingerBox.height()).toFloat()
                val areaRatio = fingerArea / frameArea

                status = FingerResultStatus.let {
                    when {
                        areaRatio < fingerConfig.minFingerArea -> it.RESULT_FINGER_NOT_DETECTED
                        areaRatio < fingerConfig.goodAreaMin -> it.RESULT_FINGER_TOO_FAR
                        areaRatio > fingerConfig.goodAreaMax -> it.RESULT_FINGER_TOO_MUCH_CLOSE
                        areaRatio > fingerConfig.maxFingerArea -> it.RESULT_FINGER_TOO_CLOSE
                        else -> it.RESULT_FINGER_OK
                    }
                }
            }

            val confidence = if (status == FingerResultStatus.RESULT_FINGER_NOT_DETECTED) 0f else 1f

            val passed = (status == FingerResultStatus.RESULT_FINGER_OK)
            if (passed) {
                return ProcessingResult.Passed(
                    data = FingerResultData(box = fingerBox!!.toRectF(), mask = res.mask),
                    confidence = confidence
                )
            }

            val warning = FingerResultStatus.let {
                when (status) {
                    it.RESULT_FINGER_NOT_DETECTED -> Warning.NoFinger
                    it.RESULT_FINGER_TOO_FAR -> Warning.FingerTooFar
                    it.RESULT_FINGER_TOO_CLOSE -> Warning.FingerTooClose
                    it.RESULT_FINGER_TOO_MUCH_CLOSE -> Warning.FingerTooMuchClose
                    else -> Warning.Internal
                }
            }

            val data = fingerBox?.let {
                FingerResultData(box = it.toRectF(), mask = res.mask)
            }

            return ProcessingResult.Failed(
                cause = warning,
                status = status,
                confidence = confidence,
                data = data
            )

        } catch (e: Exception) {
            return ProcessingResult.Failed(
                cause = Warning.Internal,
                status = FingerResultStatus.RESULT_FINGER_NOT_DETECTED,
                confidence = 0f
            )
        } finally {
            val processingTime = System.currentTimeMillis() - startTime
        }
    }
}