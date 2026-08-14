package app.gov.uidai.capture.ui.camera

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.viewinterop.AndroidView
import app.gov.uidai.capture.ui.camera.focus.FocusState
import app.gov.uidai.capture.ui.camera.view.AutoFitSurfaceView
import app.gov.uidai.capture.utils.convertCroppedRectToSensorMeteringRect

private const val TAG = "CameraPreview"
@Composable
fun CameraPreview(
    previewSize: Size,
    onSurfaceReady: (SurfaceHolder) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    onSizeChanged: (Size) -> Unit,
    onTapToFocus: (tapOffset: Offset, previewViewSize: Size) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var boxSize by remember { mutableStateOf(Size(0, 0)) }

    Box(modifier = modifier
        .fillMaxSize()
        // Report THIS Box's real on-screen bounds — same boundsInRoot()
        // method CaptureOverlay's oval uses. AutoFitSurfaceView's own
        // measured size (via addOnLayoutChangeListener) is deliberately
        // LARGER than the screen for aspect-ratio-correct center-crop —
        // using that as viewFinderSize put the coordinate math in a
        // different, incompatible space from the oval's real position,
        // causing a systematic, silent crop-offset bug.
        .onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInRoot()
            val width = bounds.width.toInt()
            val height = bounds.height.toInt()
            if (width > 0 && height > 0) {
                boxSize = Size(width,height)
                onSizeChanged(Size(width, height))
            }
        }
        .pointerInput(Unit) {
            detectTapGestures { tapOffset ->
                onTapToFocus(tapOffset, boxSize)
            }
        }
    ){
        AndroidView(
            factory = { context ->
                Log.d(TAG, "factory — previewSize passed in: $previewSize")
                AutoFitSurfaceView(context).apply {
                    setAspectRatio(previewSize.width, previewSize.height)
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            try {
                                onSurfaceReady(holder)
                            } catch (e: Exception) {
                                Log.e(TAG, "onSurfaceReady failed", e)
                            }
                        }
                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            onSurfaceDestroyed()
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}