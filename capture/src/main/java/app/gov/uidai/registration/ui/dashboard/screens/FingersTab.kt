package app.gov.uidai.registration.ui.dashboard.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gov.uidai.registration.model.dashboard.DashboardFingersResponse
import app.gov.uidai.registration.ui.dashboard.components.fingerCountBandFor
import app.gov.uidai.registration.ui.theme.Spacer
import app.gov.uidai.registration.ui.theme.Colors
import app.gov.uidai.registration.ui.theme.Spacing
private const val GRID_COLUMNS = 5

@Composable
fun FingersTab(
    fingers: DashboardFingersResponse?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.L)
    ) {
        if (isLoading && fingers == null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (fingers == null) return@Column

        HeroCard(fingers)

        DashboardCard(title = "Residents by finger count") {
            Text(
                text = "Number of residents who shared that many fingers during collection.",
                fontSize = 11.sp,
                color = Colors.Text.Tertiary
            )
            Spacer(Spacing.M)
            val bandCounts = (1..10).map { it to (fingers.residentsByFingerCount["$it"] ?: 0) }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                bandCounts.chunked(GRID_COLUMNS).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.S)
                    ) {
                        row.forEach { (fingerCount, residentCount) ->
                            FingerCountTile(
                                fingerCount = fingerCount,
                                residentCount = residentCount,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(fingers: DashboardFingersResponse) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Colors.Brand.Navy, Colors.Brand.BlueDark)))
            .padding(Spacing.Xl)
    ) {
        Text(
            text = "TOTAL FINGERS SYNCED BY ME",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.5f)
        )
        Spacer(Spacing.Xs)
        Text(
            text = "${fingers.totalFingers}",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "across ${fingers.residentCount} residents",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun FingerCountTile(fingerCount: Int, residentCount: Int, modifier: Modifier = Modifier) {
    val band = fingerCountBandFor(fingerCount)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(band.background)
            .border(1.dp, band.border, RoundedCornerShape(10.dp))
            .padding(vertical = Spacing.S, horizontal = Spacing.Xxs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$fingerCount",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = band.text
        )
        Spacer(Spacing.Xxxs)
        Text(
            text = "$residentCount",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = band.text
        )
    }
}
