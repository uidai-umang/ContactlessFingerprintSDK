package app.gov.uidai.capture.ui.camera

import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.gov.uidai.capture.ui.camera.view.AutoFitSurfaceView

private const val TAG = "CameraPreview"
@Composable
fun CameraPreview(
    previewSize: Size,
    onSurfaceReady: (SurfaceHolder) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    onSizeChanged: (Size) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            Log.d("CameraPreview", "factory — previewSize passed in: $previewSize")
            AutoFitSurfaceView(context).apply {
                setAspectRatio(previewSize.width, previewSize.height)
                // Real Android layout callback — fires every time this
                // view is ACTUALLY measured/laid out by the platform,
                // unlike AndroidView's `update` lambda, which only tracks
                // Compose recomposition and can fire before real layout
                // has ever happened (giving width/height = 0 permanently,
                // since nothing here ever recomposes again after that).
                addOnLayoutChangeListener { v, left, top, right, bottom, _, _, _, _ ->
                    val width = right - left
                    val height = bottom - top
                    if (width > 0 && height > 0) {
                        onSizeChanged(Size(width, height))
                    }
                }
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceChanged(
                        holder: SurfaceHolder, format: Int, width: Int, height: Int
                    ) {
                        Log.d(TAG, "surfaceChanged: ${width}x$height")
                    }

                    override fun surfaceCreated(holder: SurfaceHolder) {
                        Log.d(TAG, "surfaceCreated")
                        try {
                            onSurfaceReady(holder)
                        } catch (e: Exception) {
                            Log.e(TAG, "onSurfaceReady failed", e)
                        }
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