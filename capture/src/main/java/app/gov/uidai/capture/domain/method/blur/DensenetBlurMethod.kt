package app.gov.uidai.capture.domain.method.blur

import android.content.Context
import app.gov.uidai.capture.domain.config.BlurConfig
import app.gov.uidai.capture.domain.model.Error
import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.domain.model.ImageProcessingMethod
import app.gov.uidai.capture.domain.model.ProcessingResult
import app.gov.uidai.capture.domain.model.Warning


class DensenetBlurMethod(
    private val context: Context,
    private val blurConfig: BlurConfig
) : ImageProcessingMethod<Unit> {

    private val densenetBlur by lazy {
        DensenetBlur(context, blurConfig.modelPath)
    }

    override fun run(provider: ImageDataProvider): ProcessingResult<Unit> {
        if (!blurConfig.enabled) {
            return ProcessingResult.Passed(data = Unit, confidence = 1.0f)
        }

        try {
            val blurResult = densenetBlur.detectBlur(
                provider.getAsUprightBitmap(),
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
}