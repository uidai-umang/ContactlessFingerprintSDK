package app.gov.uidai.registration.ui.dashboard.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Transgender
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gov.uidai.registration.model.dashboard.DashboardOverviewResponse
import app.gov.uidai.registration.ui.dashboard.components.DashColors
import app.gov.uidai.registration.ui.dashboard.components.HeroBannerCard
import app.gov.uidai.registration.ui.dashboard.components.WhiteSectionCard
import app.gov.uidai.registration.ui.theme.Colors

private val AGE_GROUPS = listOf(
    "5-17" to Color(0xFFEDF7FD),
    "18-40" to Color(0xFFD3E7FC),
    "41-60" to Color(0xFFBDD9FB),
    "60+" to Color(0xFFEBF0FC)
)

@Composable
fun OverviewTab(
    overview: DashboardOverviewResponse?,
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
        if (isLoading && overview == null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (overview == null) return@Column

        HeroBannerCard(showPhoneGraphic = true) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = overview.capturedToday.toString(),
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "TODAY",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF043A70).copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOTAL ENROLLED",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp
                        )
                        Text(
                            text = overview.totalCaptured.toString(),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        WhiteSectionCard {
            Text(
                text = "BY GENDER",
                color = DashColors.LabelGray,
                fontSize = 12.sp,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GenderStatItem(Icons.Default.Male, "MALE", overview.byGender["MALE"] ?: 0)
                GenderStatItem(Icons.Default.Female, "FEMALE", overview.byGender["FEMALE"] ?: 0)
                GenderStatItem(Icons.Default.Transgender, "OTHERS", overview.byGender["OTHER"] ?: 0)
            }
        }

        WhiteSectionCard {
            Text(
                text = "BY AGE GROUP",
                color = DashColors.LabelGray,
                fontSize = 12.sp,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AGE_GROUPS.forEach { (key, bgColor) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = key,
                            color = DashColors.TextDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = (overview.byAgeGroup[key] ?: 0).toString(),
                            color = DashColors.TextDark,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenderStatItem(icon: ImageVector, label: String, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = DashColors.TextDark,
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(
                text = label,
                color = DashColors.LabelGray,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = count.toString(),
                color = DashColors.TextDark,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Also used by QuotaSoftPromptModal.
@Composable
fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = Colors.Text.Tertiary
    )
}