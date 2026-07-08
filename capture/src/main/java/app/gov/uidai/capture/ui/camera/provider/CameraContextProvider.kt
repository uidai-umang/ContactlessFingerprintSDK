package app.gov.uidai.capture.ui.camera.provider

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.Handler

interface CameraContextProvider {
    val characteristics: CameraCharacteristics
    val captureSession: CameraCaptureSession
    val captureRequestBuilder: CaptureRequest.Builder
    val captureCallback: CameraCaptureSession.CaptureCallback
    val cameraPreviewHandler: Handler
}