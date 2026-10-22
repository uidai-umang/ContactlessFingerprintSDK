package app.gov.uidai.registration.ui.dashboard.components

import androidx.compose.ui.graphics.Color
import app.gov.uidai.registration.model.dashboard.QuotaStatus
import app.gov.uidai.registration.ui.theme.Colors
fun quotaStatusColor(status: QuotaStatus): Color = when (status) {
    QuotaStatus.FULL -> Colors.Failure.Main
    QuotaStatus.WARN -> Colors.Warning.Main
    QuotaStatus.OPEN -> Colors.Success.Main
    QuotaStatus.UNKNOWN -> Colors.Text.Tertiary
}

data class TileColors(val background: Color, val border: Color, val text: Color)

data class FingerCountBand(val background: Color, val border: Color, val text: Color)

fun fingerCountBandFor(residentFingerCount: Int): FingerCountBand = when {
    residentFingerCount <= 3 -> FingerCountBand(Colors.Failure.Container, Colors.Failure.Border, Colors.Failure.Main)
    residentFingerCount == 4 -> FingerCountBand(Colors.Warning.Container, Colors.Warning.Border, Colors.Warning.Main)
    residentFingerCount in 5..7 -> FingerCountBand(Colors.Success.Container, Colors.Success.Border, Colors.Success.Main)
    else -> FingerCountBand(Colors.Info.Container, Colors.Info.Border, Colors.Info.Main)
}

// Diversity-tab row dots are decorative/category markers, not status indicators —
// gender dots reuse the per-tile hues from Overview, age dots follow the 4-band order.
fun genderDotColor(key: String): Color = when (key.uppercase()) {
    "MALE" -> Colors.Info.Main
    "FEMALE" -> Colors.Success.Main
    "OTHER" -> Colors.Accent.Purple
    else -> Colors.Text.Tertiary
}

fun ageGroupDotColor(index: Int): Color =
    listOf(Colors.Accent.Purple, Colors.Info.Main, Colors.Success.Main, Colors.Warning.Main).getOrElse(index) { Colors.Text.Tertiary }
