package app.gov.uidai.registration.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gov.uidai.registration.model.DashboardTab
import app.gov.uidai.registration.model.DashboardUiState
import app.gov.uidai.registration.ui.dashboard.components.BottomActionBar
import app.gov.uidai.registration.ui.dashboard.components.QuotaSoftPromptModal
import app.gov.uidai.registration.ui.dashboard.components.QuotaTicker
import app.gov.uidai.registration.ui.dashboard.screens.DiversityTab
import app.gov.uidai.registration.ui.dashboard.screens.FingersTab
import app.gov.uidai.registration.ui.dashboard.screens.OverviewTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardRoute(
    operatorId: String,
    onNavigateUp: () -> Unit,
    onNewCollection: () -> Unit,
    sessionId: String = "",
    residentPseudonymId: String = "",
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val quotaCheckResult by viewModel.quotaCheckResult.collectAsStateWithLifecycle()
    val overrideResult by viewModel.overrideResult.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.setSessionContext(sessionId, residentPseudonymId, operatorId)
        viewModel.onTabSelected(DashboardTab.OVERVIEW)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it, withDismissAction = true)
            viewModel.clearError()
        }
    }

    LaunchedEffect(overrideResult) {
        when (overrideResult) {
            is OverrideResult.Success -> {
                snackbarHostState.showSnackbar("Override logged")
                viewModel.clearOverrideResult()
                viewModel.clearQuotaCheckResult()
            }
            is OverrideResult.Error -> {
                snackbarHostState.showSnackbar((overrideResult as OverrideResult.Error).message)
                viewModel.clearOverrideResult()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleForTab(uiState.selectedTab)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back")
                    }
                }
            )
        },
        bottomBar = { BottomActionBar(onNewCollection = onNewCollection) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            QuotaTicker(alerts = uiState.alerts)

            TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                DashboardTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.onTabSelected(tab) },
                        text = { Text(titleForTab(tab)) }
                    )
                }
            }

            DashboardContent(uiState = uiState)
        }

        quotaCheckResult?.let { quotaCheck ->
            QuotaSoftPromptModal(
                quotaCheck = quotaCheck,
                operatorId = operatorId,
                onDismiss = viewModel::clearQuotaCheckResult,
                onChange = viewModel::clearQuotaCheckResult,
                onProceed = viewModel::clearQuotaCheckResult,
                onOverrideConfirmed = { dimension, key -> viewModel.confirmOverride(dimension, key) }
            )
        }
    }
}

@Composable
private fun DashboardContent(uiState: DashboardUiState) {
    when (uiState.selectedTab) {
        DashboardTab.OVERVIEW -> OverviewTab(overview = uiState.overview, isLoading = uiState.isOverviewLoading)
        DashboardTab.DIVERSITY -> DiversityTab(diversity = uiState.diversity, isLoading = uiState.isDiversityLoading)
        DashboardTab.FINGERS -> FingersTab(fingers = uiState.fingers, isLoading = uiState.isFingersLoading)
    }
}

private fun titleForTab(tab: DashboardTab): String = when (tab) {
    DashboardTab.OVERVIEW -> "My Dashboard"
    DashboardTab.DIVERSITY -> "Diversity Targets"
    DashboardTab.FINGERS -> "Finger Counts"
}
