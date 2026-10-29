package app.gov.uidai.registration.ui.registration.method

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gov.uidai.capture.R
import app.gov.uidai.capture.ui.theme.Typography
import app.gov.uidai.registration.model.CaptureMethod
import app.gov.uidai.registration.model.CaptureMode
import app.gov.uidai.registration.model.FingerCaptureStatus
import app.gov.uidai.registration.model.FingerPosition
import app.gov.uidai.registration.ui.registration.RegistrationViewModel
import app.gov.uidai.registration.ui.theme.Colors
import app.gov.uidai.registration.ui.theme.Spacing
import kotlinx.coroutines.launch

// --------------------------------------------------
// Existing flow: finger positions, status rules, labels
// --------------------------------------------------
private val SlapPositions = listOf(
    FingerPosition.LEFT_SLAP,
    FingerPosition.RIGHT_SLAP,
    FingerPosition.LEFT_THUMB,
    FingerPosition.RIGHT_THUMB
)

private val LeftHand = listOf(
    FingerPosition.LEFT_THUMB,
    FingerPosition.LEFT_INDEX,
    FingerPosition.LEFT_MIDDLE,
    FingerPosition.LEFT_RING,
    FingerPosition.LEFT_LITTLE
)

private val RightHand = listOf(
    FingerPosition.RIGHT_THUMB,
    FingerPosition.RIGHT_INDEX,
    FingerPosition.RIGHT_MIDDLE,
    FingerPosition.RIGHT_RING,
    FingerPosition.RIGHT_LITTLE
)

// One entry per tab of the Sequential mode; the index is the tab index.
private data class HandTab(val title: String, val fingers: List<FingerPosition>)

private val HandTabs = listOf(
    HandTab("Left Hand", LeftHand),
    HandTab("Right Hand", RightHand)
)

// Same rule as RegistrationViewModel.isDone(): captured, queued or uploading counts as saved.
private fun FingerCaptureStatus.isSaved() =
    this == FingerCaptureStatus.CAPTURED ||
            this == FingerCaptureStatus.PENDING ||
            this == FingerCaptureStatus.UPLOADING

private fun FingerCaptureStatus.canCapture() =
    this == FingerCaptureStatus.NOT_CAPTURED || this == FingerCaptureStatus.FAILED

private typealias FingerStatusMap = Map<FingerPosition, FingerCaptureStatus>

private fun FingerStatusMap.statusOf(position: FingerPosition) =
    this[position] ?: FingerCaptureStatus.NOT_CAPTURED

// A slap covers the four fingers of its hand (not the thumb), so those fingers are done once it is saved.
private fun FingerPosition.coveringSlap() = when (this) {
    FingerPosition.LEFT_INDEX, FingerPosition.LEFT_MIDDLE,
    FingerPosition.LEFT_RING, FingerPosition.LEFT_LITTLE -> FingerPosition.LEFT_SLAP

    FingerPosition.RIGHT_INDEX, FingerPosition.RIGHT_MIDDLE,
    FingerPosition.RIGHT_RING, FingerPosition.RIGHT_LITTLE -> FingerPosition.RIGHT_SLAP

    else -> null
}

// Status to show for a position:
//  - a finger covered by a saved slap shows the slap's status;
//  - a slap whose four fingers are all saved on their own shows the least-finished of them.
private fun FingerStatusMap.fingerStatusOf(position: FingerPosition): FingerCaptureStatus {
    val own = statusOf(position)
    if (own.isSaved()) return own

    val slap = position.coveringSlap()?.let { statusOf(it) }
    if (slap != null && slap.isSaved()) return slap

    val fingers = position.slapFingers().map { statusOf(it) }
    if (fingers.isNotEmpty() && fingers.all { it.isSaved() }) {
        return when {
            FingerCaptureStatus.UPLOADING in fingers -> FingerCaptureStatus.UPLOADING
            FingerCaptureStatus.PENDING in fingers -> FingerCaptureStatus.PENDING
            else -> FingerCaptureStatus.CAPTURED
        }
    }
    return own
}

private fun FingerStatusMap.savedCount(positions: List<FingerPosition>) =
    positions.count { fingerStatusOf(it).isSaved() }

// Fingers covered out of 10.
private fun FingerStatusMap.savedFingerCount() = savedCount(LeftHand + RightHand)

private fun FingerStatusMap.savedSummary(noun: String) =
    "${savedFingerCount()} of 10 fingerprints saved · Select a $noun to continue"

// A slap can't be captured once any of its hand's four fingers was captured on its own (Sequential).
private fun FingerPosition.slapFingers() = when (this) {
    FingerPosition.LEFT_SLAP -> LeftHand.drop(1)
    FingerPosition.RIGHT_SLAP -> RightHand.drop(1)
    else -> emptyList()
}

private fun FingerStatusMap.isSlapBlocked(position: FingerPosition) =
    !fingerStatusOf(position).isSaved() && position.slapFingers().any { statusOf(it).isSaved() }

private fun FingerStatusMap.isSelectable(position: FingerPosition) =
    fingerStatusOf(position).canCapture() && !isSlapBlocked(position)

private fun FingerPosition.slapBlockedMessage() =
    "${label()} isn't available because ${
        name.substringBefore('_').lowercase()
    }-hand fingers are already captured in Sequential."

private fun FingerPosition.isSlap() =
    this == FingerPosition.LEFT_SLAP || this == FingerPosition.RIGHT_SLAP

private fun FingerPosition.isLeft() = name.startsWith("LEFT")

private fun FingerPosition.label() =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun FingerPosition.fingerCount() = if (isSlap()) 4 else 1

private fun FingerPosition.fingerCountText() =
    fingerCount().let { "$it ${if (it == 1) "finger" else "fingers"}" }

private fun FingerPosition.guidance() =
    if (isSlap()) {
        "Align all four ${
            name.substringBefore('_').lowercase()
        }-hand fingers with the palm overlay. Keep the thumb out."
    } else {
        "Place ${label().lowercase()} flat on the center of the scanner sensor."
    }

// --------------------------------------------------
// Route: connects the screen to the shared RegistrationViewModel
// --------------------------------------------------
@Composable
fun NewCaptureMethodRoute(
    registrationViewModel: RegistrationViewModel,
    onNavigateUp: () -> Unit,
    onStartCaptureLoop: () -> Unit,
    onHelpClick: () -> Unit = {}
) {
    val uiState by registrationViewModel.uiState.collectAsStateWithLifecycle()

    FingerprintCollectionScreen(
        fingerStatus = uiState.fingerUploadStatus,
        onBackClick = onNavigateUp,
        onHelpClick = onHelpClick,
        onCreateCollection = { method, start ->
            when (method) {
                CaptureMethod.SLAP -> {
                    registrationViewModel.setSelectedCaptureMode(CaptureMode.SLAP)
                    registrationViewModel.startSlapSequence(start)
                }

                CaptureMethod.SEQUENTIAL -> {
                    registrationViewModel.setSelectedCaptureMode(CaptureMode.SEQUENTIAL)
                    registrationViewModel.startHandSequence(start)
                }
            }
            onStartCaptureLoop()
        }
    )
}

// --------------------------------------------------
// Main Screen Composable
// --------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FingerprintCollectionScreen(
    fingerStatus: Map<FingerPosition, FingerCaptureStatus>,
    onBackClick: () -> Unit = {},
    onHelpClick: () -> Unit = {},
    onCreateCollection: (CaptureMethod, FingerPosition) -> Unit = { _, _ -> }
) {
    var selectedMethod by rememberSaveable { mutableStateOf(CaptureMethod.SLAP) }

    var selectedHandTab by rememberSaveable { mutableIntStateOf(0) } // 0: Left, 1: Right
    var pickedPosition by rememberSaveable { mutableStateOf<FingerPosition?>(null) }

    val handFingers = HandTabs[selectedHandTab].fingers
    val candidates = if (selectedMethod == CaptureMethod.SLAP) SlapPositions else handFingers
    val selected = pickedPosition
        ?.takeIf { it in candidates && fingerStatus.isSelectable(it) }
        ?: candidates.firstOrNull { fingerStatus.isSelectable(it) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val savedFingers = fingerStatus.savedFingerCount()
    val sessionStatus = when (savedFingers) {
        0 -> "Not started"
        10 -> "Complete"
        else -> "In progress"
    }

    var showGuidelines by rememberSaveable { mutableStateOf(false) }
    var showCaptureStatus by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "New Collection",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Colors.Text.Primary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Colors.Background.Transparent
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.chevron_left),
                            contentDescription = "Back",
                            tint = Colors.Text.Primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onHelpClick) {
                        Icon(
                            painter = painterResource(R.drawable.help),
                            contentDescription = "Help",
                            tint = Colors.Text.Secondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Colors.Background.Primary)
            )
        },
        bottomBar = {
            BottomActionBar(
                enabled = selected != null,
                onClick = { selected?.let { onCreateCollection(selectedMethod, it) } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Colors.customColor
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 80.dp), // keeps the last item clear of the card
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: Title & Status Chip
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fingerprints",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Colors.Brand.Text
                        )

                        StatusBadge(text = sessionStatus)
                    }
                }

                // Capture Method Segmented Toggle
                Column {
                    Text(
                        text = "CAPTURE METHOD",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Colors.Text.Secondary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CaptureMethodSelector(
                        selectedMethod = selectedMethod,
                        onMethodSelected = { selectedMethod = it }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { showCaptureStatus = true }
                        ,
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        CaptionText(
                            text = "Click here to know more about the capture status.",
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            painter = painterResource(R.drawable.info_outlined),
                            contentDescription = null,
                            tint = Color.Unspecified
                        )
                    }
                }

                // Content dynamic to selected mode
                if (selectedMethod == CaptureMethod.SLAP) {
                    SlapModeContent(
                        fingerStatus = fingerStatus,
                        selected = selected,
                        onGroupSelect = { pickedPosition = it },
                        onBlockedGroupClick = { position ->
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar(position.slapBlockedMessage())
                            }
                        }
                    )
                } else {
                    SequentialModeContent(
                        fingerStatus = fingerStatus,
                        selectedHandTab = selectedHandTab,
                        onTabSelect = { selectedHandTab = it },
                        selected = selected,
                        onFingerSelect = { pickedPosition = it }
                    )
                }
            }

            // Pinned just above the bottom button
            GuideLineCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                onClick = { showGuidelines = true }
            )
        }
    }

    if (showGuidelines) {
        GuideLineDialog(onDismiss = { showGuidelines = false })
    }

    if (showCaptureStatus) {
        CaptureStatusDialog(onDismiss = { showCaptureStatus = false })
    }
}

// --------------------------------------------------
// Section: Method Selector (Slap vs Sequential)
// --------------------------------------------------
@Composable
fun CaptureMethodSelector(
    selectedMethod: CaptureMethod,
    onMethodSelected: (CaptureMethod) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .border(1.dp, Colors.Border.Primary, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val isSlap = selectedMethod == CaptureMethod.SLAP
        val isSequential = selectedMethod == CaptureMethod.SEQUENTIAL

        MethodButton(
            modifier = Modifier.weight(1f),
            label = "Slap",
            icon = painterResource(R.drawable.slap_symbol),
            isSelected = isSlap,
            onClick = { onMethodSelected(CaptureMethod.SLAP) }
        )

        MethodButton(
            modifier = Modifier.weight(1f),
            label = "Sequential",
            icon = painterResource(R.drawable.sequential_method),
            isSelected = isSequential,
            onClick = { onMethodSelected(CaptureMethod.SEQUENTIAL) }
        )
    }
}

@Composable
fun MethodButton(
    modifier: Modifier = Modifier,
    label: String,
    icon: Painter,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) Colors.Brand.Blue else Color.Transparent
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = if (isSelected) Colors.Text.OnBrand else Colors.Text.Primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = if (isSelected) Colors.Text.OnBrand else Colors.Text.Primary
            )
        }
    }
}

// --------------------------------------------------
// Section: Slap Mode 2x2 Grid View
// --------------------------------------------------
@Composable
fun SlapModeContent(
    fingerStatus: Map<FingerPosition, FingerCaptureStatus>,
    selected: FingerPosition?,
    onGroupSelect: (FingerPosition) -> Unit,
    onBlockedGroupClick: (FingerPosition) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Capture groups",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Colors.Brand.Text
            )
            Text(
                text = "${fingerStatus.savedCount(SlapPositions)} of ${SlapPositions.size} complete",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Colors.Brand.Text
            )
        }

        // Multi-segment progress indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SlapPositions.forEach { position ->
                val color = when {
                    fingerStatus.fingerStatusOf(position).isSaved() -> Colors.Success.Main
                    position == selected -> Colors.Brand.BlueSoft
                    else -> Colors.Border.Primary
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(color, RoundedCornerShape(2.dp))
                )
            }
        }

        SlapPositions.forEach { position ->
            FingerRowCard(
                position = position,
                status = fingerStatus.fingerStatusOf(position),
                isSelected = position == selected,
                isBlocked = fingerStatus.isSlapBlocked(position),
                onClick = { onGroupSelect(position) },
                onBlockedClick = { onBlockedGroupClick(position) }
            )
        }
    }
}

// --------------------------------------------------
// Section: Sequential Mode List View
// --------------------------------------------------
@Composable
fun SequentialModeContent(
    fingerStatus: Map<FingerPosition, FingerCaptureStatus>,
    selectedHandTab: Int,
    onTabSelect: (Int) -> Unit,
    selected: FingerPosition?,
    onFingerSelect: (FingerPosition) -> Unit
) {
    val fingers = HandTabs[selectedHandTab].fingers

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Tab Row (Left Hand vs Right Hand)
        TabRow(
            selectedTabIndex = selectedHandTab,
            containerColor = Color.Transparent,
            contentColor = Colors.Text.Primary,
            indicator = {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.BottomStart)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    repeat(HandTabs.size) { index ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .background(
                                    color = if (index == selectedHandTab) Colors.Success.Main else Colors.Brand.BlueSoft,
                                    shape = RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
            },
            divider = {}
        ) {
            HandTabs.forEachIndexed { index, hand ->
                Tab(
                    selected = selectedHandTab == index,
                    onClick = { onTabSelect(index) },
                    text = {
                        Text(
                            "${hand.title} (${fingerStatus.savedCount(hand.fingers)})",
                            fontWeight = if (selectedHandTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // List of Finger Cards
        fingers.forEach { position ->
            FingerRowCard(
                position = position,
                status = fingerStatus.fingerStatusOf(position),
                isSelected = position == selected,
                onClick = { onFingerSelect(position) }
            )
        }
    }
}

@Composable
fun FingerRowCard(
    position: FingerPosition,
    status: FingerCaptureStatus,
    isSelected: Boolean,
    isBlocked: Boolean = false,
    onClick: () -> Unit,
    onBlockedClick: () -> Unit = {}
) {
    val isSaved = status.isSaved()

    // A blocked slap stays tappable so the operator gets told why it can't be used.
    SelectableCard(
        modifier = Modifier.fillMaxWidth(),
        isSelected = isSelected,
        enabled = status.canCapture() || isBlocked,
        onClick = if (isBlocked) onBlockedClick else onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (isBlocked) 0.4f else 1f)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FingerIcon(
                    iconRes = if (position.isSlap()) R.drawable.slap_symbol else R.drawable.uncaptured_finger_symbol,
                    position = position,
                    tint = if (isSaved) Colors.Success.Main else Colors.Text.Secondary,
                    size = 24.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = position.label(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Colors.Text.Primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                CaptionText(text = "${position.fingerCountText()} · ${if (isSaved) "Saved" else "Not captured"}")
            }

            CaptureStatusLabel(
                status = status,
                isSelected = isSelected,
                iconSize = 16.dp
            )
        }
    }
}

// --------------------------------------------------
// Section: Guidance / Action Box
// --------------------------------------------------
@Composable
fun GuidanceCard(
    title: String,
    instruction: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Colors.Info.Container.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, Colors.Brand.BlueSoft)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Fingerprint,
                contentDescription = null,
                tint = Colors.Brand.Blue,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Colors.Brand.Blue
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = instruction,
                    fontSize = 13.sp,
                    color = Colors.Text.Primary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// --------------------------------------------------
// Section: Bottom CTA Bar
// --------------------------------------------------
@Composable
fun BottomActionBar(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = Colors.Background.Primary,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Colors.Button.Primary,
                    contentColor = Colors.Button.OnPrimary,
                    disabledContainerColor = Colors.Button.Disabled,
                    disabledContentColor = Colors.Button.OnDisabled
                )
            ) {
                Text(
                    text = "Create Collection",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun StatusBadge(text: String) {
    Row(
        modifier = Modifier
            .background(Colors.Brand.BlueLight, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(Colors.Brand.Blue, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Colors.Brand.Blue
        )
    }
}

// --------------------------------------------------
// Shared building blocks (used by more than one section above)
// --------------------------------------------------

/** Small grey 12sp text used for hints, counts and summaries. */
@Composable
private fun CaptionText(
    text: String,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = TextUnit.Unspecified
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 12.sp,
        color = Colors.Text.Secondary,
        lineHeight = lineHeight
    )
}

/** White card with a blue border when selected; the base of GroupCard and FingerRowCard. */
@Composable
private fun SelectableCard(
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                Colors.Brand.BlueLight
            } else {
                Colors.Background.Primary
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 1.8.dp else 1.dp,
            color = if (isSelected) Colors.Brand.Blue else Colors.Border.Primary
        ),
        content = content
    )
}

/** Finger / slap icon, mirrored for the right hand. */
@Composable
private fun FingerIcon(
    @DrawableRes iconRes: Int,
    position: FingerPosition,
    tint: Color,
    size: Dp
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(size)
            .graphicsLayer(scaleX = if (position.isLeft()) 1f else -1f)
    )
}

/** Optional icon followed by a 12sp label, e.g. "Done", "Selected", "Pending", "+ Capture". */
@Composable
private fun StatusLabel(
    text: String,
    textColor: Color,
    icon: Painter? = null,
    iconSize: Dp = 16.dp,
    iconTint: Color = textColor,
    fontWeight: FontWeight? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
            Spacer(Modifier.width(2.dp))
        }
        Text(text, fontSize = 12.sp, color = textColor, fontWeight = fontWeight)
    }
}

/** Label at the end of a card, driven by the finger's capture status. */
@Composable
private fun CaptureStatusLabel(
    status: FingerCaptureStatus,
    isSelected: Boolean,
    iconSize: Dp
) {
    when {
        status == FingerCaptureStatus.CAPTURED -> StatusLabel(
            icon = painterResource(R.drawable.saved),
            iconSize = 16.dp,
            text = "Done",
            iconTint = Colors.Success.Main,
            textColor = Colors.Success.Text,
            fontWeight = FontWeight.Medium
        )

        // Captured and saved on the device, waiting to sync to the backend.
        status == FingerCaptureStatus.PENDING -> StatusLabel(
            icon = rememberVectorPainter(Icons.Outlined.Schedule),
            iconSize = iconSize,
            text = "Pending",
            textColor = Colors.Warning.Primary
        )

        status == FingerCaptureStatus.CAPTURING || status == FingerCaptureStatus.UPLOADING ->
            CircularProgressIndicator(
                modifier = Modifier.size(iconSize),
                strokeWidth = 2.dp,
                color = Colors.Brand.Blue
            )

        isSelected -> StatusLabel(
            icon = painterResource(R.drawable.capture_mode_selected_ico),
            iconSize = iconSize,
            text = "Selected",
            iconTint = Colors.Brand.Blue,
            textColor = Colors.Brand.Blue,
            fontWeight = FontWeight.SemiBold
        )

        // NOT_CAPTURED and FAILED
        else -> StatusLabel(
            text = "+ Capture",
            textColor = Colors.Brand.Blue,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun GuideLineCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFD5D9E0)),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Title
            Text(
                text = "CAPTURE GUIDELINES",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = Color(0xFF1E2D42)
            )

            // Info Icon
            Icon(
                painter = painterResource(R.drawable.info_outlined),
                contentDescription = "Capture Guidelines Info",
                tint = Color(0xFFB8C0CC),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Dialog shell shared by the guideline and capture-status popups. */
@Composable
private fun InfoDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false // lets the card use the full width minus the padding below
        )
    ) {
        InfoDialogCard(title = title, content = content)
    }
}

// Split from InfoDialog because @Preview can't render a Dialog window; preview this instead.
@Composable
private fun InfoDialogCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = Color(0xFF172B42)
            )
            content()
        }
    }
}

@Composable
private fun GuideLineDialog(onDismiss: () -> Unit) =
    InfoDialog(title = "CAPTURE GUIDELINES", onDismiss = onDismiss) { GuideLineList() }

@Composable
private fun GuideLineList() {
    guidelineItemList.forEach { guideline ->
        GuideLineItems(
            icon = guideline.icon,
            title = guideline.title,
            subtitle = guideline.subtitle,
            iconBgColor = guideline.bgColor
        )
    }
}

@Composable
private fun CaptureStatusDialog(onDismiss: () -> Unit) =
    InfoDialog(title = "CAPTURE STATUS", onDismiss = onDismiss) { CaptureStatusList() }

// Rows reuse CaptureStatusLabel, so the icons and colours always match the finger cards.
private data class CaptureStatusInfo(
    val status: FingerCaptureStatus,
    val containerColor: Color,
    val description: String
)

@Composable
private fun CaptureStatusList() {
    val items = listOf(
        CaptureStatusInfo(
            FingerCaptureStatus.CAPTURED,
            Colors.Success.Main.copy(alpha = 0.12f),
            "Successful capture"
        ),
        CaptureStatusInfo(
            FingerCaptureStatus.PENDING,
            Colors.Warning.Primary.copy(alpha = 0.12f),
            "Captured but not synced"
        ),
        CaptureStatusInfo(
            FingerCaptureStatus.NOT_CAPTURED,
            Colors.Brand.BlueLight,
            "You are yet to capture"
        )
    )

    items.forEachIndexed { index, item ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(item.containerColor, RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CaptureStatusLabel(status = item.status, isSelected = false, iconSize = 16.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = item.description,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Colors.Text.Primary
            )
        }

        if(index != items.size - 1) {
         Spacer(modifier = Modifier.size(6.dp))
        }
    }
}

@Composable
private fun GuideLineItems(
    icon: Int,
    title: String,
    subtitle: String? = null,
    iconBgColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = Colors.Background.Primary)
            .padding(vertical = Spacing.M),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Xxs, Alignment.Start)
    ) {

        Box(
            modifier = Modifier
                .size(48.dp)
                .background(color = iconBgColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.Xxxs, Alignment.CenterVertically),
            horizontalAlignment = Alignment.Start
        ) {
            Text(text = title, color = Colors.Text.Primary, fontWeight = FontWeight.Normal)
            subtitle?.let {
                Text(text = subtitle, color = Colors.Text.Secondary)
            }
        }
    }
}

// --------------------------------------------------
// Previews
// --------------------------------------------------
@Preview(showBackground = true)
@Composable
fun FingerprintCollectionScreenPreview() {
    MaterialTheme {
        FingerprintCollectionScreen(
            fingerStatus = mapOf(
                FingerPosition.LEFT_SLAP to FingerCaptureStatus.PENDING,
                FingerPosition.LEFT_THUMB to FingerCaptureStatus.CAPTURED
            )
        )

        InfoDialogCard("CAPTURE GUIDELINES") { GuideLineDialog {  } }
    }
}

data class GuidelineItem(
    val icon: Int = R.drawable.camera_icon,
    val title: String = "Clean Camera lens befoe start",
    val subtitle: String? = "- No Glare No Shadow",
    val bgColor: Color = Colors.GuidelineItems.item3Color
)

val guidelineItemList: List<GuidelineItem> = listOf(
    GuidelineItem(
        icon = R.drawable.fingerprint,
        title = "Finger must be clean and dry",
        subtitle = null,
        bgColor = Colors.GuidelineItems.item1Color
    ),
    GuidelineItem(
        icon = R.drawable.sun_with_face_icon,
        title = "Use bright, Even lighting",
        subtitle = "- No Glare No Shadow",
        bgColor = Colors.GuidelineItems.item2Color
    ),
    GuidelineItem(
        icon = R.drawable.camera_icon,
        title = "Clean Camera lens befoe start",
        subtitle = "- No Glare No Shadow",
        bgColor = Colors.GuidelineItems.item1Color
    ),
    GuidelineItem(
        icon = R.drawable.live_action_icon,
        title = "Do not use a photo or video",
        subtitle = "- Live Capture Only",
        bgColor = Colors.GuidelineItems.item4Color
    ),
    GuidelineItem(
        icon = R.drawable.ink_icon,
        title = "Avoid Fingers with Ink, Mehandi or tattoos",
        subtitle = null,
        bgColor = Colors.GuidelineItems.item5Color
    )

)