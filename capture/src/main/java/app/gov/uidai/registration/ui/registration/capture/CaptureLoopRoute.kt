package app.gov.uidai.registration.ui.registration.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gov.uidai.capture.pref.PreferenceStore
import app.gov.uidai.capture.ui.camera.CameraController
import app.gov.uidai.capture.ui.camera.CameraScreen
import app.gov.uidai.capture.ui.camera.CameraViewModel
import app.gov.uidai.capture.ui.camera.slap.SlapCaptureRoute
import app.gov.uidai.capture.ui.camera.slap.SlapCaptureViewModel
import app.gov.uidai.capture.usecase.factory.ImageProcessorFactory
import app.gov.uidai.registration.model.FingerPosition
import app.gov.uidai.registration.ui.registration.RegistrationViewModel
import javax.inject.Provider

/**
 * Capture loop: renders the camera for [RegistrationViewModel.activeCapture]
 * and hands each result over in memory. The ViewModel starts the upload and
 * moves to the next position; the loop leaves (via [onExit]) once the
 * sequence ends or is aborted.
 *
 * CameraController is single-use (closeCamera() stops its threads), so each
 * segment -- a run of same-type captures -- gets a fresh controller and
 * camera ViewModel. Within a segment the screen stays mounted and resets
 * itself when the capture id changes.
 */
@Composable
fun CaptureLoopRoute(
    viewModel: RegistrationViewModel,
    cameraControllerProvider: Provider<CameraController>,
    imageProcessorFactory: ImageProcessorFactory,
    preferenceStore: PreferenceStore,
    onExit: () -> Unit
) {
    val active by viewModel.activeCapture.collectAsStateWithLifecycle()
    val capture = active

    LaunchedEffect(capture == null) {
        if (capture == null) onExit()
    }
    if (capture == null) return

    key(capture.segment) {
        when (capture.position) {
            FingerPosition.LEFT_SLAP, FingerPosition.RIGHT_SLAP -> {
                SlapCaptureRoute(
                    handType = if (capture.position == FingerPosition.LEFT_SLAP) "Left" else "Right",
                    captureId = capture.id,
                    onFinish = viewModel::onCaptureResult,
                    onPopBackStack = viewModel::cancelSequence,
                    viewModel = hiltViewModel<SlapCaptureViewModel>(key = "slap-${capture.segment}")
                )
            }
            else -> {
                val cameraController = remember { cameraControllerProvider.get() }
                CameraScreen(
                    txnId = capture.txnId,
                    fingerType = capture.position.name,
                    captureId = capture.id,
                    cameraController = cameraController,
                    imageProcessorFactory = imageProcessorFactory,
                    preferenceStore = preferenceStore,
                    onFinish = viewModel::onCaptureResult,
                    onPopBackStack = viewModel::cancelSequence,
                    viewModel = hiltViewModel<CameraViewModel>(key = "camera-${capture.segment}")
                )
            }
        }
    }
}
