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
import app.gov.uidai.registration.ui.theme.dash_alt_row_bg
import app.gov.uidai.registration.ui.theme.dash_alt_row_border
import app.gov.uidai.registration.ui.theme.dash_alt_slots_text
import app.gov.uidai.registration.ui.theme.dash_alt_tag_bg
import app.gov.uidai.registration.ui.theme.dash_alt_tag_border
import app.gov.uidai.registration.ui.theme.dash_alt_tag_text
import app.gov.uidai.registration.ui.theme.dash_modal_ack_bg
import app.gov.uidai.registration.ui.theme.dash_modal_ack_border
import app.gov.uidai.registration.ui.theme.dash_modal_ack_text
import app.gov.uidai.registration.ui.theme.dash_modal_back_border
import app.gov.uidai.registration.ui.theme.dash_modal_back_text
import app.gov.uidai.registration.ui.theme.dash_modal_full_bar_fill
import app.gov.uidai.registration.ui.theme.dash_modal_full_bar_track
import app.gov.uidai.registration.ui.theme.dash_modal_full_button
import app.gov.uidai.registration.ui.theme.dash_modal_full_icon_bg
import app.gov.uidai.registration.ui.theme.dash_modal_full_label
import app.gov.uidai.registration.ui.theme.dash_modal_full_note
import app.gov.uidai.registration.ui.theme.dash_modal_full_snapshot_bg
import app.gov.uidai.registration.ui.theme.dash_modal_full_snapshot_border
import app.gov.uidai.registration.ui.theme.dash_modal_full_title
import app.gov.uidai.registration.ui.theme.dash_modal_full_value
import app.gov.uidai.registration.ui.theme.dash_modal_warn_bar_fill
import app.gov.uidai.registration.ui.theme.dash_modal_warn_bar_track
import app.gov.uidai.registration.ui.theme.dash_modal_warn_button
import app.gov.uidai.registration.ui.theme.dash_modal_warn_icon_bg
import app.gov.uidai.registration.ui.theme.dash_modal_warn_label
import app.gov.uidai.registration.ui.theme.dash_modal_warn_note
import app.gov.uidai.registration.ui.theme.dash_modal_warn_snapshot_bg
import app.gov.uidai.registration.ui.theme.dash_modal_warn_snapshot_border
import app.gov.uidai.registration.ui.theme.dash_modal_warn_value
import app.gov.uidai.registration.ui.theme.dash_text_muted
import app.gov.uidai.registration.ui.theme.dash_text_primary

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
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(isFull)
                Spacer(12.dp)
                Column {
                    Text(
                        text = if (isFull) "Quota is full" else "${critical.key} quota nearly full",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFull) dash_modal_full_title else dash_text_primary
                    )
                    Text(
                        text = if (isFull) {
                            "This capture needs an operator override to proceed."
                        } else {
                            "You can still proceed, or choose an open slot instead."
                        },
                        fontSize = 12.sp,
                        color = dash_text_muted
                    )
                }
            }
            Spacer(16.dp)

            SnapshotBox(critical = critical, isFull = isFull)

            if (isFull) {
                Spacer(16.dp)
                AckBox(operatorId)
            }

            if (quotaCheck.alternatives.isNotEmpty()) {
                Spacer(16.dp)
                SectionTitle("Demographics with open quota")
                Spacer(8.dp)
                quotaCheck.alternatives.forEach { alternative ->
                    AlternativeRow(alternative)
                    Spacer(8.dp)
                }
            }

            Spacer(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onChange,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, dash_modal_back_border)
                ) {
                    Text("Change", color = dash_modal_back_text)
                }
                Button(
                    onClick = {
                        if (isFull) onOverrideConfirmed(critical.dimension(quotaCheck), critical.key) else onProceed()
                    },
                    modifier = Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFull) dash_modal_full_button else dash_modal_warn_button
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
            .background(if (isFull) dash_modal_full_icon_bg else dash_modal_warn_icon_bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = if (isFull) dash_modal_full_title else dash_modal_warn_value
        )
    }
}

@Composable
private fun SnapshotBox(critical: QuotaCheckLine, isFull: Boolean) {
    val bg = if (isFull) dash_modal_full_snapshot_bg else dash_modal_warn_snapshot_bg
    val border = if (isFull) dash_modal_full_snapshot_border else dash_modal_warn_snapshot_border
    val labelColor = if (isFull) dash_modal_full_label else dash_modal_warn_label
    val valueColor = if (isFull) dash_modal_full_value else dash_modal_warn_value
    val barTrack = if (isFull) dash_modal_full_bar_track else dash_modal_warn_bar_track
    val barFill = if (isFull) dash_modal_full_bar_fill else dash_modal_warn_bar_fill
    val noteColor = if (isFull) dash_modal_full_note else dash_modal_warn_note

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
            .padding(14.dp)
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
        Spacer(8.dp)
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
        Spacer(8.dp)
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
            .background(dash_modal_ack_bg)
            .border(1.dp, dash_modal_ack_border, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = dash_modal_ack_text,
            modifier = Modifier.size(14.dp)
        )
        Spacer(8.dp)
        Text(
            text = "This capture will be flagged as an override with operator $operatorId attached — " +
                "biometric data is still saved.",
            fontSize = 11.sp,
            color = dash_modal_ack_text
        )
    }
}

@Composable
private fun AlternativeRow(alternative: Alternative) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(dash_alt_row_bg)
            .border(1.dp, dash_alt_row_border, RoundedCornerShape(9.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(dash_alt_tag_bg)
                .border(1.dp, dash_alt_tag_border, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(text = alternative.key, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = dash_alt_tag_text)
        }
        Spacer(10.dp)
        Column {
            Text(
                text = alternative.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = dash_text_primary
            )
            Text(text = "${alternative.slotsOpen} slots open", fontSize = 10.sp, color = dash_alt_slots_text)
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
