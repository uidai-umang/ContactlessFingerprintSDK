package app.gov.uidai.registration.ui.registration.method

import androidx.annotation.XmlRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gov.uidai.registration.model.CaptureMethod
import app.gov.uidai.registration.model.CaptureMethodUiState
import app.gov.uidai.registration.model.SlapSubOption
import app.gov.uidai.registration.ui.theme.capture_method_active
import app.gov.uidai.registration.ui.theme.capture_method_active_container
import app.gov.uidai.registration.ui.theme.capture_method_bg
import app.gov.uidai.registration.ui.theme.capture_method_border
import app.gov.uidai.registration.ui.theme.capture_method_disabled_bg
import app.gov.uidai.registration.ui.theme.capture_method_disabled_text
import app.gov.uidai.registration.ui.theme.capture_method_icon_chip_bg
import app.gov.uidai.registration.ui.theme.capture_method_info_footer_text
import app.gov.uidai.registration.ui.theme.capture_method_locked_bg
import app.gov.uidai.registration.ui.theme.capture_method_locked_border
import app.gov.uidai.registration.ui.theme.capture_method_locked_text
import app.gov.uidai.registration.ui.theme.capture_method_nav_back_circle
import app.gov.uidai.registration.ui.theme.capture_method_primary
import app.gov.uidai.registration.ui.theme.capture_method_primary_container
import app.gov.uidai.registration.ui.theme.capture_method_progress_bg
import app.gov.uidai.registration.ui.theme.capture_method_progress_border
import app.gov.uidai.registration.ui.theme.capture_method_progress_track_bg
import app.gov.uidai.registration.ui.theme.capture_method_strip_bg
import app.gov.uidai.registration.ui.theme.capture_method_strip_border
import app.gov.uidai.registration.ui.theme.capture_method_text_muted
import app.gov.uidai.registration.ui.theme.capture_method_text_primary
import app.gov.uidai.registration.ui.theme.capture_method_warning_bg
import app.gov.uidai.registration.ui.theme.capture_method_warning_border
import app.gov.uidai.registration.ui.theme.capture_method_warning_text
import app.gov.uidai.registration.ui.theme.md_theme_onSecondaryContainer

@Composable
fun CaptureMethodRoute(
    onNavigateUp: () -> Unit,
    onContinue: () -> Unit,
    viewModel: CaptureMethodViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CaptureMethodScreen(
        uiState = uiState,
        onSelectMethod = viewModel::selectMethod,
        onSelectSlapSubOption = viewModel::selectSlapSubOption,
        onContinue = {
            viewModel.onContinue()
            onContinue()
        },
        onBack = onNavigateUp
    )
}

@Composable
fun CaptureMethodScreen(
    uiState: CaptureMethodUiState,
    onSelectMethod: (CaptureMethod) -> Unit,
    onSelectSlapSubOption: (SlapSubOption) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    val isMethodReadyToContinue = when (uiState.selectedMethod) {
        CaptureMethod.SLAP -> uiState.selectedSlapSubOption != null
        CaptureMethod.SEQUENTIAL -> true
        null -> false
    }

    Column(modifier = Modifier.fillMaxSize().background(capture_method_bg)) {
        CaptureMethodTopBar(onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (uiState.isLocked) {
                    "Capture method is locked for this session."
                } else {
                    "Choose how fingerprints will be captured for this session. You can switch methods until the first finger is saved."
                },
                fontSize = 13.sp,
                color = capture_method_text_primary,
                lineHeight = 20.sp
            )

            SlapCaptureCard(
                isSelected = uiState.selectedMethod == CaptureMethod.SLAP,
                isLocked = uiState.isLocked,
                selectedSubOption = uiState.selectedSlapSubOption,
                completedSubOptions = uiState.completedSlapSubOptions,
                onClick = { onSelectMethod(CaptureMethod.SLAP) },
                onSelectSubOption = onSelectSlapSubOption
            )

            SequentialCaptureCard(
                isSelected = uiState.selectedMethod == CaptureMethod.SEQUENTIAL,
                isLocked = uiState.isLocked,
                fingersAlreadyCaptured = uiState.fingersAlreadyCaptured,
                onClick = { onSelectMethod(CaptureMethod.SEQUENTIAL) }
            )

            if (uiState.isLocked) {
                CaptureLockedWarningNote()
            }
        }

        CaptureMethodBottomBar(
            isLocked = uiState.isLocked,
            isMethodReadyToContinue = isMethodReadyToContinue,
            onContinue = onContinue
        )
    }
}

@Composable
private fun CaptureMethodTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(capture_method_bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(capture_method_nav_back_circle)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Go Back",
                tint = capture_method_text_primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = "New Collection",
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = capture_method_text_primary
        )
    }
}

@Composable
private fun SlapCaptureCard(
    isSelected: Boolean,
    isLocked: Boolean,
    selectedSubOption: SlapSubOption?,
    completedSubOptions: Set<SlapSubOption>,
    onClick: () -> Unit,
    onSelectSubOption: (SlapSubOption) -> Unit
) {
    val borderColor = when {
        isLocked -> capture_method_locked_border
        isSelected -> capture_method_primary
        else -> capture_method_border
    }
    val bgColor = when {
        isLocked -> capture_method_locked_bg
        isSelected -> capture_method_primary_container
        else -> Color.White
    }
    val iconChipBg = when {
        isLocked -> capture_method_locked_border
        isSelected -> capture_method_primary
        else -> capture_method_icon_chip_bg
    }
    val textColor = when {
        isLocked -> capture_method_locked_text
        isSelected -> capture_method_primary
        else -> capture_method_text_primary
    }
    val descColor = when {
        isLocked -> capture_method_locked_text
        isSelected -> capture_method_primary.copy(alpha = 0.8f)
        else -> capture_method_text_muted
    }

    val dividerColor = when {
        !isLocked && !isSelected -> capture_method_locked_border
        isSelected -> capture_method_primary.copy(alpha = 0.2f)
        else -> capture_method_strip_border
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isLocked) 0.5f else 1f)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(enabled = !isLocked, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(iconChipBg),
                contentAlignment = Alignment.Center
            ) {
                Text("\uD83D\uDD90", fontSize = 20.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "Slap capture",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = textColor,
                        modifier = Modifier.weight(1f)
                    )
                    if (isLocked) {
                        Text("🔒", fontSize = 16.sp)
                    } else {
                        RadioIndicator(isSelected = isSelected)
                    }
                }
                Text(
                    text = if (isLocked) {
                        "Not available — sequential capture already in progress."
                    } else {
                        "Capture all four fingers of each hand together using the palm overlay, then thumbs separately. Faster per resident."
                    },
                    fontSize = 12.sp,
                    color = descColor
                )
            }
        }

        if(!isLocked) HorizontalDivider(color = dividerColor, thickness = 1.dp)

        if(!isSelected && !isLocked) {
            SlabSubOptionNeutralState()
        }

        if (isSelected && !isLocked) {
            SlapSubOptionsRow(
                isInteractive = isSelected,
                selectedOption = selectedSubOption,
                completedOptions = completedSubOptions,
                onSelectOption = onSelectSubOption
            )
        }
    }
}

@Composable
private fun SlabSubOptionNeutralState() {
    Row(modifier = Modifier.fillMaxWidth().background(color = md_theme_onSecondaryContainer).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {

        Text(
            text = "\uD83D\uDC48 Left slap"
        )

        Text(
            text = "\uD83D\uDC49 Right slap"
        )

        Text(
            text = "\uD83D\uDC4D Thumb"
        )

    }
}


@Composable
private fun SlapSubOptionsRow(
    isInteractive: Boolean,
    selectedOption: SlapSubOption?,
    completedOptions: Set<SlapSubOption>,
    onSelectOption: (SlapSubOption) -> Unit
) {
    Column(
        modifier = Modifier.background(capture_method_strip_bg)
    ){
        Text(
            text = "SELECT GROUPS TO CAPTURE",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = capture_method_primary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(capture_method_strip_bg)
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SlapSubOption.entries.forEach { option ->
                SubOptionChip(
                    option = option,
                    isInteractive = isInteractive,
                    isSelected = isInteractive && selectedOption == option,
                    isCompleted = option in completedOptions,
                    onClick = { onSelectOption(option) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SubOptionChip(
    option: SlapSubOption,
    isInteractive: Boolean,
    isSelected: Boolean,
    isCompleted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (emoji, title, subtitle) = when (option) {
        SlapSubOption.LEFT_SLAP -> Triple("\uD83D\uDC48", "Left slap", "4 fingers")
        SlapSubOption.RIGHT_SLAP -> Triple("\uD83D\uDC49", "Right slap", "4 fingers")
        SlapSubOption.THUMBS -> Triple("👍", "Thumbs", "2 fingers")
    }
    // Selectable once the Slap card itself is chosen — and only if this
    // particular sub-capture hasn't already been done this session.
    val isClickable = isInteractive && !isCompleted

    val bgColor = when {
        isSelected -> capture_method_primary
        isCompleted -> capture_method_locked_border
        isInteractive -> Color.White
        else -> capture_method_strip_bg
    }
    val contentColor = when {
        isSelected -> Color.White
        isCompleted -> capture_method_locked_text
        isInteractive -> capture_method_primary
        else -> capture_method_text_muted
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .then(
                if (isInteractive && !isSelected) {
                    Modifier.border(1.dp, capture_method_primary, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .clickable(enabled = isClickable, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (isCompleted) "✓" else emoji, fontSize = 16.sp)
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = contentColor)
        Text(if (isCompleted) "Done" else subtitle, fontSize = 9.sp, color = contentColor.copy(alpha = 0.85f))
    }
}

@Composable
private fun SequentialCaptureCard(
    isSelected: Boolean,
    isLocked: Boolean,
    fingersAlreadyCaptured: Int,
    onClick: () -> Unit
) {
    val borderColor = when {
        isLocked -> capture_method_active
        isSelected -> capture_method_primary
        else -> capture_method_border
    }
    val bgColor = when {
        isLocked -> capture_method_active_container
        isSelected -> capture_method_primary_container
        else -> Color.White
    }
    val iconChipBg = when {
        isLocked -> capture_method_active
        isSelected -> capture_method_primary
        else -> capture_method_icon_chip_bg
    }
    val textColor = when {
        isLocked -> capture_method_active
        isSelected -> capture_method_primary
        else -> capture_method_text_primary
    }
    val descColor = when {
        isLocked -> capture_method_active.copy(alpha = 0.85f)
        isSelected -> capture_method_primary.copy(alpha = 0.8f)
        else -> capture_method_text_muted
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(enabled = !isLocked, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(iconChipBg),
                contentAlignment = Alignment.Center
            ) {
                Text("☝️", fontSize = 20.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "Single finger — sequential",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = textColor,
                        modifier = Modifier.weight(1f)
                    )
                    RadioIndicator(
                        isSelected = isSelected || isLocked,
                        fillColor = if (isLocked) capture_method_active else capture_method_primary
                    )
                }
                Text(
                    text = if (isLocked) {
                        "In progress — $fingersAlreadyCaptured fingers captured so far. Continue to add more."
                    } else {
                        "Capture one finger at a time in a guided sequence. Minimum 4 fingers required. Better for residents with difficulty using slap."
                    },
                    fontSize = 12.sp,
                    color = descColor
                )
            }
        }

        if (isLocked) {
            SessionProgressStrip(fingersAlreadyCaptured = fingersAlreadyCaptured)
        } else if (isSelected) {
            SequentialFooterNote()
        }
    }
}

@Composable
private fun SequentialFooterNote() {
    Column {
        HorizontalDivider(color = capture_method_strip_border, thickness = 1.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(capture_method_strip_bg)
                .padding(12.dp)
        ) {
            Text(
                text = "ⓘ Once the first finger is saved you cannot switch to slap mode. Complete the session sequentially.",
                fontSize = 11.sp,
                color = capture_method_info_footer_text
            )
        }
    }
}

@Composable
private fun SessionProgressStrip(fingersAlreadyCaptured: Int) {
    val progress = (fingersAlreadyCaptured.toFloat() / 4f).coerceIn(0f, 1f)
    Column {
        HorizontalDivider(color = capture_method_progress_border, thickness = 1.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(capture_method_progress_bg)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SESSION PROGRESS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = capture_method_active,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "$fingersAlreadyCaptured / min. 4",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = capture_method_active
                )
            }
            Box(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(capture_method_progress_track_bg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .clip(RoundedCornerShape(20.dp))
                        .background(capture_method_active)
                )
            }
        }
    }
}

@Composable
private fun CaptureLockedWarningNote() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(capture_method_warning_bg)
            .border(1.dp, capture_method_warning_border, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = "⚠ You cannot switch to slap mode once sequential capture has started. Please continue with the current session or finish and start a new collection.",
            fontSize = 12.sp,
            color = capture_method_warning_text,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun RadioIndicator(
    isSelected: Boolean,
    fillColor: Color = capture_method_primary
) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .then(
                if (isSelected) Modifier.background(fillColor)
                else Modifier.border(2.dp, capture_method_border, CircleShape)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
private fun CaptureMethodBottomBar(
    isLocked: Boolean,
    isMethodReadyToContinue: Boolean,
    onContinue: () -> Unit
) {
    val enabled = isLocked || isMethodReadyToContinue
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(capture_method_bg)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Button(
            onClick = onContinue,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = capture_method_primary,
                contentColor = Color.White,
                disabledContainerColor = capture_method_disabled_bg,
                disabledContentColor = capture_method_disabled_text
            )
        ) {
            Text(
                text = when {
                    isLocked -> "Continue capture →"
                    isMethodReadyToContinue -> "Continue to guidelines →"
                    else -> "Continue"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
@Preview
fun Preview() {
    SlapCaptureCard(
        isSelected = false,
        isLocked = false,
        selectedSubOption = null,
        completedSubOptions = emptySet(),
        onClick = {},
        onSelectSubOption = {}
    )
}
