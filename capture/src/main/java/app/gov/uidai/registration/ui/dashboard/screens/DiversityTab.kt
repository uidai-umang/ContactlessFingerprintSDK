package app.gov.uidai.registration.ui.dashboard.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gov.uidai.registration.model.dashboard.DashboardDiversityResponse
import app.gov.uidai.registration.model.dashboard.QuotaLine
import app.gov.uidai.registration.model.dashboard.QuotaStatus
import app.gov.uidai.registration.ui.dashboard.components.DashColors
import app.gov.uidai.registration.ui.dashboard.components.WhiteSectionCard
import app.gov.uidai.registration.ui.dashboard.components.formatCount
import app.gov.uidai.registration.ui.dashboard.components.genderIcon
import app.gov.uidai.registration.ui.dashboard.components.genderLabel
import app.gov.uidai.registration.ui.theme.Colors

@Composable
fun DiversityTab(
    diversity: DashboardDiversityResponse?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isLoading && diversity == null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (diversity == null) return@Column

        WhiteSectionCard {
            SlotHeaderRow(title = "BY GENDER")
            Spacer(modifier = Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                diversity.gender.forEach { line ->
                    SlotRow(label = genderLabel(line.key), line = line, icon = genderIcon(line.key))
                }
            }
        }

        WhiteSectionCard {
            SlotHeaderRow(title = "BY AGE GROUP")
            Spacer(modifier = Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                diversity.ageGroup.forEach { line ->
                    SlotRow(label = line.key, line = line, icon = null)
                }
            }
        }
    }
}

@Composable
private fun SlotHeaderRow(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = DashColors.LabelGray, fontSize = 12.sp, letterSpacing = 0.8.sp)
        Text(text = "Slot Available", color = DashColors.LabelGray, fontSize = 12.sp)
    }
}

@Composable
private fun SlotRow(label: String, line: QuotaLine, icon: Int? = null) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (icon != null) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = label,
                        tint = DashColors.TextDark
                    )
                }
                Text(
                    text = label,
                    color = DashColors.TextDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            SlotPill(line)
        }
        if (line.overridesTotal > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${line.overridesTotal} overrides total · ${line.overridesByOperator} by you",
                fontSize = 11.sp,
                color = Colors.Accent.Purple
            )
        }
    }
}

@Composable
private fun SlotPill(line: QuotaLine) {
    val (bg, fg) = when (line.quotaStatus) {
        QuotaStatus.OPEN -> DashColors.GreenPillBg to DashColors.GreenPillText
        QuotaStatus.WARN -> Colors.Warning.Container to Colors.Warning.Main
        QuotaStatus.FULL -> Colors.Failure.Container to Colors.Failure.Main
        QuotaStatus.UNKNOWN -> DashColors.ScreenBg to DashColors.LabelGray
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = if (line.quotaStatus == QuotaStatus.FULL) "Full" else formatCount(line.slotsOpen),
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}