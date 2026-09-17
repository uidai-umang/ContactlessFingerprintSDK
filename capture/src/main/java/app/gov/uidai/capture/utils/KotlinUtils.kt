package app.gov.uidai.capture.utils

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import android.view.WindowManager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gov.uidai.capture.ui.camera.CameraViewModel
import app.gov.uidai.capture.ui.camera.model.CaptureState
import `in`.gov.uidai.utility.extension.toYYYYMMDDHHmmss
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

object KotlinUtils {
    // small, standalone helpers — could live in a Utils.kt in this package

    private const val PATH = "/ContactlessFingerSDK"
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }


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


    suspend fun save(bitmap: Bitmap, captureName: String): Uri? =
        withContext(Dispatchers.IO) {
            if (!::appContext.isInitialized) {
                android.util.Log.e(
                    "DebugImageSaver",
                    "save() called before init() -- dropping $captureName"
                )
                return@withContext null
            }
            try {
                val timeStamp = Date().toYYYYMMDDHHmmss()
                val fileName = "${captureName}_$timeStamp.jpg"

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + PATH
                        )
                    }
                }

                val uri = appContext.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )
                uri?.let {
                    appContext.contentResolver.openOutputStream(it)?.use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                    }
                }
                uri
            } catch (e: Exception) {
                android.util.Log.e("DebugImageSaver", "Failed to save $captureName", e)
                null
            }
        }


}