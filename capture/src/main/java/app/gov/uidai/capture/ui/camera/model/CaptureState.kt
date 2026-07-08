package app.gov.uidai.capture.ui.camera.model

/**
 * Represents the distinct UI states of the capture process.
 */
sealed class CaptureState {
    data object Initial : CaptureState()
    data class AutoCaptureTrigger(val info: Info) : CaptureState()
    data class AutoCaptureSuccess(val info: Info) : CaptureState()
    data class Warn(val warning: Warning) : CaptureState()
    data class Success(val info: Info, val resultValue: Stage2ResultValue? = null) : CaptureState()
    data class Failed(val error: Error, val resultValue: Stage2ResultValue? = null) : CaptureState()
}