package app.gov.uidai.registration.ui.registration

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gov.uidai.registration.model.CLFingerprint
import app.gov.uidai.registration.model.FingerCaptureStatus
import app.gov.uidai.registration.model.FingerPosition
import app.gov.uidai.registration.model.RegistrationUiState
import app.gov.uidai.registration.model.SharedUiState
import app.gov.uidai.registration.ui.composable.LoadingDialog
import app.gov.uidai.registration.ui.theme.Spacer

// Mockup-exact colors — host app has its own AttendanceAppTheme, separate
// from the capture module's design tokens; kept local here same as the
// capture module's finger-list screen was, until the two get unified.
private object FingerListColors {
    val Background = Color(0xFFF5F0EC)
    val ContainerBase = Color(0xFFEDE8E3)
    val Border = Color(0xFFD8D0C8)
    val Primary = Color(0xFF1A56A0)
    val TextPrimary = Color(0xFF1A1A1A)
    val TextMuted = Color(0xFF8A7F78)

    val SyncingBg = Color(0xFFFFFBEB)
    val SyncingContainer = Color(0xFFFEF3C7)
    val SyncingBorder = Color(0xFFFCD34D)
    val SyncingText = Color(0xFFD97706)
    val SyncingBadgeText = Color(0xFF92400E)

    val InfoContainer = Color(0xFFEBF3FD)
    val InfoBorder = Color(0xFF93C5FD)

    val Positive = Color(0xFF16A34A)
    val PositiveContainer = Color(0xFFF0FDF4)
    val PositiveBorder = Color(0xFF86EFAC)

    val Negative = Color(0xFFE53935)
    val NegativeContainer = Color(0xFFFFEBEE)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationRoute(
    uidHash: String,
    sharedUiState: SharedUiState,
    onNavigateUp: () -> Unit,
    viewModel: RegistrationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val registrationResult by viewModel.registrationResult.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val sdkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> viewModel.handleSdkActivityResult(result.resultCode, result.data) }

    LaunchedEffect(Unit) { viewModel.setUidHash(uidHash) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capture Fingerprints", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FingerListColors.Background)
            )
        },
        containerColor = FingerListColors.Background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        RegistrationScreen(
            uiState = uiState,
            onAddFingerprint = {
                keyboardController?.hide()
                viewModel.captureFingerprint(it, sdkLauncher)
            },
            onRemoveFingerprint = viewModel::removeFingerprint,
            onSaveRegistration = viewModel::registerUser,
            paddingValues = paddingValues
        )
        LoadingDialog(sharedUiState.isLoadingEmbedder, "Initializing Embedder...")

        LaunchedEffect(uiState.message) {
            uiState.message?.let {
                snackbarHostState.showSnackbar(it, withDismissAction = true)
                viewModel.clearError()
            }
        }
        LaunchedEffect(registrationResult) {
            when (registrationResult) {
                is RegistrationResult.Success -> {
                    snackbarHostState.showSnackbar("Registration completed successfully!")
                    keyboardController?.hide()
                    onNavigateUp()
                }
                is RegistrationResult.Error -> {
                    snackbarHostState.showSnackbar((registrationResult as RegistrationResult.Error).message)
                }
                else -> {}
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun RegistrationScreen(
    uiState: RegistrationUiState,
    onRemoveFingerprint: (FingerPosition) -> Unit,
    onSaveRegistration: () -> Unit,
    onAddFingerprint: (FingerPosition) -> Unit,
    paddingValues: PaddingValues
) {
    var selectedImage by remember { mutableStateOf<FingerPosition?>(null) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val rightHandFingers = uiState.fingerprints.filter { it.key.name.contains("RIGHT") }
    val leftHandFingers = uiState.fingerprints.filter { it.key.name.contains("LEFT") }
    val totalCapturedOverall = uiState.fingerUploadStatus.values.count { it == FingerCaptureStatus.CAPTURED }
    val currentHandKeys = if (selectedTabIndex == 0) leftHandFingers.keys else rightHandFingers.keys
    val currentHandCaptured = currentHandKeys.count { uiState.fingerUploadStatus[it] == FingerCaptureStatus.CAPTURED }
    val currentHandComplete = currentHandCaptured == currentHandKeys.size && currentHandKeys.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (totalCapturedOverall < 4) {
                    MinRequirementBanner()
                }

                FingerprintSection(
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { selectedTabIndex = it },
                    leftHandFingers = leftHandFingers,
                    rightHandFingers = rightHandFingers,
                    loadingFingerPosition = uiState.loadingFinger,
                    fingerUploadStatus = uiState.fingerUploadStatus,
                    onAddFingerprint = onAddFingerprint,
                    onClickFingerprint = { selectedImage = it },
                    onRemoveFingerprint = onRemoveFingerprint
                )

                ProgressCard(
                    captured = currentHandCaptured,
                    total = currentHandKeys.size,
                    isComplete = currentHandComplete
                )
            }

            BottomActionBar(
                currentHandComplete = currentHandComplete,
                otherHandLabel = if (selectedTabIndex == 0) "right" else "left",
                canSave = uiState.canSave && !uiState.isLoading,
                onSkipAndFinish = onSaveRegistration,
                onFinishRegistration = onSaveRegistration,
                onSwitchToOtherHand = { selectedTabIndex = if (selectedTabIndex == 0) 1 else 0 }
            )
        }

        // Image preview dialog — existing debug/detail feature, not in the
        // mockup but real, kept unchanged
        selectedImage?.let {
            val bitmap = uiState.fingerprints[it]?.bitmap?.asImageBitmap()!!
            val fingerQuality = uiState.fingerprints[it]?.fingerQuality
            Dialog(onDismissRequest = { selectedImage = null }) {
                Card(shape = MaterialTheme.shapes.extraLarge) {
                    Column(modifier = Modifier.padding(bottom = 16.dp)) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(8.dp)
                        fingerQuality?.attributes?.forEach { attribute ->
                            Text(
                                text = "${attribute.getFormatedName()}: ${String.format("%.1f", attribute.score)}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(4.dp)
                        }
                        Spacer(4.dp)
                        TextButton(onClick = { selectedImage = null }, modifier = Modifier.fillMaxWidth()) {
                            Text("Okay")
                        }
                    }
                }
            }
        }

        if (uiState.isLoading) {
            Dialog(onDismissRequest = { }) { CircularProgressIndicator() }
        }
    }
}

@Composable
private fun MinRequirementBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(FingerListColors.InfoContainer)
            .border(1.dp, FingerListColors.InfoBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = FingerListColors.Primary, modifier = Modifier.size(16.dp))
        Text(
            text = "Capture minimum 4 fingers from either hand to complete registration. Tap any finger to start.",
            fontSize = 12.sp,
            color = FingerListColors.Primary,
            lineHeight = 17.sp
        )
    }
}

@Composable
fun FingerprintSection(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    leftHandFingers: Map<FingerPosition, CLFingerprint?>,
    rightHandFingers: Map<FingerPosition, CLFingerprint?>,
    loadingFingerPosition: FingerPosition?,
    fingerUploadStatus: Map<FingerPosition, FingerCaptureStatus>,
    onAddFingerprint: (FingerPosition) -> Unit,
    onClickFingerprint: (FingerPosition) -> Unit,
    onRemoveFingerprint: (FingerPosition) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, FingerListColors.Border, RoundedCornerShape(12.dp))
    ) {
        Row(modifier = Modifier.fillMaxWidth().background(FingerListColors.ContainerBase)) {
            listOf("Left", "Right").forEachIndexed { index, title ->
                val isActive = selectedTabIndex == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isActive) FingerListColors.Background else Color.Transparent)
                        .clickable { onTabSelected(index) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isActive) FingerListColors.TextPrimary else FingerListColors.TextMuted
                        )
                        if (isActive) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(modifier = Modifier.height(2.dp).width(24.dp).background(FingerListColors.Primary))
                        }
                    }
                }
            }
        }

        AnimatedContent(targetState = selectedTabIndex, label = "tabContentAnimation") { targetTabIndex ->
            FingerList(
                items = if (targetTabIndex == 0) leftHandFingers else rightHandFingers,
                loadingFingerPosition = loadingFingerPosition,
                fingerUploadStatus = fingerUploadStatus,
                onAddFingerprint = onAddFingerprint,
                onRemoveFingerprint = onRemoveFingerprint,
                onClickFingerprint = onClickFingerprint
            )
        }
    }
}

@Composable
fun FingerList(
    items: Map<FingerPosition, CLFingerprint?>,
    loadingFingerPosition: FingerPosition?,
    fingerUploadStatus: Map<FingerPosition, FingerCaptureStatus>,
    onAddFingerprint: (FingerPosition) -> Unit,
    onClickFingerprint: (FingerPosition) -> Unit,
    onRemoveFingerprint: (FingerPosition) -> Unit
) {
    Column(modifier = Modifier.background(FingerListColors.Background)) {
        items.entries.forEachIndexed { index, item ->
            FingerItem(
                position = item.key,
                isLoading = (item.key == loadingFingerPosition),
                uploadStatus = fingerUploadStatus[item.key] ?: FingerCaptureStatus.NOT_CAPTURED,
                bitmap = item.value?.bitmap,
                minutiaCount = item.value?.fingerQuality?.getMinutia(),
                onClick = { onClickFingerprint(item.key) },
                onRemove = { onRemoveFingerprint(item.key) },
                onAdd = { onAddFingerprint(item.key) }
            )
            if (index < items.size - 1) {
                HorizontalDivider(color = FingerListColors.ContainerBase, thickness = 1.dp)
            }
        }
    }
}

@Composable
fun FingerItem(
    position: FingerPosition,
    isLoading: Boolean,
    uploadStatus: FingerCaptureStatus,
    bitmap: Bitmap?,
    minutiaCount: Double?,
    onAdd: () -> Unit,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isItemAdded = bitmap != null
    val isClickable = when (uploadStatus) {
        FingerCaptureStatus.NOT_CAPTURED -> !isLoading
        FingerCaptureStatus.CAPTURING, FingerCaptureStatus.UPLOADING, FingerCaptureStatus.CAPTURED -> false
        FingerCaptureStatus.PENDING, FingerCaptureStatus.FAILED -> isItemAdded
    }
    val rowBackground = if (uploadStatus == FingerCaptureStatus.UPLOADING) FingerListColors.SyncingBg else Color.Transparent
    val fingerName = position.name.replace('_', ' ')

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(rowBackground)
            .clickable(
                enabled = isClickable,
                onClick = {
                    when (uploadStatus) {
                        FingerCaptureStatus.NOT_CAPTURED -> onAdd()
                        FingerCaptureStatus.PENDING, FingerCaptureStatus.FAILED -> if (isItemAdded) onClick()
                        else -> {}
                    }
                }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.size(36.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            when {
                uploadStatus == FingerCaptureStatus.UPLOADING -> {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(FingerListColors.SyncingContainer)
                            .border(2.dp, FingerListColors.SyncingText, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = FingerListColors.SyncingText)
                    }
                }
                uploadStatus == FingerCaptureStatus.PENDING -> {
                    Box(modifier = Modifier.fillMaxSize().background(FingerListColors.SyncingContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Refresh, "Pending sync", tint = FingerListColors.SyncingText, modifier = Modifier.size(20.dp))
                    }
                }
                uploadStatus == FingerCaptureStatus.FAILED -> {
                    Box(modifier = Modifier.fillMaxSize().background(FingerListColors.NegativeContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Warning, "Failed", tint = FingerListColors.Negative, modifier = Modifier.size(20.dp))
                    }
                }
                isItemAdded || uploadStatus == FingerCaptureStatus.CAPTURED -> {
                    Box(modifier = Modifier.fillMaxSize().background(FingerListColors.Positive), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, "Captured", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                uploadStatus == FingerCaptureStatus.CAPTURING -> {
                    Box(modifier = Modifier.fillMaxSize().background(FingerListColors.InfoContainer), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = FingerListColors.Primary)
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(FingerListColors.InfoContainer)
                            .border(2.dp, FingerListColors.Primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, "Add $fingerName", tint = FingerListColors.Primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        val nameColor = when (uploadStatus) {
            FingerCaptureStatus.UPLOADING -> FingerListColors.SyncingText
            FingerCaptureStatus.NOT_CAPTURED -> FingerListColors.TextMuted
            else -> FingerListColors.TextPrimary
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = fingerName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = nameColor)
            if (minutiaCount != null && (isItemAdded || uploadStatus == FingerCaptureStatus.CAPTURED)) {
                Text(text = "Minutia: ${minutiaCount.toInt()}", fontSize = 11.sp, color = FingerListColors.TextMuted)
            }
        }

        when (uploadStatus) {
            FingerCaptureStatus.UPLOADING -> StatusBadge("Syncing…", FingerListColors.SyncingContainer, FingerListColors.SyncingBadgeText, FingerListColors.SyncingBorder)
            FingerCaptureStatus.PENDING -> StatusBadge("Pending", FingerListColors.SyncingContainer, FingerListColors.SyncingBadgeText, FingerListColors.SyncingBorder)
            FingerCaptureStatus.FAILED -> StatusBadge("Failed", FingerListColors.NegativeContainer, FingerListColors.Negative, FingerListColors.Negative)
            FingerCaptureStatus.CAPTURED -> StatusBadge("Synced", FingerListColors.PositiveContainer, FingerListColors.Positive, FingerListColors.PositiveBorder)
            else -> {}
        }
    }
}

@Composable
private fun StatusBadge(text: String, bg: Color, textColor: Color, border: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(text = text, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}

@Composable
private fun ProgressCard(captured: Int, total: Int, isComplete: Boolean) {
    val progress = if (total > 0) captured.toFloat() / total else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isComplete) FingerListColors.PositiveContainer else Color.White)
            .border(1.dp, if (isComplete) FingerListColors.PositiveBorder else FingerListColors.Border, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isComplete) "HAND COMPLETE" else "PROGRESS",
                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = if (isComplete) FingerListColors.Positive else FingerListColors.TextMuted
            )
            Text(
                text = if (isComplete) "$captured / $total \u2713" else "$captured / $total captured",
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (isComplete) FingerListColors.Positive else FingerListColors.Primary
            )
        }
        Spacer(modifier = Modifier.height(7.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(20.dp))
                .background(if (isComplete) FingerListColors.PositiveBorder else FingerListColors.ContainerBase)
        ) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(progress).background(if (isComplete) FingerListColors.Positive else FingerListColors.Primary))
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = if (isComplete) "All fingers synced. You can add the other hand or finish registration."
            else "Capture ${total - captured} more finger${if (total - captured != 1) "s" else ""} to complete this hand.",
            fontSize = 10.sp,
            color = if (isComplete) FingerListColors.Positive else FingerListColors.TextMuted
        )
    }
}

@Composable
private fun BottomActionBar(
    currentHandComplete: Boolean,
    otherHandLabel: String,
    canSave: Boolean,
    onSkipAndFinish: () -> Unit,
    onFinishRegistration: () -> Unit,
    onSwitchToOtherHand: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(FingerListColors.Background).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (currentHandComplete) {
            Button(
                onClick = onFinishRegistration,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FingerListColors.Positive),
                shape = RoundedCornerShape(10.dp)
            ) { Text("\u2713 Finish registration", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = onSwitchToOtherHand, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(10.dp)) {
                Text("Add $otherHandLabel hand fingers")
            }
        } else {
            OutlinedButton(onClick = onSkipAndFinish, enabled = canSave, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(10.dp)) {
                Text("Skip remaining & finish")
            }
        }
    }
}