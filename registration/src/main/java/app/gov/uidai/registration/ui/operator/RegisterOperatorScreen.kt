package app.gov.uidai.registration.ui.operator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gov.uidai.registration.model.RegisterOperatorUiState
import app.gov.uidai.registration.ui.composable.LoadingDialog
import app.gov.uidai.registration.ui.theme.AppButton
import app.gov.uidai.registration.ui.theme.Spacer
import app.gov.uidai.registration.ui.uidentry.BackgroundWithGradient

@Composable
fun RegisterOperatorRoute(
    initialOperatorRefId: String?,
    onRegistered: (operatorId: String) -> Unit,
    viewModel: RegisterOperatorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialOperatorRefId) { viewModel.prefill(initialOperatorRefId) }

    LaunchedEffect(uiState.registeredOperatorId) {
        uiState.registeredOperatorId?.let {
            onRegistered(it)
            viewModel.onNavigated()
        }
    }

    RegisterOperatorScreen(
        uiState = uiState,
        onOperatorRefIdChanged = viewModel::onOperatorRefIdChanged,
        onRegister = viewModel::register
    )
    LoadingDialog(uiState.isLoading, "Registering Operator ...")
}

@Composable
fun RegisterOperatorScreen(
    uiState: RegisterOperatorUiState,
    onOperatorRefIdChanged: (String) -> Unit,
    onRegister: () -> Unit
) {
    Scaffold { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            BackgroundWithGradient()

            Column(
                modifier = Modifier
                    .matchParentSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
                    .padding(top = maxHeight * 0.30f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Register Operator",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(16.dp)

                OutlinedTextField(
                    value = uiState.operatorRefId,
                    onValueChange = onOperatorRefIdChanged,
                    label = { Text("Operator ID") },
                    placeholder = { Text("dev-operator-001") },
                    singleLine = true,
                    isError = uiState.errorMessage != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onRegister() }),
                    modifier = Modifier.fillMaxWidth()
                )

                AnimatedVisibility(uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(16.dp)

                AppButton(
                    text = "Continue",
                    onClick = onRegister,
                    enabled = uiState.canSubmit
                )
            }
        }
    }
}
