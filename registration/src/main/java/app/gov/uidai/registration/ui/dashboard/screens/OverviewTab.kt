package app.gov.uidai.registration.ui.dashboard.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import app.gov.uidai.registration.model.dashboard.DashboardOverviewResponse
import app.gov.uidai.registration.ui.dashboard.components.TileColors
import app.gov.uidai.registration.ui.theme.Spacer
import app.gov.uidai.registration.ui.theme.dash_age1_bg
import app.gov.uidai.registration.ui.theme.dash_age1_border
import app.gov.uidai.registration.ui.theme.dash_age1_text
import app.gov.uidai.registration.ui.theme.dash_age2_bg
import app.gov.uidai.registration.ui.theme.dash_age2_border
import app.gov.uidai.registration.ui.theme.dash_age2_text
import app.gov.uidai.registration.ui.theme.dash_age3_bg
import app.gov.uidai.registration.ui.theme.dash_age3_border
import app.gov.uidai.registration.ui.theme.dash_age3_text
import app.gov.uidai.registration.ui.theme.dash_age4_bg
import app.gov.uidai.registration.ui.theme.dash_age4_border
import app.gov.uidai.registration.ui.theme.dash_age4_text
import app.gov.uidai.registration.ui.theme.dash_card_border
import app.gov.uidai.registration.ui.theme.dash_female_bg
import app.gov.uidai.registration.ui.theme.dash_female_border
import app.gov.uidai.registration.ui.theme.dash_female_text
import app.gov.uidai.registration.ui.theme.dash_hero_gradient_end
import app.gov.uidai.registration.ui.theme.dash_hero_gradient_start
import app.gov.uidai.registration.ui.theme.dash_male_bg
import app.gov.uidai.registration.ui.theme.dash_male_border
import app.gov.uidai.registration.ui.theme.dash_male_text
import app.gov.uidai.registration.ui.theme.dash_other_bg
import app.gov.uidai.registration.ui.theme.dash_other_border
import app.gov.uidai.registration.ui.theme.dash_other_text
import app.gov.uidai.registration.ui.theme.dash_sec_title

@Composable
fun OverviewTab(
    overview: DashboardOverviewResponse?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(scrollState)
        ,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isLoading && overview == null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (overview == null) return@Column

        HeroCard(overview)

        DashboardCard(title = "By gender") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CountTile(
                    label = "Male",
                    count = overview.byGender["MALE"] ?: 0,
                    colors = TileColors(dash_male_bg, dash_male_border, dash_male_text),
                    modifier = Modifier.weight(1f)
                )
                CountTile(
                    label = "Female",
                    count = overview.byGender["FEMALE"] ?: 0,
                    colors = TileColors(dash_female_bg, dash_female_border, dash_female_text),
                    modifier = Modifier.weight(1f)
                )
                CountTile(
                    label = "Other",
                    count = overview.byGender["OTHER"] ?: 0,
                    colors = TileColors(dash_other_bg, dash_other_border, dash_other_text),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        DashboardCard(title = "By age group") {
            val ageTiles = listOf(
                Triple("5-17", "5-17", TileColors(dash_age1_bg, dash_age1_border, dash_age1_text)),
                Triple("18-40", "18-40", TileColors(dash_age2_bg, dash_age2_border, dash_age2_text)),
                Triple("41-60", "41-60", TileColors(dash_age3_bg, dash_age3_border, dash_age3_text)),
                Triple("60+", "60+", TileColors(dash_age4_bg, dash_age4_border, dash_age4_text))
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ageTiles.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { (key, label, colors) ->
                            CountTile(
                                label = label,
                                count = overview.byAgeGroup[key] ?: 0,
                                colors = colors,
                                fontSize = 26.sp,
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
private fun HeroCard(overview: DashboardOverviewResponse) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(dash_hero_gradient_start, dash_hero_gradient_end)))
            .padding(20.dp)
    ) {
        Text(
            text = "MY TOTAL CAPTURED",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.5f)
        )
        Spacer(6.dp)
        Text(
            text = "${overview.totalCaptured}",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "residents enrolled",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.55f)
        )
        Spacer(16.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Captured today",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
                Text(
                    text = "${overview.capturedToday}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, dash_card_border, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        SectionTitle(title)
        Spacer(12.dp)
        content()
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = dash_sec_title
    )
}

@Composable
private fun CountTile(
    label: String,
    count: Int,
    colors: TileColors,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 28.sp
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.background)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$count",
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = colors.text
        )
        Spacer(4.dp)
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = colors.text
        )
    }
}
