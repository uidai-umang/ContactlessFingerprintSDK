package app.gov.uidai.capture.domain.method.blur

import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.domain.model.ImageProcessingMethod
import app.gov.uidai.capture.domain.model.ProcessingResult
import app.gov.uidai.capture.domain.model.Warning
import com.chaquo.python.Python

// Fast, classical, live-only blur check. NOT a replacement for DenseNet —
// DensenetBlurMethod remains the sole authority on the final captured
// frame (Stage 2). This exists purely to unblock the live gate quickly,
// mirroring the finger-detection live/precise split approved this morning.
// Confidence scale is raw Laplacian variance, NOT 0-1 like DenseNet's —
// do not compare thresholds across the two methods.
class LaplacianBlurMethod(
    private val minVariance: Float
) : ImageProcessingMethod<Unit> {

    private val py = Python.getInstance()
    private val laplacianModule = py.getModule("blur_detector_laplacian")

    override fun run(provider: ImageDataProvider): ProcessingResult<Unit> {
        try {
            val byteArray = provider.getAsByteArray()
            val variance = laplacianModule.callAttr(
                "main", byteArray, provider.width, provider.height
            ).toFloat()

            val passed = variance >= minVariance

            return if (passed) {
                ProcessingResult.Passed(data = Unit, confidence = variance)
            } else {
                ProcessingResult.Failed(cause = Warning.Blur, status = -1, confidence = variance)
            }
        } catch (e: Exception) {
            return ProcessingResult.Failed(
                cause = app.gov.uidai.capture.domain.model.Error.SomethingWentWrong,
                status = -1, confidence = 0f, exception = e
            )
        }
    }
}