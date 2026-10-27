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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.gov.uidai.capture.BuildConfig
import app.gov.uidai.capture.pref.PreferenceStore
import app.gov.uidai.capture.ui.camera.CameraController
import app.gov.uidai.capture.ui.guideline.GuidelineScreen
import app.gov.uidai.capture.ui.settings.DebugSettingsScreen
import app.gov.uidai.capture.usecase.factory.ImageProcessorFactory
import app.gov.uidai.capture.utils.MainThreadWatchdog
import app.gov.uidai.registration.connectivity.ConnectivityObserver
import app.gov.uidai.registration.connectivity.ui.NoInternetScreen
import app.gov.uidai.registration.connectivity.ui.UnderMaintenanceScreen
import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.maintenance.MaintenanceStatusProvider
import app.gov.uidai.registration.ui.dashboard.DashboardRoute
import app.gov.uidai.registration.ui.operator.RegisterOperatorRoute
import app.gov.uidai.registration.ui.registration.RegistrationRoute
import app.gov.uidai.registration.ui.registration.RegistrationViewModel
import app.gov.uidai.registration.ui.registration.capture.CaptureLoopRoute
import app.gov.uidai.registration.ui.registration.method.NewCaptureMethodRoute
import app.gov.uidai.registration.ui.theme.AttendanceAppTheme
import app.gov.uidai.registration.ui.theme.Colors
import app.gov.uidai.registration.ui.uidentry.UidEntryRoute
import app.gov.uidai.registration.usecase.DeviceUseCase
import app.gov.uidai.registration.utils.Routes
import app.gov.uidai.registration.utils.device.DeviceRegistrationGate
import app.gov.uidai.registration.utils.residentInput
import app.gov.uidai.registration.utils.residentNavArguments
import app.gov.uidai.registration.utils.worker.CaptureWorkScheduler
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.launch
import javax.inject.Provider

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

    // Capture loop dependencies.
    @Inject
    lateinit var cameraControllerProvider: Provider<CameraController>

    @Inject
    lateinit var imageProcessorFactory: ImageProcessorFactory

    @Inject
    lateinit var preferenceStore: PreferenceStore

    companion object {
        const val EXTRA_OPERATOR_REF_ID = "operator_ref_id"   // host app passes this
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) MainThreadWatchdog.start()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = Color.Transparent.toArgb(),
                darkScrim = Colors.Background.Tertiary.toArgb()
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = Colors.Background.Primary.toArgb(),
                darkScrim = Colors.Background.Tertiary.toArgb()
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

        // Debug builds save diagnostic images to Pictures/, which needs this pre-Q.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        sharedViewModel.initialize(this)
        CaptureWorkScheduler.schedule(this)

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
                            startDestination = Routes.RegisterOperator.route
                        ) {
                            composable(Routes.RegisterOperator.route) {
                                RegisterOperatorRoute(
                                    initialOperatorRefId = intent.getStringExtra(
                                        EXTRA_OPERATOR_REF_ID
                                    ),
                                    onRegistered = { operatorId ->
                                        registerDeviceIfNeeded(operatorId)
                                        navController.navigate(
                                            Routes.Dashboard.createRoute(
                                                operatorId
                                            )
                                        ) {
                                            popUpTo(Routes.RegisterOperator.route) {
                                                inclusive = true
                                            }
                                        }
                                    }
                                )
                            }
                            composable(Routes.UidEntry.route) {
                                UidEntryRoute(
                                    sharedUiState = sharedUiState,
                                    onClearSharedMessage = sharedViewModel::clearError,
                                    onNavigateToRegistration = { resident ->
                                        if (registrationViewModel.isConsented) {
                                            navController.navigate(
                                                Routes.CaptureMethod.createRoute(
                                                    resident
                                                )
                                            ) {
                                                popUpTo(Routes.UidEntry.route) { inclusive = true }
                                            }
                                        } else {
                                            navController.navigate(
                                                Routes.Guideline.createRoute(
                                                    resident
                                                )
                                            )
                                        }
                                    }
                                )
                            }
                            composable(
                                route = Routes.Guideline.route,
                                arguments = residentNavArguments
                            ) { backStackEntry ->
                                val resident = backStackEntry.residentInput()
                                GuidelineScreen(
                                    txnId = "",
                                    onBack = { navController.navigateUp() },
                                    onProceed = {
                                        // GuidelineScreen sets isLoading on proceed and never
                                        // resets it, so it must not stay on the back stack.
                                        registrationViewModel.postResidentConsent()
                                        navController.navigate(
                                            Routes.CaptureMethod.createRoute(
                                                resident
                                            )
                                        ) {
                                            popUpTo(Routes.Guideline.route) { inclusive = true }
                                        }
                                    },
                                    onDebugSettings = { navController.navigate(Routes.DebugSettings.route) }
                                )
                            }
                            composable(Routes.DebugSettings.route) {
                                DebugSettingsScreen(onNavigateUp = { navController.navigateUp() })
                            }
                            composable(
                                route = Routes.CaptureMethod.route,
                                arguments = residentNavArguments
                            ) { backStackEntry ->
                                val resident = backStackEntry.residentInput()

                                LaunchedEffect(resident) {
                                    registrationViewModel.setResident(resident)
                                }

                                NewCaptureMethodRoute(
                                    registrationViewModel = registrationViewModel,
                                    onNavigateUp = { navController.navigateUp() },
                                    onStartCaptureLoop = {
                                        navController.navigate(Routes.CaptureLoop.route) {
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                            composable(Routes.CaptureLoop.route) {
                                CaptureLoopRoute(
                                    viewModel = registrationViewModel,
                                    cameraControllerProvider = cameraControllerProvider,
                                    imageProcessorFactory = imageProcessorFactory,
                                    preferenceStore = preferenceStore,
                                    // Back to whichever screen launched the loop, statuses intact.
                                    onExit = {
                                        navController.popBackStack(
                                            Routes.CaptureLoop.route,
                                            inclusive = true
                                        )
                                    }
                                )
                            }
                            composable(
                                route = Routes.Registration.route,
                                arguments = residentNavArguments
                            ) { backStackEntry ->
                                RegistrationRoute(
                                    resident = backStackEntry.residentInput(),
                                    viewModel = registrationViewModel,
                                    sharedUiState = sharedUiState,
                                    onNavigateUp = { navController.navigateUp() },
                                    onStartCaptureLoop = {
                                        navController.navigate(Routes.CaptureLoop.route) {
                                            launchSingleTop = true
                                        }
                                    }
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
                                    backStackEntry.arguments?.getString(Routes.ARG_OPERATOR_ID)
                                        .orEmpty()
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

    private fun registerDeviceIfNeeded(operatorId: String) {
        if (DeviceRegistrationGate.isRegistered(this)) return
        lifecycleScope.launch {
            val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            val result = deviceUseCase.registerDeviceIfNeeded(
                context = this@RegistrationActivity,
                operatorId = operatorId,
                androidId = androidId
            )
            if (result is ApiResult.Success) {
                DeviceRegistrationGate.markRegistered(this@RegistrationActivity)
            }
        }
    }
}
