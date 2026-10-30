package app.gov.uidai.registration.ui.registration.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import app.gov.uidai.registration.ui.theme.Colors
import app.gov.uidai.registration.ui.theme.Spacing
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
    val upNext by viewModel.upNext.collectAsStateWithLifecycle()
    val capture = active

    LaunchedEffect(capture == null) {
        if (capture == null) onExit()
    }
    if (capture == null) return

    Box(modifier = Modifier.fillMaxSize()) {
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

        upNext?.let { NextCaptureCue(it) }
    }
}

@Composable
private fun NextCaptureCue(next: FingerPosition) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Colors.Background.Tertiary.copy(alpha = 0.7f))
            // swallow taps so nothing underneath is hit during the pause
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(Colors.Background.Primary, RoundedCornerShape(16.dp))
                .padding(horizontal = Spacing.Xxl, vertical = Spacing.L),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.Xxs)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Colors.Success.Main,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Capture saved",
                    color = Colors.Success.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Next: ${
                    next.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
                }",
                color = Colors.Text.Primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
