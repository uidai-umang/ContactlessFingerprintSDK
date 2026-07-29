package app.gov.uidai.capture.ui.camera

import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
                onSizeChanged(Size(width, height))
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
//    AndroidView(
//        factory = { context ->
//            Log.d("CameraPreview", "factory — previewSize passed in: $previewSize")
//            AutoFitSurfaceView(context).apply {
//                setAspectRatio(previewSize.width, previewSize.height)
//                // Real Android layout callback — fires every time this
//                // view is ACTUALLY measured/laid out by the platform,
//                // unlike AndroidView's `update` lambda, which only tracks
//                // Compose recomposition and can fire before real layout
//                // has ever happened (giving width/height = 0 permanently,
//                // since nothing here ever recomposes again after that).
//                addOnLayoutChangeListener { v, left, top, right, bottom, _, _, _, _ ->
//                    val width = right - left
//                    val height = bottom - top
//                    if (width > 0 && height > 0) {
//                        onSizeChanged(Size(width, height))
//                    }
//                }
//                holder.addCallback(object : SurfaceHolder.Callback {
//                    override fun surfaceChanged(
//                        holder: SurfaceHolder, format: Int, width: Int, height: Int
//                    ) {
//                        Log.d(TAG, "surfaceChanged: ${width}x$height")
//                    }
//
//                    override fun surfaceCreated(holder: SurfaceHolder) {
//                        Log.d(TAG, "surfaceCreated")
//                        try {
//                            onSurfaceReady(holder)
//                        } catch (e: Exception) {
//                            Log.e(TAG, "onSurfaceReady failed", e)
//                        }
//                    }
//
//                    override fun surfaceDestroyed(holder: SurfaceHolder) {
//                        Log.d(TAG, "surfaceDestroyed")
//                        onSurfaceDestroyed()
//                    }
//
//                })
//            }
//        },
//        modifier = modifier
//    )
}