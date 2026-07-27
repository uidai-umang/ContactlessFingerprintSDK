package app.gov.uidai.capture.ui.camera

import android.content.ContentValues.TAG
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.gov.uidai.capture.ui.camera.view.AutoFitSurfaceView

@Composable
fun CameraPreview(
    previewSize: Size,
    onSurfaceReady: (SurfaceHolder) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            Log.d(TAG, "AndroidView factory invoked — creating AutoFitSurfaceView")
            AutoFitSurfaceView(context).apply {
                setAspectRatio(previewSize.width, previewSize.height)
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceChanged(
                        holder: SurfaceHolder, format: Int, width: Int, height: Int
                    ) {
                        Log.d(TAG, "surfaceChanged: ${width}x$height")
                    }

                    override fun surfaceCreated(holder: SurfaceHolder) {
                        Log.d(TAG, "surfaceCreated")
                        onSurfaceReady(holder)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        Log.d(TAG, "surfaceDestroyed")
                        onSurfaceDestroyed()
                    }

                })
            }
        },
        modifier = modifier
    )
}