package app.gov.uidai.registration.ui.dashboard.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.gov.uidai.registration.model.dashboard.QuotaStatus
import app.gov.uidai.registration.ui.theme.pendingContainer
import app.gov.uidai.registration.ui.theme.successContainer

// The palette in ui/theme/Color.kt has no amber or purple tokens, so WARN and the
// overrides note reuse the nearest existing containers (tertiary, secondaryContainer)
// instead of introducing new hardcoded colors.
@Composable
fun quotaStatusColor(status: QuotaStatus): Color = when (status) {
    QuotaStatus.FULL -> MaterialTheme.colorScheme.error
    QuotaStatus.WARN -> MaterialTheme.colorScheme.tertiary
    QuotaStatus.OPEN -> successContainer
    QuotaStatus.UNKNOWN -> MaterialTheme.colorScheme.outline
}

@Composable
fun fingerCountBandColor(residentCount: Int): Color = when {
    residentCount <= 3 -> MaterialTheme.colorScheme.errorContainer
    residentCount == 4 -> MaterialTheme.colorScheme.secondaryContainer
    residentCount in 5..7 -> successContainer
    else -> pendingContainer
}
