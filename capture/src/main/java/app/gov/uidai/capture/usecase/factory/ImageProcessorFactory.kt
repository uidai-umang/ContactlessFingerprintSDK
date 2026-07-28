package app.gov.uidai.capture.usecase.factory

import androidx.lifecycle.LifecycleCoroutineScope
import app.gov.uidai.capture.pref.PreferenceStore
import app.gov.uidai.capture.ui.camera.config.CameraSettings
import app.gov.uidai.capture.usecase.AutoCaptureImageProcessor
import app.gov.uidai.capture.usecase.ImageProcessor
import app.gov.uidai.capture.usecase.ManualCaptureImageProcessor
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageProcessorFactory @Inject constructor(
    private val preferenceStore: PreferenceStore,
    private val autoCaptureImageProcessorFactory: AutoCaptureImageProcessor.Factory,
    private val manualCaptureImageProcessorFactory: ManualCaptureImageProcessor.Factory
) {
    fun create(
        coroutineScope: CoroutineScope,
        provider: ImageProcessor.Provider,
        controller: ImageProcessor.Controller,
        listener: ImageProcessor.Listener
    ): ImageProcessor {
        val isManualCapture = preferenceStore.get(CameraSettings.MANUAL_CAPTURE)

        return if(isManualCapture){
            manualCaptureImageProcessorFactory.create(
                coroutineScope, provider, controller, listener
            )
        } else {
            autoCaptureImageProcessorFactory.create(
                coroutineScope, provider, controller, listener
            )
        }
    }
}