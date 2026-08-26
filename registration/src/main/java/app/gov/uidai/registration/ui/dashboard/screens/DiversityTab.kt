package app.gov.uidai.registration.ui.dashboard.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gov.uidai.registration.model.dashboard.DashboardDiversityResponse
import app.gov.uidai.registration.model.dashboard.QuotaLine
import app.gov.uidai.registration.model.dashboard.QuotaStatus
import app.gov.uidai.registration.ui.dashboard.components.quotaStatusColor
import app.gov.uidai.registration.ui.theme.Spacer

@Composable
fun DiversityTab(
    diversity: DashboardDiversityResponse?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isLoading && diversity == null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (diversity == null) return@Column

        DashboardCard(title = "Gender") {
            QuotaLineList(diversity.gender)
        }

        DashboardCard(title = "Age group") {
            QuotaLineList(diversity.ageGroup)
        }
    }
}

@Composable
private fun QuotaLineList(lines: List<QuotaLine>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        lines.forEach { line ->
            QuotaLineRow(line)
            if (line.overridesTotal > 0) {
                OverridesNote(line)
            }
        }
    }
}

@Composable
private fun QuotaLineRow(line: QuotaLine) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(quotaStatusColor(line.quotaStatus))
            )
            Spacer(8.dp)
            Text(
                text = line.key,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = if (line.quotaStatus == QuotaStatus.FULL) "Quota full" else "${line.slotsOpen} slots open",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = quotaStatusColor(line.quotaStatus)
        )
    }
}

@Composable
private fun OverridesNote(line: QuotaLine) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = "${line.overridesTotal} overrides total · ${line.overridesByOperator} by you",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
