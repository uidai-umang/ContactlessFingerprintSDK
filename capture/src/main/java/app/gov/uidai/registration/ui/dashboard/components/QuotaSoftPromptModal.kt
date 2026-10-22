package app.gov.uidai.registration.ui.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gov.uidai.registration.model.dashboard.Alternative
import app.gov.uidai.registration.model.dashboard.QuotaCheckLine
import app.gov.uidai.registration.model.dashboard.QuotaCheckResponse
import app.gov.uidai.registration.model.dashboard.QuotaStatus
import app.gov.uidai.registration.ui.dashboard.screens.SectionTitle
import app.gov.uidai.registration.ui.theme.Spacer
import app.gov.uidai.registration.ui.theme.Colors
import app.gov.uidai.registration.ui.theme.Spacing
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotaSoftPromptModal(
    quotaCheck: QuotaCheckResponse,
    operatorId: String,
    onDismiss: () -> Unit,
    onChange: () -> Unit,
    onProceed: () -> Unit,
    onOverrideConfirmed: (dimension: String, key: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val isFull = quotaCheck.worstStatus == QuotaStatus.FULL
    val critical = criticalLine(quotaCheck)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.Xl).padding(bottom = Spacing.Xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(isFull)
                Spacer(Spacing.M)
                Column {
                    Text(
                        text = if (isFull) "Quota is full" else "${critical.key} quota nearly full",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFull) Colors.Failure.Text else Colors.Text.Primary
                    )
                    Text(
                        text = if (isFull) {
                            "This capture needs an operator override to proceed."
                        } else {
                            "You can still proceed, or choose an open slot instead."
                        },
                        fontSize = 12.sp,
                        color = Colors.Text.Tertiary
                    )
                }
            }
            Spacer(Spacing.L)

            SnapshotBox(critical = critical, isFull = isFull)

            if (isFull) {
                Spacer(Spacing.L)
                AckBox(operatorId)
            }

            if (quotaCheck.alternatives.isNotEmpty()) {
                Spacer(Spacing.L)
                SectionTitle("Demographics with open quota")
                Spacer(Spacing.S)
                quotaCheck.alternatives.forEach { alternative ->
                    AlternativeRow(alternative)
                    Spacer(Spacing.S)
                }
            }

            Spacer(Spacing.S)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                OutlinedButton(
                    onClick = onChange,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Colors.Border.Primary)
                ) {
                    Text("Change", color = Colors.Text.Secondary)
                }
                Button(
                    onClick = {
                        if (isFull) onOverrideConfirmed(critical.dimension(quotaCheck), critical.key) else onProceed()
                    },
                    modifier = Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFull) Colors.Failure.Main else Colors.Warning.Main
                    )
                ) {
                    Text(if (isFull) "Override & save ➤" else "Proceed with resident ›", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun IconChip(isFull: Boolean) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFull) Colors.Failure.Container else Colors.Warning.Container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = if (isFull) Colors.Failure.Text else Colors.Warning.Main
        )
    }
}

@Composable
private fun SnapshotBox(critical: QuotaCheckLine, isFull: Boolean) {
    val bg = if (isFull) Colors.Failure.Container else Colors.Warning.Container
    val border = if (isFull) Colors.Failure.Border else Colors.Warning.Border
    val labelColor = if (isFull) Colors.Failure.Text else Colors.Warning.Text
    val valueColor = if (isFull) Colors.Failure.Main else Colors.Warning.Main
    val barTrack = if (isFull) Colors.Failure.Border else Colors.Warning.Container
    val barFill = if (isFull) Colors.Failure.Main else Colors.Warning.Main
    val noteColor = if (isFull) Colors.Failure.Text else Colors.Warning.Text

    // quota/check only returns slots_open (not total capacity), so the fill amount
    // is a status-based approximation rather than an exact percentage — 100% for FULL.
    val fillFraction = when {
        isFull -> 1f
        critical.quotaStatus == QuotaStatus.WARN -> 0.85f
        else -> 0.5f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .padding(Spacing.L)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = critical.key, fontSize = 12.sp, color = labelColor)
            Text(
                text = "${critical.slotsOpen} slots open",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }
        Spacer(Spacing.S)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(barTrack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fillFraction)
                    .height(8.dp)
                    .background(barFill)
            )
        }
        Spacer(Spacing.S)
        Text(
            text = if (isFull) {
                "This demographic has reached its collection target."
            } else {
                "Nearing target — consider redirecting non-priority residents."
            },
            fontSize = 11.sp,
            color = noteColor
        )
    }
}

@Composable
private fun AckBox(operatorId: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Colors.Warning.Container)
            .border(1.dp, Colors.Warning.Border, RoundedCornerShape(8.dp))
            .padding(Spacing.S),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = Colors.Warning.Text,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Spacing.S)
        Text(
            text = "This capture will be flagged as an override with operator $operatorId attached — " +
                "biometric data is still saved.",
            fontSize = 11.sp,
            color = Colors.Warning.Text
        )
    }
}

@Composable
private fun AlternativeRow(alternative: Alternative) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Colors.Success.Container)
            .border(1.dp, Colors.Success.Border, RoundedCornerShape(9.dp))
            .padding(Spacing.S),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Colors.Success.Container)
                .border(1.dp, Colors.Success.Border, RoundedCornerShape(6.dp))
                .padding(horizontal = Spacing.S, vertical = Spacing.Xxs)
        ) {
            Text(text = alternative.key, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Colors.Success.Text)
        }
        Spacer(Spacing.S)
        Column {
            Text(
                text = alternative.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Colors.Text.Primary
            )
            Text(text = "${alternative.slotsOpen} slots open", fontSize = 10.sp, color = Colors.Success.Main)
        }
    }
}

private fun criticalLine(quotaCheck: QuotaCheckResponse): QuotaCheckLine =
    if (quotaCheck.ageGroup.quotaStatus.severity() > quotaCheck.gender.quotaStatus.severity()) {
        quotaCheck.ageGroup
    } else {
        quotaCheck.gender
    }

private fun QuotaCheckLine.dimension(quotaCheck: QuotaCheckResponse): String =
    if (this === quotaCheck.ageGroup) "age_group" else "gender"

private fun QuotaStatus.severity(): Int = when (this) {
    QuotaStatus.FULL -> 2
    QuotaStatus.WARN -> 1
    QuotaStatus.OPEN, QuotaStatus.UNKNOWN -> 0
}
