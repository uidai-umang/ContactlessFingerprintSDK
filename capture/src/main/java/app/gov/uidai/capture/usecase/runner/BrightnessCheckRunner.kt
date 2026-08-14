package app.gov.uidai.capture.usecase.runner

import app.gov.uidai.capture.domain.method.brightness.BrightnessCheck
import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.domain.model.ProcessingResult
import app.gov.uidai.capture.utils.RollingConfidence

class BrightnessCheckRunner(private val brightnessCheck: BrightnessCheck) {
    private val confidence = RollingConfidence(windowSize = 10, requiredPassRate = 0.7f)

    fun run(provider: ImageDataProvider): ProcessingResult<*> {
        val result = brightnessCheck.run(provider)
        confidence.record(result.passed)
        return result
    }

    fun isConfident(): Boolean = confidence.isConfident()
}