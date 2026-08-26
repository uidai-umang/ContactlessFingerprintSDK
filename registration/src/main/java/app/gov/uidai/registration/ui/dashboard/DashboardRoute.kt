package app.gov.uidai.registration.ui.dashboard

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import app.gov.uidai.registration.ui.theme.dash_avatar_bg
import app.gov.uidai.registration.ui.theme.dash_avatar_border
import app.gov.uidai.registration.ui.theme.dash_avatar_text
import app.gov.uidai.registration.ui.theme.dash_navy
import app.gov.uidai.registration.ui.theme.dash_screen_bg
import app.gov.uidai.registration.ui.theme.dash_tab_active
import app.gov.uidai.registration.ui.theme.dash_tab_inactive

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
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
        topBar = {
            TopAppBar(
                title = { Text(titleForTab(uiState.selectedTab), fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back", tint = Color.White)
                    }
                },
                actions = { OperatorAvatar(operatorId) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = dash_navy)
            )
        },
        bottomBar = { BottomActionBar(onNewCollection = onNewCollection) },
        containerColor = dash_screen_bg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            QuotaTicker(alerts = uiState.alerts)

            TabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                containerColor = Color.White,
                indicator = { tabPositions ->
                    val position = tabPositions[uiState.selectedTab.ordinal]
                    val indicatorOffset by animateDpAsState(targetValue = position.left, label = "tabIndicatorOffset")
                    val indicatorWidth by animateDpAsState(targetValue = position.width, label = "tabIndicatorWidth")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentSize(Alignment.BottomStart)
                            .offset(x = indicatorOffset)
                            .width(indicatorWidth)
                            .height(2.5.dp)
                            .background(dash_tab_active)
                    )
                }
            ) {
                DashboardTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.onTabSelected(tab) },
                        selectedContentColor = dash_tab_active,
                        unselectedContentColor = dash_tab_inactive,
                        text = {
                            Text(
                                text = titleForTab(tab),
                                fontWeight = if (uiState.selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
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
private fun OperatorAvatar(operatorId: String) {
    val initials = remember(operatorId) { operatorInitials(operatorId) }
    Box(
        modifier = Modifier
            .padding(end = 12.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(dash_avatar_bg)
            .border(1.5.dp, dash_avatar_border, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = initials, color = dash_avatar_text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

// No operator-name field exists on this screen yet (only the id), so the chip
// shows the first two alphanumeric characters of the operator id as a stand-in.
private fun operatorInitials(operatorId: String): String {
    val alnum = operatorId.filter { it.isLetterOrDigit() }
    return if (alnum.length >= 2) alnum.take(2).uppercase() else "OP"
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
