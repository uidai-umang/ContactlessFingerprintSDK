package app.gov.uidai.registration.ui.uidentry

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gov.uidai.registration.R
import app.gov.uidai.registration.model.SharedUiState
import app.gov.uidai.registration.model.UIDEntryUiState
import app.gov.uidai.registration.ui.composable.LoadingDialog
import app.gov.uidai.registration.ui.theme.AppButton
import app.gov.uidai.registration.ui.theme.CheckboxRow
import app.gov.uidai.registration.ui.theme.Spacer

@Composable
fun UidEntryRoute(
    sharedUiState: SharedUiState,
    onClearSharedMessage: () -> Unit,
    onNavigateToRegistration: (uidHash: String) -> Unit,
    viewModel: UIDEntryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.checkRegistration(isCheckingFromOnResume = true)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        UidEntryScreen(
            uiState = uiState,
            onUidChanged = viewModel::onUIDChanged,
            onRememberMeCheckChanged = viewModel::onRememberMeChanged,
            onNavigateToRegistration = {
                onNavigateToRegistration(viewModel.getCurrentUidHash())
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

@Composable
fun UidEntryScreen(
    uiState: UIDEntryUiState,
    onUidChanged: (String) -> Unit,
    onRememberMeCheckChanged: (Boolean) -> Unit,
    onNavigateToRegistration: () -> Unit,
    onNavigateToMatchFingers: () -> Unit,
    paddingValues: PaddingValues
) {

    var showDialog by remember {
        mutableStateOf(false)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                paddingValues
            )
    ) {

        val halfHeight = maxHeight * 0.30f

        BackgroundWithGradient()

        Column(
            modifier = Modifier
                .matchParentSize()
                .padding(24.dp)
                .padding(top = halfHeight),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Enter Aadhaar Number",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
            )

            UidEntryUI(
                value = uiState.uid,
                onValueChange = onUidChanged,
                isError = uiState.textInputErrorMessage != null,
                enabled = uiState.isTextFieldEnabled,
                modifier = Modifier.fillMaxWidth()
            )

            CheckboxRow(
                checked = uiState.rememberMe,
                onCheckedChange = onRememberMeCheckChanged,
                text = "Remember Me",
                modifier = Modifier.fillMaxWidth()
            )

            AnimatedVisibility(uiState.textInputErrorMessage != null) {
                Text(
                    text = uiState.textInputErrorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            AnimatedVisibility(uiState.isLoading) {
                Text(
                    text = "Checking Registration...",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(16.dp)

            AnimatedVisibility(uiState.isUserRegistered != null) {
                when (uiState.isUserRegistered) {
                    true -> {
                        RegisteredUserButtons(
                            onNavigateToMatchFingers = onNavigateToMatchFingers
                        )
                    }

                    false -> {
                        UnregisteredUserButtons(
                            onNavigateToRegistration = onNavigateToRegistration
                        )
                    }

                    null -> {

                    }
                }
            }
        }
    }
}

@Composable
fun UidEntryUI(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(value, selection = TextRange(value.length)))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Invisible text field layered in the same Box
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                val filtered = newValue.text.filter { it.isDigit() }.take(12)
                textFieldValue = TextFieldValue(
                    text = filtered,
                    selection = TextRange(filtered.length) // cursor always at end
                )
                if (filtered != value) {
                    onValueChange(filtered)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            enabled = enabled,
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { state ->
                    Log.d("UidEntryUI", "Hidden field focus: ${state.isFocused}")
                }
        ) { innerTextField ->
            Row {
                Row(
                    modifier = modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(12) { index ->
                        val char = value.getOrNull(index)
                        Box(
                            Modifier.width(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (char != null) {
                                // Show entered digit
                                Text(
                                    text = char.toString(),
                                    style = MaterialTheme.typography.titleLarge,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 28.sp
                                )
                            } else {
                                // Show a circle dot instead of text
                                Box(
                                    modifier = Modifier
                                        .size(12.dp) // control dot size here
                                        .background(
                                            MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }


                        if ((index + 1) % 4 == 0 && index != 11) {
                            Spacer(12.dp) // group gap
                        }
                    }
                }
                innerTextField()
            }
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
    onNavigateToRegistration: () -> Unit
) {
    AppButton(
        text = "Register",
        icon = ImageVector.vectorResource(R.drawable.ic_add_person),
        onClick = onNavigateToRegistration
    )
}

@Composable
fun UidTextField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            // Only allow digits and limit to 12 characters
            if (newValue.length <= 12 && newValue.all { it.isDigit() }) {
                onValueChange(newValue)
            }
        },
        label = { Text("Enter 12-digit UID") },
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        enabled = enabled,
        modifier = modifier
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

        /*Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Contactless Finger Validation",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "Powered by UIDAI",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Thin,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center
            )
        }*/
    }
}