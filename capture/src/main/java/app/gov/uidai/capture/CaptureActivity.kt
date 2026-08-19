package app.gov.uidai.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
    @Inject lateinit var cameraController: CameraController
    @Inject lateinit var imageProcessorFactory: ImageProcessorFactory
    @Inject lateinit var preferenceStore: PreferenceStore

    private var txnId: String = ""
    private var returnFullImage: Boolean = false
    private var returnCroppedImage: Boolean = false
    private var fingerType: String = ""

    companion object {
        private val TAG = CaptureActivity::class.simpleName

        private val REQUIRED_PERMISSIONS = buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    private var showPermanentlyDeniedState = mutableStateOf(false)

    private val permissionLauncher: ActivityResultLauncher<Array<String>> = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (!allGranted) {
            val anyPermanentlyDenied = results.filterValues { !it }.keys.any { permission ->
                !ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
            }
            if (anyPermanentlyDenied) {
                showPermanentlyDeniedState.value = true
            } else {
                permissionLauncher.launch(REQUIRED_PERMISSIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

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

        checkAndRequestPermissions()

        setContent {
            val showPermanentlyDenied by showPermanentlyDeniedState

            Box(modifier = Modifier.fillMaxSize()) {
                CaptureNavHost()

                if (showPermanentlyDenied) {
                    PermanentlyDeniedDialog(
                        onOpenSettings = {
                            showPermanentlyDeniedState.value = false
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", packageName, null)
                            )
                            startActivity(intent)
                        },
                        onCancel = {
                            handleCaptureResult(CaptureResult(resultCode = ResultCode.CAPTURE_USER_ABORT))
                        }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        if (showPermanentlyDeniedState.value) {
            val allGranted = REQUIRED_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
            if (allGranted) {
                showPermanentlyDeniedState.value = false
            }
        }
    }

    @Composable
    private fun PermanentlyDeniedDialog(onOpenSettings: () -> Unit, onCancel: () -> Unit) {
        MaterialTheme {
            AlertDialog(
                onDismissRequest = { /* must choose -- no dismiss without action */ },
                title = { Text("Permissions Required") },
                text = {
                    Text(
                        "This app needs Camera access to capture fingerprints. " +
                                "Please enable it in Settings to continue."
                    )
                },
                confirmButton = {
                    TextButton(onClick = onOpenSettings) { Text("Open Settings") }
                },
                dismissButton = {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            )
        }
    }

    @Composable
    private fun CaptureNavHost() {
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
                val finger = backStackEntry.arguments?.getString(CaptureRoutes.ARG_FINGER_TYPE).orEmpty()
                CameraScreen(
                    txnId = id,
                    fingerType = finger,
                    cameraController = cameraController,
                    imageProcessorFactory = imageProcessorFactory,
                    preferenceStore = preferenceStore,
                    onPopBackStack = { navController.popBackStack() },
                    onFinish = { result -> handleCaptureResult(result) }
                )
            }
            composable(CaptureRoutes.DebugSettings.route) {
                DebugSettingsScreen(onNavigateUp = { navController.navigateUp() })
            }
        }
    }

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
}