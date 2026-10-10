package app.gov.uidai.capture.utils

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gov.uidai.capture.ui.camera.CameraViewModel
import app.gov.uidai.capture.ui.camera.model.CaptureState

object KotlinUtils {
    // small, standalone helpers — could live in a Utils.kt in this package

    fun getDeviceRotationCompat(context: Context): Int {
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            (context as? Activity)?.display?.rotation ?: 0
        } else {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION") wm.defaultDisplay.rotation
        }
        return when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    fun RoundedCornerShapeCompat() = RoundedCornerShape(30.dp)

    @Composable
    fun headingTextFor(state: CaptureState, viewModel: CameraViewModel): String = when (state) {
        is CaptureState.Initial -> "Place your finger inside the overlay"
        is CaptureState.AutoCaptureTrigger -> "Hold steady"
        is CaptureState.AutoCaptureSuccess -> "Evaluating..."
        is CaptureState.Success -> "Captured"
        is CaptureState.Failed -> "Capture failed"
        is CaptureState.Warn -> stringResource(state.warning.titleRes)
    }
}