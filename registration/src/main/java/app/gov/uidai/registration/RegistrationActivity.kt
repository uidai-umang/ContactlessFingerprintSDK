package app.gov.uidai.registration

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.gov.uidai.registration.connectivity.ConnectivityObserver
import app.gov.uidai.registration.connectivity.ui.NoInternetScreen
import app.gov.uidai.registration.connectivity.ui.UnderMaintenanceScreen
import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.maintenance.MaintenanceStatusProvider
import app.gov.uidai.registration.ui.dashboard.DashboardRoute
import app.gov.uidai.registration.ui.registration.RegistrationRoute
import app.gov.uidai.registration.ui.registration.RegistrationViewModel
import app.gov.uidai.registration.ui.registration.method.CaptureMethodRoute
import app.gov.uidai.registration.model.SlapSubOption
import app.gov.uidai.registration.utils.toBitmap
import com.gemalto.jp2.JP2Encoder
import `in`.gov.uidai.utility.constants.JourneyConstant
import `in`.gov.uidai.utility.constants.ResultCode
import app.gov.uidai.registration.ui.theme.AttendanceAppTheme
import app.gov.uidai.registration.ui.theme.md_theme_scrim
import app.gov.uidai.registration.ui.theme.md_theme_surface
import app.gov.uidai.registration.ui.uidentry.UidEntryRoute
import app.gov.uidai.registration.usecase.DeviceUseCase
import app.gov.uidai.registration.usecase.SlapCaptureLauncher
import app.gov.uidai.registration.utils.Routes
import app.gov.uidai.registration.utils.device.DeviceRegistrationGate
import app.gov.uidai.registration.utils.worker.CaptureWorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegistrationActivity : ComponentActivity() {
    private val sharedViewModel: SharedViewModel by viewModels()
    private val registrationViewModel: RegistrationViewModel by viewModels()

    @Inject
    lateinit var deviceUseCase: DeviceUseCase

    @Inject
    lateinit var connectivityObserver: ConnectivityObserver

    @Inject
    lateinit var maintenanceStatusProvider: MaintenanceStatusProvider

    private val dummyOperatorId = "00000000-0000-0000-0000-000000000001"

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = Color.Transparent.toArgb(),
                darkScrim = md_theme_scrim.toArgb()
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = md_theme_surface.toArgb(),
                darkScrim = md_theme_scrim.toArgb()
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        sharedViewModel.initialize(this)
        CaptureWorkScheduler.schedule(this)

        if (!DeviceRegistrationGate.isRegistered(this)) {
            lifecycleScope.launch {
                val androidId =
                    Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                val result = deviceUseCase.registerDeviceIfNeeded(
                    context = this@RegistrationActivity,
                    operatorId = dummyOperatorId,
                    androidId = androidId
                )
                if (result is ApiResult.Success) {
                    DeviceRegistrationGate.markRegistered(this@RegistrationActivity)
                }
            }
        }

        setContent {
            AttendanceAppTheme {
                val isConnected by connectivityObserver.isConnected.collectAsStateWithLifecycle(
                    initialValue = true
                )
                var isUnderMaintenance by remember { mutableStateOf<Boolean?>(null) }

                LaunchedEffect(isConnected) {
                    if (isConnected) {
                        isUnderMaintenance = maintenanceStatusProvider.isUnderMaintenance()
                    }
                }

                when {
                    !isConnected -> NoInternetScreen(onRetry = {})
                    isUnderMaintenance == true -> UnderMaintenanceScreen()
                    isUnderMaintenance == null -> {}
                    else -> {
                        val navController = rememberNavController()
                        val sharedUiState by sharedViewModel.uiState.collectAsStateWithLifecycle()

                        NavHost(
                            navController = navController,
                            startDestination = Routes.Dashboard.createRoute(dummyOperatorId)
                        ) {
                            composable(Routes.UidEntry.route) {
                                UidEntryRoute(
                                    sharedUiState = sharedUiState,
                                    onClearSharedMessage = sharedViewModel::clearError,
                                    onNavigateToRegistration = { uidHash ->
                                        navController.navigate(Routes.CaptureMethod.createRoute(uidHash))
                                    }
                                )
                            }
                            composable(
                                route = Routes.CaptureMethod.route,
                                arguments = listOf(navArgument(Routes.ARG_UID_HASH) {
                                    type = NavType.StringType
                                })
                            ) { backStackEntry ->
                                val uidHash =
                                    backStackEntry.arguments?.getString(Routes.ARG_UID_HASH).orEmpty()
                                val context = LocalContext.current

                                // registrationViewModel is Activity-scoped (see field above),
                                // shared with the Registration destination below -- one
                                // resident lookup / session / capture_mode source of truth
                                // for both sequential and slap capture.
                                LaunchedEffect(uidHash) {
                                    registrationViewModel.setUidHash(uidHash)
                                }

                                // Tracks which sub-option launched the slap capture Activity,
                                // since the ActivityResultLauncher callback below doesn't get
                                // the original launch params back.
                                var pendingSlapSubOption by remember { mutableStateOf<SlapSubOption?>(null) }

                                val slapCaptureLauncher = rememberLauncherForActivityResult(
                                    ActivityResultContracts.StartActivityForResult()
                                ) { result ->
                                    val handType = pendingSlapSubOption?.let {
                                        if (it == SlapSubOption.LEFT_SLAP) "Left" else "Right"
                                    }
                                    when (result.resultCode) {
                                        ResultCode.SDK_SUCCESS -> {
                                            val uri = result.data?.data
                                            val responseXml = result.data?.getStringExtra(JourneyConstant.RESPONSE)
                                            val base64String = uri?.let {
                                                context.contentResolver.openInputStream(it)?.bufferedReader()
                                                    ?.use { reader -> reader.readText() }
                                            }
                                            if (base64String != null && handType != null) {
                                                val bitmap = base64String.toBitmap()
                                                val jp2ByteArray = JP2Encoder(bitmap).encode()
                                                val (blurScore, brightnessScore, glareScore) =
                                                    parseSlapScoresFromResponseXml(responseXml)
                                                registrationViewModel.uploadSlapResult(
                                                    handType = handType,
                                                    imageBytes = jp2ByteArray,
                                                    blurScore = blurScore,
                                                    brightnessScore = brightnessScore,
                                                    glareScore = glareScore
                                                )
                                            } else {
                                                Log.w("SlapCapture", "No image data in slap capture result")
                                            }
                                        }
                                        else -> Log.d(
                                            "SlapCapture",
                                            "Slap capture activity result: resultCode=${result.resultCode}"
                                        )
                                    }
                                }
                                CaptureMethodRoute(
                                    onNavigateUp = { navController.navigateUp() },
                                    registrationViewModel = registrationViewModel,
                                    onContinueSequential = {
                                        navController.navigate(Routes.Registration.createRoute(uidHash))
                                    },
                                    onContinueSlap = { slapSubOption ->
                                        pendingSlapSubOption = slapSubOption
                                        val intent = SlapCaptureLauncher.createIntent(
                                            context = context,
                                            purpose = "register",
                                            slapSubOption = slapSubOption
                                        )
                                        slapCaptureLauncher.launch(intent)
                                    }
                                )
                            }
                            composable(
                                route = Routes.Registration.route,
                                arguments = listOf(navArgument(Routes.ARG_UID_HASH) {
                                    type = NavType.StringType
                                })
                            ) { backStackEntry ->
                                val uidHash =
                                    backStackEntry.arguments?.getString(Routes.ARG_UID_HASH).orEmpty()
                                RegistrationRoute(
                                    uidHash = uidHash,
                                    viewModel = registrationViewModel,
                                    sharedUiState = sharedUiState,
                                    onNavigateUp = { navController.navigateUp() }
                                )
                            }
                            // Not yet reachable from any screen in this module — no
                            // operator menu/entry point exists. Wire a navigation call
                            // to Routes.Dashboard.createRoute(operatorId) once one does.
                            composable(
                                route = Routes.Dashboard.route,
                                arguments = listOf(navArgument(Routes.ARG_OPERATOR_ID) {
                                    type = NavType.StringType
                                })
                            ) { backStackEntry ->
                                val operatorId =
                                    backStackEntry.arguments?.getString(Routes.ARG_OPERATOR_ID).orEmpty()
                                DashboardRoute(
                                    operatorId = operatorId,
                                    onNavigateUp = { navController.navigateUp() },
                                    onNewCollection = { navController.navigate(Routes.UidEntry.route) }
                                )
                            }
                        }
                    }
                }

            }
        }
    }

    // Mirrors FingerSDKManagerImpl.parseScoresFromResponseXml -- slap capture
    // returns the same XML shape via JourneyConstant.RESPONSE, just without
    // going through FingerEmbedder (see SlapCaptureLauncher's doc comment).
    private fun parseSlapScoresFromResponseXml(xml: String?): Triple<Double, Double, Double> {
        if (xml == null) return Triple(0.0, 0.0, 0.0)
        fun extractAttr(name: String): Double =
            Regex("""$name="([\d.]+)"""").find(xml)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        return Triple(
            extractAttr("blurScore"),
            extractAttr("brightnessScore"),
            extractAttr("glareScore")
        )
    }
}