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
import app.gov.uidai.registration.ui.theme.Colors
import app.gov.uidai.registration.ui.theme.Spacing
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
            .padding(Spacing.L)
            .verticalScroll(scrollState)
        ,
        verticalArrangement = Arrangement.spacedBy(Spacing.L)
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
                horizontalArrangement = Arrangement.spacedBy(Spacing.M)
            ) {
                CountTile(
                    label = "Male",
                    count = overview.byGender["MALE"] ?: 0,
                    colors = TileColors(Colors.Info.Container, Colors.Info.Border, Colors.Info.Main),
                    modifier = Modifier.weight(1f)
                )
                CountTile(
                    label = "Female",
                    count = overview.byGender["FEMALE"] ?: 0,
                    colors = TileColors(Colors.Success.Container, Colors.Success.Border, Colors.Success.Main),
                    modifier = Modifier.weight(1f)
                )
                CountTile(
                    label = "Other",
                    count = overview.byGender["OTHER"] ?: 0,
                    colors = TileColors(Colors.Accent.PurpleContainer, Colors.Accent.Purple, Colors.Accent.Purple),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        DashboardCard(title = "By age group") {
            val ageTiles = listOf(
                Triple("5-17", "5-17", TileColors(Colors.Accent.PurpleContainer, Colors.Accent.Purple, Colors.Accent.Purple)),
                Triple("18-40", "18-40", TileColors(Colors.Info.Container, Colors.Info.Border, Colors.Info.Main)),
                Triple("41-60", "41-60", TileColors(Colors.Success.Container, Colors.Success.Border, Colors.Success.Main)),
                Triple("60+", "60+", TileColors(Colors.Warning.Container, Colors.Warning.Border, Colors.Warning.Main))
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                ageTiles.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.M)
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
            .background(Brush.linearGradient(listOf(Colors.Brand.BlueDark, Colors.Brand.Blue)))
            .padding(Spacing.Xl)
    ) {
        Text(
            text = "MY TOTAL CAPTURED",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.5f)
        )
        Spacer(Spacing.Xs)
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
        Spacer(Spacing.L)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                .padding(horizontal = Spacing.L, vertical = Spacing.M)
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
            .border(1.dp, Colors.Border.Primary, RoundedCornerShape(14.dp))
            .padding(Spacing.L)
    ) {
        SectionTitle(title)
        Spacer(Spacing.M)
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
        color = Colors.Text.Tertiary
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
            .padding(vertical = Spacing.L, horizontal = Spacing.S),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$count",
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = colors.text
        )
        Spacer(Spacing.Xxs)
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = colors.text
        )
    }
}
