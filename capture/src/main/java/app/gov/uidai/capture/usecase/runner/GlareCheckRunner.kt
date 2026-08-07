package app.gov.uidai.capture.usecase.runner

import app.gov.uidai.capture.domain.method.glare.GlareCheck
import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.domain.model.ProcessingResult
import app.gov.uidai.capture.utils.RollingConfidence

class GlareCheckRunner(private val glareCheck: GlareCheck) {
    private val confidence = RollingConfidence(windowSize = 10, requiredPassRate = 0.9f)

    fun run(provider: ImageDataProvider): ProcessingResult<*> {
        val result = glareCheck.run(provider)
        confidence.record(result.passed)
        return result
    }

    fun isConfident(): Boolean = confidence.isConfident()
}