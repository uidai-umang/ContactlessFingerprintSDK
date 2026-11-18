package app.gov.uidai.registration.ui.dashboard.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gov.uidai.registration.model.dashboard.DashboardFingersResponse
import app.gov.uidai.registration.ui.dashboard.components.DashColors
import app.gov.uidai.registration.ui.dashboard.components.HeroBannerCard
import app.gov.uidai.registration.ui.dashboard.components.WhiteSectionCard

private const val GRID_COLUMNS = 5

@Composable
fun FingersTab(
    fingers: DashboardFingersResponse?,
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
        if (isLoading && fingers == null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (fingers == null) return@Column

        HeroBannerCard(showPhoneGraphic = false) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = fingers.totalFingers.toString(),
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Total Fingers Synced",
                    color = Color.White,
                    fontSize = 15.sp
                )
                Text(
                    text = "Till now",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp
                )
            }
        }

        WhiteSectionCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "FINGERCOUNT",
                    color = DashColors.LabelGray,
                    fontSize = 12.sp,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "(in Numbers)",
                    color = DashColors.LabelGray,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(14.dp))

            val counts = (1..10).map { fingers.residentsByFingerCount["$it"] ?: 0 }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                counts.chunked(GRID_COLUMNS).forEachIndexed { rowIndex, rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEachIndexed { colIndex, count ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DashColors.FingerBoxBg)
                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = (rowIndex * GRID_COLUMNS + colIndex + 1).toString(),
                                    color = DashColors.LabelGray,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = count.toString(),
                                    color = DashColors.PrimaryBlue,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}