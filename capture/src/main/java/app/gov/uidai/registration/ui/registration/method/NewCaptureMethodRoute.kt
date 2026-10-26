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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.CheckCircle
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gov.uidai.capture.R
import app.gov.uidai.registration.model.CaptureMethod
import app.gov.uidai.registration.model.CaptureMode
import app.gov.uidai.registration.model.FingerCaptureStatus
import app.gov.uidai.registration.model.FingerPosition
import app.gov.uidai.registration.ui.registration.RegistrationViewModel
import app.gov.uidai.registration.ui.theme.Colors
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
    "${label()} isn't available because ${name.substringBefore('_').lowercase()}-hand fingers are already captured in Sequential."

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
    var selectedMethod by rememberSaveable { mutableStateOf(CaptureMethod.SEQUENTIAL) }

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
                selectedGroupText = selected
                    ?.let { "${it.label()} selected · ${it.fingerCountText()}" }
                    ?: if (selectedMethod == CaptureMethod.SLAP) "No group left to capture" else "No finger left to capture",
                enabled = selected != null,
                onClick = { selected?.let { onCreateCollection(selectedMethod, it) } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Colors.Background.Secondary
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Title & Status Chip
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Fingerprints",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Colors.Text.Primary
                        )
                        Text(
                            text = "Resident capture session",
                            fontSize = 14.sp,
                            color = Colors.Text.Secondary
                        )
                    }
                    StatusBadge(text = sessionStatus)
                }
            }

            // Capture Method Segmented Toggle
            item {
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
                    slapSaved = fingerStatus.savedCount(SlapPositions),
                    onMethodSelected = { selectedMethod = it }
                )
                Spacer(modifier = Modifier.height(6.dp))
                CaptionText(text = "Counts in tabs show fingers captured with each method.")
            }

            // Content dynamic to selected mode
            if (selectedMethod == CaptureMethod.SLAP) {
                item {
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
                }
            } else {
                item {
                    SequentialModeContent(
                        fingerStatus = fingerStatus,
                        selectedHandTab = selectedHandTab,
                        onTabSelect = { selectedHandTab = it },
                        selected = selected,
                        onFingerSelect = { pickedPosition = it }
                    )
                }
            }

            // Instructional Guidance Card
            if (selected != null) {
                item {
                    GuidanceCard(
                        title = "Next: ${selected.label()}",
                        instruction = selected.guidance()
                    )
                }
            }

            // Footer Retain Note
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = Colors.Text.Secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    CaptionText(
                        text = "You can switch methods at any time. A saved slap marks that hand's four fingers as done in Sequential.",
                        lineHeight = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// --------------------------------------------------
// Section: Method Selector (Slap vs Sequential)
// --------------------------------------------------
@Composable
fun CaptureMethodSelector(
    selectedMethod: CaptureMethod,
    slapSaved: Int,
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
            label = "Slap ($slapSaved)",
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
                color = Colors.Text.Primary
            )
            Text(
                text = "${fingerStatus.savedCount(SlapPositions)} of ${SlapPositions.size} complete",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Colors.Text.Secondary
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

        CaptionText(text = fingerStatus.savedSummary("group"))

        // 2x2 Grid
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SlapPositions.chunked(2).forEach { rowPositions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowPositions.forEach { position ->
                        GroupCard(
                            modifier = Modifier.weight(1f),
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
        }
    }
}

@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    position: FingerPosition,
    status: FingerCaptureStatus,
    isSelected: Boolean,
    isBlocked: Boolean = false,
    onClick: () -> Unit,
    onBlockedClick: () -> Unit = {}
) {
    val isSaved = status.isSaved()

    // A blocked group stays tappable so the operator gets told why it can't be used.
    SelectableCard(
        modifier = modifier,
        isSelected = isSelected,
        enabled = status.canCapture() || isBlocked,
        onClick = if (isBlocked) onBlockedClick else onClick
    ) {
        Column(
            modifier = Modifier
                .alpha(if (isBlocked) 0.4f else 1f)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FingerIcon(
                    iconRes = if (position.isSlap()) R.drawable.slap_symbol else R.drawable.uncaptured_finger_symbol,
                    position = position,
                    tint = if (isSaved) Colors.Success.Main else Colors.Text.Primary,
                    size = 22.dp
                )
                CaptureStatusLabel(
                    status = status,
                    isSelected = isSelected,
                    iconSize = 14.dp
                )
            }

            Column {
                Text(
                    text = position.label(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Colors.Text.Primary
                )
                CaptionText(text = position.fingerCountText() + if (isSaved) " · Saved" else "")
            }
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

        CaptionText(text = fingerStatus.savedSummary("finger"))

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
    onClick: () -> Unit
) {
    val isSaved = status.isSaved()

    SelectableCard(
        modifier = Modifier.fillMaxWidth(),
        isSelected = isSelected,
        enabled = status.canCapture(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FingerIcon(
                    iconRes = R.drawable.uncaptured_finger_symbol,
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
                CaptionText(text = if (isSaved) "1 finger · Saved" else "1 finger · Not captured")
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
    selectedGroupText: String,
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
            CaptionText(text = selectedGroupText)
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
        colors = CardDefaults.cardColors(containerColor = if(isSelected) {
            Colors.Brand.BlueLight
        } else {
            Colors.Background.Primary
        }),
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
    }
}