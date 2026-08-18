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
import app.gov.uidai.registration.connectivity.ConnectivityObserver
import app.gov.uidai.registration.connectivity.ui.NoInternetScreen
import app.gov.uidai.registration.connectivity.ui.UnderMaintenanceScreen
import app.gov.uidai.registration.data.remote.network.ApiResult
import app.gov.uidai.registration.maintenance.MaintenanceStatusProvider
import app.gov.uidai.registration.ui.registration.RegistrationRoute
import app.gov.uidai.registration.ui.theme.AttendanceAppTheme
import app.gov.uidai.registration.ui.theme.md_theme_scrim
import app.gov.uidai.registration.ui.theme.md_theme_surface
import app.gov.uidai.registration.ui.uidentry.UidEntryRoute
import app.gov.uidai.registration.usecase.DeviceUseCase
import app.gov.uidai.registration.utils.Routes
import app.gov.uidai.registration.utils.device.DeviceRegistrationGate
import app.gov.uidai.registration.utils.worker.CaptureWorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegistrationActivity : ComponentActivity() {
    private val sharedViewModel: SharedViewModel by viewModels()

    @Inject
    lateinit var deviceUseCase: DeviceUseCase

    @Inject
    lateinit var connectivityObserver: ConnectivityObserver

    @Inject
    lateinit var maintenanceStatusProvider: MaintenanceStatusProvider

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
                    operatorId = "00000000-0000-0000-0000-000000000001",
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
                            startDestination = Routes.UidEntry.route
                        ) {
                            composable(Routes.UidEntry.route) {
                                UidEntryRoute(
                                    sharedUiState = sharedUiState,
                                    onClearSharedMessage = sharedViewModel::clearError,
                                    onNavigateToRegistration = { uidHash ->
                                        navController.navigate(Routes.Registration.createRoute(uidHash))
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
                                    sharedUiState = sharedUiState,
                                    onNavigateUp = { navController.navigateUp() }
                                )
                            }
                        }
                    }
                }

            }
        }
    }
}