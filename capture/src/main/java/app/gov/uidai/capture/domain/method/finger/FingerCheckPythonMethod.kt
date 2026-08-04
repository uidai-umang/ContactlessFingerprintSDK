package app.gov.uidai.capture.domain.method.finger

import android.graphics.RectF
import androidx.compose.ui.graphics.toAndroidRectF
import app.gov.uidai.capture.BuildConfig
import app.gov.uidai.capture.domain.config.FingerConfig
import app.gov.uidai.capture.domain.config.FingerResultStatus
import app.gov.uidai.capture.domain.model.FingerResultData
import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.domain.model.ImageProcessingMethod
import app.gov.uidai.capture.domain.model.ProcessingResult
import app.gov.uidai.capture.domain.model.Warning
import app.gov.uidai.capture.utils.extension.getBox
import app.gov.uidai.capture.utils.extension.getConfidence
import app.gov.uidai.capture.utils.extension.getStatus
import com.chaquo.python.Python

class FingerCheckPythonMethod(
    private val fingerConfig: FingerConfig
) : ImageProcessingMethod<FingerResultData> {

    companion object {
        private val TAG = FingerCheckPythonMethod::class.simpleName
    }

    private val py = Python.getInstance()
    private val fingerDetectorHSVModule = py.getModule("finger_detector_hsv")

    init {
        fingerDetectorHSVModule.callAttr("set_debug_enabled", BuildConfig.DEBUG)
    }

    override fun run(provider: ImageDataProvider): ProcessingResult<FingerResultData> {
        if (!fingerConfig.enabled) {
            return ProcessingResult.Passed(
                FingerResultData(RectF()), 1f
            )
        }

        try {
            val byteArray = provider.getAsByteArray()
            val result = fingerDetectorHSVModule.callAttr(
                "main",
                byteArray,
                provider.width,
                provider.height,
                provider.rotationDegrees,
                fingerConfig.minFingerArea,
                fingerConfig.maxFingerArea,
                fingerConfig.goodAreaMin,
                fingerConfig.goodAreaMax,
                fingerConfig.textureThresholdMin,
                fingerConfig.textureThresholdMax,
                fingerConfig.edgeDensityThresholdMin,
                fingerConfig.edgeDensityThresholdMax
            )

            val status = result.getStatus()
            val confidence = result.getConfidence()
            val boundingBox = result.getBox()

            val passed = (status == FingerResultStatus.RESULT_FINGER_OK)
            if (passed) {
                return ProcessingResult.Passed(
                    data = FingerResultData(box = boundingBox.toAndroidRectF()),
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
            return ProcessingResult.Failed(
                cause = warning,
                status = status,
                confidence = confidence
            )
        } catch (e: Exception) {
            return ProcessingResult.Failed(
                cause = Warning.Internal,
                status = FingerResultStatus.RESULT_FINGER_NOT_DETECTED,
                confidence = 0f,
                exception =  e
            )
        }
    }
}