package app.gov.uidai.capture.ui.consent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gov.uidai.capture.BuildConfig
import app.gov.uidai.capture.R
import app.gov.uidai.registration.ui.theme.Colors
import kotlinx.coroutines.flow.first

data class Consent(val title: String, val message: String)

private val ConsentPoints = listOf(
    Consent(
        title = "What we collect",
        message = "Contactless fingerprint images is collected and with anonymised demographic data (age group and gender only)— no name, no Aadhaar number)."
    ),
    Consent(
        title = "Legal basis",
        message = "It is collected under Section 23(2)(q) of the Aadhaar Act, 2016 and Section 17(2)(b) of the DPDP Act, 2023 for biometric research. Not used to make any decision affecting you."
    ),
    Consent(
        title = "Retention",
        message = "Data will be retained for up to 5 years for model validation and regulatory audit, then permanently deleted."
    ),
    Consent(
        title = "Your participation",
        message = "The Participation is fully voluntary and has no impact on your Aadhaar service. You may withdraw at any time — data already captured will be retained for the programme duration per Section 17(4) DPDP Act, 2023."
    ),
    Consent(
        title = "What we collect",
        message = "Contactless fingerprint images is collected and with anonymised demographic data (age group and gender only)— no name, no Aadhaar number)."
    ),
    Consent(
        title = "Legal basis",
        message = "It is collected under Section 23(2)(q) of the Aadhaar Act, 2016 and Section 17(2)(b) of the DPDP Act, 2023 for biometric research. Not used to make any decision affecting you."
    ),
    Consent(
        title = "Retention",
        message = "Data will be retained for up to 5 years for model validation and regulatory audit, then permanently deleted."
    ),
    Consent(
        title = "Your participation",
        message = "The Participation is fully voluntary and has no impact on your Aadhaar service. You may withdraw at any time — data already captured will be retained for the programme duration per Section 17(4) DPDP Act, 2023."
    ),
    Consent(
        title = "What we collect",
        message = "Contactless fingerprint images is collected and with anonymised demographic data (age group and gender only)— no name, no Aadhaar number)."
    ),
    Consent(
        title = "Legal basis",
        message = "It is collected under Section 23(2)(q) of the Aadhaar Act, 2016 and Section 17(2)(b) of the DPDP Act, 2023 for biometric research. Not used to make any decision affecting you."
    ),
    Consent(
        title = "Retention",
        message = "Data will be retained for up to 5 years for model validation and regulatory audit, then permanently deleted."
    ),
    Consent(
        title = "Your participation",
        message = "The Participation is fully voluntary and has no impact on your Aadhaar service. You may withdraw at any time — data already captured will be retained for the programme duration per Section 17(4) DPDP Act, 2023."
    ),

)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsentScreen(
    txnId: String,
    onBack: () -> Unit,
    onProceed: (txnId: String) -> Unit,
    onHelpClick: () -> Unit = {},
    onDebugSettings: () -> Unit = {},
    showDebugButton: Boolean = BuildConfig.DEBUG
) {
    var isConsentChecked by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // The consent checkbox stays locked until the operator has scrolled to the last point.
    // Sticky: scrolling back up afterwards does not lock it again.
    val scrollState = rememberScrollState()
    var hasReadAll by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(scrollState) {
        snapshotFlow { !scrollState.canScrollForward }.first { it }
        hasReadAll = true
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Consent",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Colors.Text.Primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            ConsentBottomBar(
                isConsentChecked = isConsentChecked,
                onConsentChange = { isConsentChecked = it },
                consentEnabled = hasReadAll,
                showDebugButton = showDebugButton,
                onDebugSettings = onDebugSettings,
                isLoading = isLoading,
                onContinueClick = {
                    isLoading = true
                    onProceed(txnId)
                }
            )
        },
        containerColor = Colors.customColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "Purpose & your rights",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Colors.Text.Primary,
                modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
            )

            // Only the consent points scroll; the header above and the checkbox / button below stay put.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ConsentPoints.forEach { ConsentItemCard(it) }
            }
        }
    }
}

@Composable
private fun ConsentBottomBar(
    isConsentChecked: Boolean,
    onConsentChange: (Boolean) -> Unit,
    consentEnabled: Boolean,
    showDebugButton: Boolean,
    onDebugSettings: () -> Unit,
    isLoading: Boolean,
    onContinueClick: () -> Unit
) {
    Surface(color = Colors.Background.Primary, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Consent agreement: dimmed and not clickable until every point has been scrolled through.
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Colors.Warning.Primary.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (consentEnabled) 1f else 0.5f)
                    .clickable(enabled = consentEnabled) { onConsentChange(!isConsentChecked) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Checkbox(
                        checked = isConsentChecked,
                        onCheckedChange = onConsentChange,
                        enabled = consentEnabled,
                        colors = CheckboxDefaults.colors(
                            checkedColor = Colors.Brand.Blue,
                            uncheckedColor = Colors.Text.Secondary
                        ),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "The resident has been informed of the purpose, data collected and their right to decline. Both parties agree to proceed",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp,
                        color = Colors.Text.Primary
                    )
                }
            }

            if (showDebugButton) {
                OutlinedButton(onClick = onDebugSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.btn_debug_settings))
                }
            }

            Button(
                onClick = onContinueClick,
                enabled = isConsentChecked && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Colors.Button.Primary,
                    contentColor = Colors.Button.OnPrimary,
                    disabledContainerColor = Colors.Button.Disabled,
                    disabledContentColor = Colors.Button.OnDisabled
                )
            ) {
                Text(
                    text = if (isLoading) stringResource(R.string.btn_loading_camera) else "Continue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ConsentItemCard(
    consent: Consent,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Colors.Background.Primary,
        shadowElevation = 0.5.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Colors.Success.Main,
                modifier = Modifier.size(22.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = consent.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Colors.Text.Primary
                )
                Text(
                    text = consent.message,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Colors.Text.Secondary
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun ConsentScreenPreview() {
    MaterialTheme {
        ConsentScreen(txnId = "", onBack = {}, onProceed = {}, showDebugButton = false)
    }
}