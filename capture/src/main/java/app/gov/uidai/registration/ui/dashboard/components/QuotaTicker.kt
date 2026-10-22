package app.gov.uidai.registration.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.gov.uidai.registration.model.dashboard.QuotaAlert
import app.gov.uidai.registration.ui.theme.dash_ticker_bg
import app.gov.uidai.registration.ui.theme.dash_ticker_dot
import app.gov.uidai.registration.ui.theme.dash_ticker_sep
import app.gov.uidai.registration.ui.theme.dash_ticker_text

@Composable
fun QuotaTicker(alerts: List<QuotaAlert>, modifier: Modifier = Modifier) {
    if (alerts.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(dash_ticker_bg)
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.basicMarquee(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            alerts.forEachIndexed { index, alert ->
                TickerItem(alert)
                if (index < alerts.lastIndex) {
                    Text(text = "  |  ", color = dash_ticker_sep, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TickerItem(alert: QuotaAlert) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(dash_ticker_dot)
        )
        Text(
            text = alert.message,
            color = dash_ticker_text,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
    }
}
