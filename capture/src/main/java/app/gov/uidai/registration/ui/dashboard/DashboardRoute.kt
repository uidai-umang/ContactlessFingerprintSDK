package app.gov.uidai.registration.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gov.uidai.registration.model.DashboardTab
import app.gov.uidai.registration.model.DashboardUiState
import app.gov.uidai.registration.ui.dashboard.components.BottomActionBar
import app.gov.uidai.registration.ui.dashboard.components.DashColors
import app.gov.uidai.registration.ui.dashboard.components.QuotaSoftPromptModal
import app.gov.uidai.registration.ui.dashboard.components.QuotaTicker
import app.gov.uidai.registration.ui.dashboard.screens.DiversityTab
import app.gov.uidai.registration.ui.dashboard.screens.FingersTab
import app.gov.uidai.registration.ui.dashboard.screens.OverviewTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardRoute(
    operatorId: String,
    onNavigateUp: () -> Unit,
    onNewCollection: () -> Unit,
    residentPseudonymId: String = "",
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val quotaCheckResult by viewModel.quotaCheckResult.collectAsStateWithLifecycle()
    val overrideResult by viewModel.overrideResult.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val pagerState = rememberPagerState(
        initialPage = uiState.selectedTab.ordinal,
        pageCount = { DashboardTab.entries.size }
    )
    val scope = rememberCoroutineScope()

    // Swipe -> update the selected tab once the page settles.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val tab = DashboardTab.entries[page]
            if (tab != uiState.selectedTab) viewModel.onTabSelected(tab)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.setContext(residentPseudonymId, operatorId)
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
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Dashboard",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DashColors.TopBarNavy
                )
            )
        },
        bottomBar = { BottomActionBar(onNewCollection = onNewCollection) },
        containerColor = DashColors.ScreenBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)) {
            QuotaTicker(alerts = uiState.alerts)

            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.White,
                divider = { HorizontalDivider(color = DashColors.TabDivider, thickness = 1.dp) },
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            height = 2.5.dp,
                            color = DashColors.PrimaryBlue
                        )
                    }
                }
            ) {
                DashboardTab.entries.forEachIndexed { index, tab ->
                    val isSelected = pagerState.currentPage == index
                    Tab(
                        selected = isSelected,
                        onClick = {
                            viewModel.onTabSelected(tab)
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        modifier = Modifier.background(
                            if (isSelected) DashColors.SelectedTabBg else Color.White
                        ),
                        text = {
                            Text(
                                text = titleForTab(tab),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) DashColors.PrimaryBlue else DashColors.TextDark,
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                DashboardContent(tab = DashboardTab.entries[page], uiState = uiState)
            }
        }

        quotaCheckResult?.let { quotaCheck ->
            QuotaSoftPromptModal(
                quotaCheck = quotaCheck,
                operatorId = operatorId,
                onDismiss = viewModel::clearQuotaCheckResult,
                onChange = viewModel::clearQuotaCheckResult,
                onProceed = viewModel::clearQuotaCheckResult,
                onOverrideConfirmed = { dimension, key ->
                    viewModel.confirmOverride(
                        dimension,
                        key
                    )
                }
            )
        }
    }
}

@Composable
private fun DashboardContent(tab: DashboardTab, uiState: DashboardUiState) {
    when (tab) {
        DashboardTab.OVERVIEW -> OverviewTab(
            overview = uiState.overview,
            isLoading = uiState.isOverviewLoading
        )

        DashboardTab.DIVERSITY -> DiversityTab(
            diversity = uiState.diversity,
            isLoading = uiState.isDiversityLoading
        )

        DashboardTab.FINGERS -> FingersTab(
            fingers = uiState.fingers,
            isLoading = uiState.isFingersLoading
        )
    }
}

private fun titleForTab(tab: DashboardTab): String = when (tab) {
    DashboardTab.OVERVIEW -> "Dashboard"
    DashboardTab.DIVERSITY -> "Target Slots"
    DashboardTab.FINGERS -> "Finger Counts"
}