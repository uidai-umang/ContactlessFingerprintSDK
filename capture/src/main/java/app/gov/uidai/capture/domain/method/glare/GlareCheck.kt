package app.gov.uidai.capture.domain.method.glare

import app.gov.uidai.capture.domain.config.GlareConfig
import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.domain.model.ImageProcessingMethod
import app.gov.uidai.capture.domain.model.ProcessingResult
import app.gov.uidai.capture.domain.model.Warning
import app.gov.uidai.capture.utils.extension.getConfidence
import app.gov.uidai.capture.utils.extension.getStatus
import com.chaquo.python.Python


class GlareCheck(
    private val glareConfig: GlareConfig
) : ImageProcessingMethod<Unit> {

    companion object {
        private val TAG = GlareCheck::class.simpleName
        private const val RESULT_GLARE_OK = 0
        private const val RESULT_GLARE_FAILED = -1
    }

    private val py = Python.getInstance()
    private val glareModule = py.getModule("glare_detector")

    override fun run(provider: ImageDataProvider): ProcessingResult<Unit> {
        if (!glareConfig.enabled) {
            return ProcessingResult.Passed(Unit, 1f)
        }

        try {
            val result = glareModule.callAttr(
                "main",
                provider.getAsByteArray(),
                provider.width,
                provider.height,
                glareConfig.threshold,
                glareConfig.varianceMin,
                glareConfig.varianceMax,
                glareConfig.maxGlareValue
            )


            val status = result.getStatus()
            val confidence = result.getConfidence()

            return if (status == RESULT_GLARE_OK) {
                ProcessingResult.Passed(Unit, confidence)
            } else {
                ProcessingResult.Failed(Warning.Glare, status, confidence)
            }
        } catch (e: Exception) {
            return ProcessingResult.Failed(
                cause = Warning.Internal,
                status = RESULT_GLARE_FAILED,
                confidence = 0f,
                exception = e
            )
        }
    }
}