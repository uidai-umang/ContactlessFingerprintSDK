package app.gov.uidai.registration.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gov.uidai.registration.model.dashboard.Alternative
import app.gov.uidai.registration.model.dashboard.QuotaCheckLine
import app.gov.uidai.registration.model.dashboard.QuotaCheckResponse
import app.gov.uidai.registration.model.dashboard.QuotaStatus
import app.gov.uidai.registration.ui.theme.AppButton
import app.gov.uidai.registration.ui.theme.Spacer
import app.gov.uidai.registration.ui.theme.successContainer

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
    val headerColor = quotaStatusColor(quotaCheck.worstStatus)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
                text = if (isFull) "Quota is full" else "${critical.key} quota nearly full",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = headerColor
            )
            Spacer(12.dp)

            // quota/check only returns slots_open (not total capacity), so this bar shows
            // a status-based approximation of fill level rather than an exact percentage.
            val fillFraction = when (critical.quotaStatus) {
                QuotaStatus.FULL -> 1f
                QuotaStatus.WARN -> 0.85f
                else -> 0.5f
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fillFraction)
                        .height(8.dp)
                        .background(headerColor)
                )
            }
            Spacer(16.dp)

            if (quotaCheck.alternatives.isNotEmpty()) {
                Text(
                    text = "Suggested alternatives",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(8.dp)
                quotaCheck.alternatives.forEach { alternative ->
                    AlternativeRow(alternative)
                    Spacer(6.dp)
                }
                Spacer(8.dp)
            }

            if (isFull) {
                Text(
                    text = "Proceeding will log an override against operator $operatorId.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(12.dp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onChange, modifier = Modifier.weight(1f)) {
                    Text("Change")
                }
                if (isFull) {
                    AppButton(
                        text = "Override & save",
                        onClick = {
                            onOverrideConfirmed(critical.dimension(quotaCheck), critical.key)
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    AppButton(
                        text = "Proceed",
                        onClick = onProceed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(8.dp)
        }
    }
}

@Composable
private fun AlternativeRow(alternative: Alternative) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(successContainer)
        )
        Spacer(8.dp)
        Text(
            text = "${alternative.label} — ${alternative.slotsOpen} slots open",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
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
