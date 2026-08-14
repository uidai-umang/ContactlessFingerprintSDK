package app.gov.uidai.capture

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.gov.uidai.capture.ui.CaptureRoutes
import app.gov.uidai.capture.ui.camera.CameraController
import app.gov.uidai.capture.ui.camera.CameraScreen
import app.gov.uidai.capture.ui.camera.CaptureResult
import app.gov.uidai.capture.ui.guideline.GuidelineScreen
import app.gov.uidai.capture.ui.settings.DebugSettingsScreen
import app.gov.uidai.capture.pref.PreferenceStore
import app.gov.uidai.capture.usecase.factory.ImageProcessorFactory
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dagger.hilt.android.AndroidEntryPoint
import `in`.gov.uidai.network.model.local.PidOptions
import `in`.gov.uidai.network.model.local.SDKResponse
import `in`.gov.uidai.utility.constants.JourneyConstant
import `in`.gov.uidai.utility.constants.ResultCode
import `in`.gov.uidai.utility.mapper.XmlMapper
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class CaptureActivity : ComponentActivity() {

    // Injected here (Activity-scoped) since CameraScreen currently takes
    // these as direct parameters rather than resolving them via
    // hiltViewModel() internally.
    @Inject lateinit var cameraController: CameraController
    @Inject lateinit var imageProcessorFactory: ImageProcessorFactory
    @Inject lateinit var preferenceStore: PreferenceStore

    private var txnId: String = ""
    private var returnFullImage: Boolean = false
    private var returnCroppedImage: Boolean = false
    private var fingerType: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        // Unchanged from before — parse PID options BEFORE any UI is shown
        val req = intent.getStringExtra(JourneyConstant.REQUEST)
        if (req == null) {
            Toast.makeText(this, "No Input PID Options Provided!", Toast.LENGTH_LONG).show()
            throw IllegalStateException()
        }
        try {
            val pidOptions: PidOptions = XmlMapper.read(req)
            txnId = pidOptions.custOpts?.param?.find { it.name == "txnId" }?.value.orEmpty()
            returnFullImage = pidOptions.custOpts?.param?.find { it.name == "fullImage" }?.value == "true"
            returnCroppedImage = pidOptions.custOpts?.param?.find { it.name == "croppedImage" }?.value == "true"
            fingerType = pidOptions.custOpts?.param?.find { it.name == "fingerType" }?.value.orEmpty()
            Log.d(TAG, "Transaction Id: $txnId")
        } catch (_: Exception) {
            Toast.makeText(this, "Wrong Input PID Options Provided!", Toast.LENGTH_LONG).show()
            throw IllegalStateException()
        }

        setContent {
            val navController = rememberNavController()

            NavHost(navController = navController, startDestination = CaptureRoutes.Guideline.createRoute(txnId)) {
                composable(
                    route = CaptureRoutes.Guideline.route,
                    arguments = listOf(navArgument(CaptureRoutes.ARG_TXN_ID) { type = NavType.StringType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString(CaptureRoutes.ARG_TXN_ID).orEmpty()
                    GuidelineScreen(
                        txnId = id,
                        onBack = { handleCaptureResult(CaptureResult(resultCode = ResultCode.CAPTURE_USER_ABORT)) },
                        onProceed = { proceedTxnId ->
                            navController.navigate(CaptureRoutes.Camera.createRoute(proceedTxnId, fingerType))
                        },
                        onDebugSettings = { navController.navigate(CaptureRoutes.DebugSettings.route) }
                    )
                }
                composable(
                    route = CaptureRoutes.Camera.route,
                    arguments = listOf(navArgument(CaptureRoutes.ARG_TXN_ID) { type = NavType.StringType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString(CaptureRoutes.ARG_TXN_ID).orEmpty()
                    val finger = backStackEntry.arguments?.getString(CaptureRoutes.ARG_FINGER_TYPE).orEmpty()  // ADDED
                    CameraScreen(
                        txnId = id,
                        fingerType = finger,
                        cameraController = cameraController,
                        imageProcessorFactory = imageProcessorFactory,
                        preferenceStore = preferenceStore,
                        onFinish = { result -> handleCaptureResult(result) }
                    )
                }
                composable(CaptureRoutes.DebugSettings.route) {
                    DebugSettingsScreen(onNavigateUp = { navController.navigateUp() })
                }
            }
        }
    }

    // Direct replacement for the old FragmentResultListener — same exact
    // logic, now reading from CaptureResult's typed fields instead of a
    // Bundle. External contract (setResult + finish) is UNCHANGED.
    private fun handleCaptureResult(result: CaptureResult) {
        var sdkResponse = SDKResponse()

        when (result.resultCode) {
            ResultCode.CAPTURE_SUCCESS -> {
                val finalImageUri = saveBase64ToFile(this, "final_image", result.finalImage)

                if (returnFullImage) {
                    val fullImageUri = saveBase64ToFile(this, "full_image", result.fullImage)
                    sdkResponse = sdkResponse.copy(fullImage = fullImageUri.toString())
                }
                if (returnCroppedImage) {
                    val croppedImageUri = saveBase64ToFile(this, "cropped_image", result.croppedImage)
                    sdkResponse = sdkResponse.copy(croppedImage = croppedImageUri.toString())
                }

                sdkResponse = sdkResponse.copy(
                    blurScore = result.blurScore,
                    brightnessScore = result.brightnessScore,
                    glareScore = result.glareScore
                )

                Log.d(TAG, XmlMapper.writePretty(sdkResponse))
                Log.d(TAG, "ACTIVITY_RESULT -- OK -- Data: $finalImageUri")

                val resultIntent = Intent().apply {
                    data = finalImageUri
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    putExtra(JourneyConstant.RESPONSE, XmlMapper.write(sdkResponse))
                }
                setResult(ResultCode.CAPTURE_SUCCESS, resultIntent)
            }
            else -> {
                Log.d(TAG, "ACTIVITY_RESULT -- Failed with code: ${result.resultCode}")
                setResult(result.resultCode)
            }
        }
        finish()
    }

    fun saveBase64ToFile(context: Context, name: String, base64String: String?): Uri {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "$name.txt")
        file.outputStream().bufferedWriter().use { writer -> writer.write(base64String) }
        return FileProvider.getUriForFile(context, "${context.packageName}.file_provider", file)
    }

    companion object {
        private val TAG = CaptureActivity::class.simpleName
    }
}