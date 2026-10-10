package app.gov.uidai.registration.ui.uidentry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gov.uidai.registration.R
import app.gov.uidai.registration.model.SharedUiState
import app.gov.uidai.registration.model.UIDEntryUiState
import app.gov.uidai.registration.model.resident.Gender
import app.gov.uidai.registration.model.resident.MIN_RESIDENT_AGE_YEARS
import app.gov.uidai.registration.model.resident.ResidentInput
import app.gov.uidai.registration.ui.composable.LoadingDialog
import app.gov.uidai.registration.ui.theme.AppButton
import app.gov.uidai.registration.ui.theme.Spacer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DobDisplayFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

@Composable
fun UidEntryRoute(
    sharedUiState: SharedUiState,
    onClearSharedMessage: () -> Unit,
    onNavigateToRegistration: (ResidentInput) -> Unit,
    viewModel: UIDEntryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.checkRegistration()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        UidEntryScreen(
            uiState = uiState,
            onRefIdChanged = viewModel::onRefIdChanged,
            onDobSelected = viewModel::onDobSelected,
            onGenderSelected = viewModel::onGenderSelected,
            onNavigateToRegistration = {
                viewModel.residentInput()?.let(onNavigateToRegistration)
            },
            onNavigateToMatchFingers = { /* UserInfoFragment out of scope per your instruction */ },
            paddingValues = paddingValues
        )
        LoadingDialog(sharedUiState.isLoadingAssets, "Loading Assets ...")
        LoadingDialog(sharedUiState.isLoadingEmbedder, "Initializing Embedder...")

        LaunchedEffect(sharedUiState.message) {
            sharedUiState.message?.let {
                snackbarHostState.showSnackbar(it, withDismissAction = true)
                onClearSharedMessage()
            }
        }
        LaunchedEffect(uiState.message) {
            uiState.message?.let {
                snackbarHostState.showSnackbar(it, withDismissAction = true)
                viewModel.clearMessage()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UidEntryScreen(
    uiState: UIDEntryUiState,
    onRefIdChanged: (String) -> Unit,
    onDobSelected: (LocalDate) -> Unit,
    onGenderSelected: (Gender) -> Unit,
    onNavigateToRegistration: () -> Unit,
    onNavigateToMatchFingers: () -> Unit,
    paddingValues: PaddingValues
) {
    var showDatePicker by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        val halfHeight = maxHeight * 0.30f

        BackgroundWithGradient()

        Column(
            modifier = Modifier
                .matchParentSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .padding(top = halfHeight),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Enter Resident Details",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
            )

            Spacer(16.dp)

            OutlinedTextField(
                value = uiState.refId,
                onValueChange = onRefIdChanged,
                label = { Text("Resident Ref ID") },
                placeholder = { Text("test-001") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(12.dp)

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = uiState.dob?.format(DobDisplayFormat).orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date of Birth") },
                    placeholder = { Text("Select date") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Transparent overlay: a read-only text field swallows taps.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showDatePicker = true }
                )
            }

            Spacer(12.dp)

            Text(
                text = "Gender",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(4.dp)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Gender.entries.forEachIndexed { index, gender ->
                    SegmentedButton(
                        selected = uiState.gender == gender,
                        onClick = { onGenderSelected(gender) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = Gender.entries.size
                        ),
                        label = { Text(gender.label) }
                    )
                }
            }

            AnimatedVisibility(uiState.isLoading) {
                Text(
                    text = "Checking Registration...",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(16.dp)

            if (uiState.isUserRegistered == true) {
                RegisteredUserButtons(onNavigateToMatchFingers = onNavigateToMatchFingers)
            } else {
                UnregisteredUserButtons(
                    enabled = uiState.canRegister,
                    onNavigateToRegistration = onNavigateToRegistration
                )
            }
        }
    }

    if (showDatePicker) {
        val latestDob = LocalDate.now().minusYears(MIN_RESIDENT_AGE_YEARS.toLong())
        val latestDobMillis = latestDob.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val selectedMillis = uiState.dob?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()

        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedMillis,
            initialDisplayedMonthMillis = selectedMillis ?: latestDobMillis,
            yearRange = 1900..latestDob.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= latestDobMillis
                override fun isSelectableYear(year: Int) = year <= latestDob.year
            }
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedDateMillis != null,
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onDobSelected(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            )
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
fun RegisteredUserButtons(
    onNavigateToMatchFingers: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Spacer(8.dp)
        AppButton(
            text = "Check Matching Scores",
            icon = ImageVector.vectorResource(R.drawable.ic_match_finger),
            onClick = onNavigateToMatchFingers,
            isOutlined = true
        )
        Spacer(8.dp)
    }
}

@Composable
fun UnregisteredUserButtons(
    enabled: Boolean,
    onNavigateToRegistration: () -> Unit
) {
    AppButton(
        text = "Register",
        icon = ImageVector.vectorResource(R.drawable.ic_add_person),
        onClick = onNavigateToRegistration,
        enabled = enabled
    )
}

@Composable
fun BackgroundWithGradient() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Background image
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationY = -0.30f * size.height
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.dashboard_background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()

            )
            // Gradient overlay
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            )
            // Aadhaar logo
            Image(
                painter = painterResource(R.drawable.aadhaar_logo_with_background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(120.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        clip = false
                    )
            )
        }
    }
}