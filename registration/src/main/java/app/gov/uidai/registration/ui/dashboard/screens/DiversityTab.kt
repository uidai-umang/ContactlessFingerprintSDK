package app.gov.uidai.registration.ui.dashboard.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gov.uidai.registration.model.dashboard.DashboardDiversityResponse
import app.gov.uidai.registration.model.dashboard.QuotaLine
import app.gov.uidai.registration.model.dashboard.QuotaStatus
import app.gov.uidai.registration.ui.dashboard.components.ageGroupDotColor
import app.gov.uidai.registration.ui.dashboard.components.genderDotColor
import app.gov.uidai.registration.ui.dashboard.components.quotaStatusColor
import app.gov.uidai.registration.ui.theme.Spacer
import app.gov.uidai.registration.ui.theme.dash_override_note_bg
import app.gov.uidai.registration.ui.theme.dash_override_note_border
import app.gov.uidai.registration.ui.theme.dash_override_note_text
import app.gov.uidai.registration.ui.theme.dash_text_primary

private val DIVIDER_COLOR = Color(0xFFEEF2F8)

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
            QuotaLineList(diversity.gender) { _, key -> genderDotColor(key) }
        }

        DashboardCard(title = "Age group") {
            QuotaLineList(diversity.ageGroup) { index, _ -> ageGroupDotColor(index) }
        }
    }
}

@Composable
private fun QuotaLineList(lines: List<QuotaLine>, dotColorFor: (Int, String) -> Color) {
    Column {
        lines.forEachIndexed { index, line ->
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                QuotaLineRow(line, dotColorFor(index, line.key))
                if (line.overridesTotal > 0) {
                    Spacer(6.dp)
                    OverridesNote(line)
                }
            }
            if (index < lines.lastIndex) {
                HorizontalDivider(color = DIVIDER_COLOR, thickness = 1.dp)
            }
        }
    }
}

@Composable
private fun QuotaLineRow(line: QuotaLine, dotColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(8.dp)
            Text(
                text = line.key,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = dash_text_primary
            )
        }
        Text(
            text = if (line.quotaStatus == QuotaStatus.FULL) "Quota full" else "${line.slotsOpen} slots open",
            fontSize = 12.sp,
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
            .padding(start = 22.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(dash_override_note_bg)
            .border(1.dp, dash_override_note_border, RoundedCornerShape(7.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = "${line.overridesTotal} overrides total · ${line.overridesByOperator} by you",
            fontSize = 11.sp,
            color = dash_override_note_text
        )
    }
}
