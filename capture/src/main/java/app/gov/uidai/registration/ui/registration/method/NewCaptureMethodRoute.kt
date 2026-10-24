package app.gov.uidai.registration.ui.registration.method

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
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

private fun FingerStatusMap.savedCount(positions: List<FingerPosition>) =
    positions.count { statusOf(it).isSaved() }

// Fingers covered out of 10: a saved slap covers the four fingers of its hand, not the thumb.
private fun FingerStatusMap.savedFingerCount(): Int =
    listOf(
        LeftHand to FingerPosition.LEFT_SLAP,
        RightHand to FingerPosition.RIGHT_SLAP
    ).sumOf { (fingers, slap) ->
        val thumb = fingers.first()
        fingers.count { statusOf(it).isSaved() || (it != thumb && statusOf(slap).isSaved()) }
    }

private fun FingerPosition.label() =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun FingerPosition.fingerCount() =
    if (this == FingerPosition.LEFT_SLAP || this == FingerPosition.RIGHT_SLAP) 4 else 1

private fun FingerPosition.fingerCountText() =
    fingerCount().let { "$it ${if (it == 1) "finger" else "fingers"}" }

private fun FingerPosition.guidance() = when (this) {
    FingerPosition.LEFT_SLAP, FingerPosition.RIGHT_SLAP ->
        "Align all four ${name.substringBefore('_').lowercase()}-hand fingers with the palm overlay. Keep the thumb out."
    else -> "Place ${label().lowercase()} flat on the center of the scanner sensor."
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

    val handFingers = if (selectedHandTab == 0) LeftHand else RightHand
    val candidates = if (selectedMethod == CaptureMethod.SLAP) SlapPositions else handFingers
    val selected = pickedPosition
        ?.takeIf { it in candidates && fingerStatus.statusOf(it).canCapture() }
        ?: candidates.firstOrNull { fingerStatus.statusOf(it).canCapture() }

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
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Colors.Text.Primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onHelpClick) {
                        Icon(
                            imageVector = Icons.Outlined.HelpOutline,
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
                Text(
                    text = "Counts in tabs show fingers captured with each method.",
                    fontSize = 12.sp,
                    color = Colors.Text.Secondary
                )
            }

            // Content dynamic to selected mode
            if (selectedMethod == CaptureMethod.SLAP) {
                item {
                    SlapModeContent(
                        fingerStatus = fingerStatus,
                        selected = selected,
                        onGroupSelect = { pickedPosition = it }
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
                    Text(
                        text = "Switch between Slap and Sequential at any time. Your saved fingerprints are retained.",
                        fontSize = 12.sp,
                        color = Colors.Text.Secondary,
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
            icon = painterResource(R.drawable.capture_symbol),
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
    onGroupSelect: (FingerPosition) -> Unit
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
                    fingerStatus.statusOf(position).isSaved() -> Colors.Success.Main
                    position == selected -> Colors.Brand.BlueSoft
                    else -> Colors.Border.Primary
                }
                Box(Modifier.weight(1f).fillMaxHeight().background(color, RoundedCornerShape(2.dp)))
            }
        }

        Text(
            text = "${fingerStatus.savedFingerCount()} of 10 fingerprints saved · Select a group to continue",
            fontSize = 12.sp,
            color = Colors.Text.Secondary
        )

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
                            status = fingerStatus.statusOf(position),
                            isSelected = position == selected,
                            onClick = { onGroupSelect(position) }
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
    onClick: () -> Unit
) {
    val isSaved = status.isSaved()

    Card(
        modifier = modifier
            .clickable(enabled = status.canCapture(), onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Colors.Background.Primary),
        border = BorderStroke(
            width = if (isSelected) 1.8.dp else 1.dp,
            color = if (isSelected) Colors.Brand.Blue else Colors.Border.Primary
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.ThumbUp,
                    contentDescription = null,
                    tint = if (isSaved) Colors.Success.Main else Colors.Brand.Blue,
                    modifier = Modifier.size(22.dp)
                )

                when {
                    isSaved -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Check, null, tint = Colors.Success.Main, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Done", fontSize = 12.sp, color = Colors.Success.Text, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    isSelected -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = Colors.Brand.Blue, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Selected", fontSize = 12.sp, color = Colors.Brand.Blue, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Schedule, null, tint = Colors.Text.Secondary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Pending", fontSize = 12.sp, color = Colors.Text.Secondary)
                        }
                    }
                }
            }

            Column {
                Text(text = position.label(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Colors.Text.Primary)
                Text(
                    text = position.fingerCountText() + if (isSaved) " · Saved" else "",
                    fontSize = 12.sp,
                    color = Colors.Text.Secondary
                )
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
    val fingers = if (selectedHandTab == 0) LeftHand else RightHand

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Tab Row (Left Hand vs Right Hand)
        TabRow(
            selectedTabIndex = selectedHandTab,
            containerColor = Color.Transparent,
            contentColor = Colors.Brand.Blue,
            divider = { HorizontalDivider(color = Colors.Border.Primary) }
        ) {
            Tab(
                selected = selectedHandTab == 0,
                onClick = { onTabSelect(0) },
                text = { Text("Left Hand (${fingerStatus.savedCount(LeftHand)})", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedHandTab == 1,
                onClick = { onTabSelect(1) },
                text = { Text("Right Hand (${fingerStatus.savedCount(RightHand)})", fontWeight = FontWeight.SemiBold) }
            )
        }

        Text(
            text = "${fingerStatus.savedFingerCount()} of 10 fingerprints saved · Select a finger to continue",
            fontSize = 12.sp,
            color = Colors.Text.Secondary
        )

        // List of Finger Cards
        fingers.forEach { position ->
            FingerRowCard(
                position = position,
                status = fingerStatus.statusOf(position),
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = status.canCapture(), onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Colors.Background.Primary),
        border = BorderStroke(
            width = if (isSelected) 1.8.dp else 1.dp,
            color = if (isSelected) Colors.Brand.Blue else Colors.Border.Primary
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.ThumbUp,
                    contentDescription = null,
                    tint = if (isSaved) Colors.Success.Main else Colors.Text.Secondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = position.label(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Colors.Text.Primary
                    )
                    Text(
                        text = if (isSaved) "1 finger · Saved" else "1 finger · Not captured",
                        fontSize = 12.sp,
                        color = Colors.Text.Secondary
                    )
                }
            }

            if (isSaved) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = Colors.Success.Main, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Done", fontSize = 12.sp, color = Colors.Success.Text, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, null, tint = Colors.Text.Secondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Pending", fontSize = 12.sp, color = Colors.Text.Secondary)
                }
            }
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
            verticalAlignment = Alignment.Top
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
            Text(
                text = selectedGroupText,
                fontSize = 12.sp,
                color = Colors.Text.Secondary
            )
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
// Previews
// --------------------------------------------------
@Preview(showBackground = true)
@Composable
fun FingerprintCollectionScreenPreview() {
    MaterialTheme {
        FingerprintCollectionScreen(
            fingerStatus = mapOf(
                FingerPosition.LEFT_SLAP to FingerCaptureStatus.CAPTURED,
                FingerPosition.LEFT_THUMB to FingerCaptureStatus.CAPTURED
            )
        )
    }
}