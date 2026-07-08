package app.gov.uidai.capture.domain.method.brightness

import app.gov.uidai.capture.domain.config.BrightnessConfig
import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.domain.model.ImageProcessingMethod
import app.gov.uidai.capture.domain.model.ProcessingResult
import app.gov.uidai.capture.domain.model.Warning
import app.gov.uidai.capture.utils.extension.getConfidence
import app.gov.uidai.capture.utils.extension.getStatus
import com.chaquo.python.Python

class BrightnessCheck(
    private val brightnessConfig: BrightnessConfig
) : ImageProcessingMethod<Unit> {

    companion object {
        const val RESULT_BRIGHTNESS_OK = 0
        const val RESULT_BRIGHTNESS_LOW = -1
        const val RESULT_BRIGHTNESS_HIGH = 1
    }

    private val py = Python.getInstance()
    private val brightnessModule = py.getModule("brightness_detector")

    override fun run(provider: ImageDataProvider): ProcessingResult<Unit> {
        if (!brightnessConfig.enabled) {
            return ProcessingResult.Passed(Unit, 1f)
        }

        try {
            val result = brightnessModule.callAttr(
                "main",
                provider.getAsByteArray(),
                provider.width,
                provider.height,
                brightnessConfig.darkPercentage,
                brightnessConfig.darkThreshold,
                brightnessConfig.brightPercentage,
                brightnessConfig.brightThreshold
            )

            val status = result.getStatus()
            val confidence = result.getConfidence()

            if (status == RESULT_BRIGHTNESS_OK) {
                return ProcessingResult.Passed(Unit, confidence)
            }

            val warning = when (status) {
                RESULT_BRIGHTNESS_LOW -> Warning.LowLight
                RESULT_BRIGHTNESS_HIGH -> Warning.TooBright
                else -> Warning.Internal
            }
            return ProcessingResult.Failed(warning, status, confidence)

        } catch (e: Exception) {
            return ProcessingResult.Failed(
                Warning.Internal,
                RESULT_BRIGHTNESS_LOW,
                0f,
                exception = e
            )
        }
    }
}