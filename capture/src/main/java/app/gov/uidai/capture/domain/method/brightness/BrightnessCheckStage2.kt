package app.gov.uidai.capture.domain.method.brightness

import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.domain.model.ImageProcessingMethod
import app.gov.uidai.capture.domain.model.ProcessingResult

class BrightnessCheckStage2 : ImageProcessingMethod<Unit> {

    override fun run(provider: ImageDataProvider): ProcessingResult<Unit> {
        val nv21 = provider.getAsByteArray()
        val width = provider.width
        val height = provider.height
        val yRowStride = provider.width // FIX: Use the provider's width as the stride

        var sum = 0L
        var count = 0

        var rowOffset = 0
        for (row in 0 until height) {
            for (col in 0 until width) {
                sum += nv21[rowOffset + col].toInt() and 0xFF
            }
            rowOffset += yRowStride
            count += width
        }

        val avgLuma = (if (count == 0) 0.0 else sum.toDouble() / count).toInt()
        val confidence = avgLuma / 255f

        // This method doesn't have its own settings, so we can't get min/max threshold.
        // The calling code in ManualCaptureImageProcessor will have to check the confidence.
        // For now, we'll just return the result.
        // A better long-term solution might be to pass thresholds into the run method.

        return ProcessingResult.Passed(
            data = Unit,
            confidence = confidence
        )
    }
}