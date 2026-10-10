package app.gov.uidai.registration.ui.dashboard.components

import androidx.compose.ui.graphics.Color
import app.gov.uidai.registration.model.dashboard.QuotaStatus
import app.gov.uidai.registration.ui.theme.dash_age1_text
import app.gov.uidai.registration.ui.theme.dash_age2_text
import app.gov.uidai.registration.ui.theme.dash_age3_text
import app.gov.uidai.registration.ui.theme.dash_age4_text
import app.gov.uidai.registration.ui.theme.dash_band_amber_bg
import app.gov.uidai.registration.ui.theme.dash_band_amber_border
import app.gov.uidai.registration.ui.theme.dash_band_amber_text
import app.gov.uidai.registration.ui.theme.dash_band_blue_bg
import app.gov.uidai.registration.ui.theme.dash_band_blue_border
import app.gov.uidai.registration.ui.theme.dash_band_blue_text
import app.gov.uidai.registration.ui.theme.dash_band_green_bg
import app.gov.uidai.registration.ui.theme.dash_band_green_border
import app.gov.uidai.registration.ui.theme.dash_band_green_text
import app.gov.uidai.registration.ui.theme.dash_band_red_bg
import app.gov.uidai.registration.ui.theme.dash_band_red_border
import app.gov.uidai.registration.ui.theme.dash_band_red_text
import app.gov.uidai.registration.ui.theme.dash_female_text
import app.gov.uidai.registration.ui.theme.dash_male_text
import app.gov.uidai.registration.ui.theme.dash_other_text
import app.gov.uidai.registration.ui.theme.dash_status_full
import app.gov.uidai.registration.ui.theme.dash_status_open
import app.gov.uidai.registration.ui.theme.dash_status_warn
import app.gov.uidai.registration.ui.theme.dash_text_muted

fun quotaStatusColor(status: QuotaStatus): Color = when (status) {
    QuotaStatus.FULL -> dash_status_full
    QuotaStatus.WARN -> dash_status_warn
    QuotaStatus.OPEN -> dash_status_open
    QuotaStatus.UNKNOWN -> dash_text_muted
}

data class TileColors(val background: Color, val border: Color, val text: Color)

data class FingerCountBand(val background: Color, val border: Color, val text: Color)

fun fingerCountBandFor(residentFingerCount: Int): FingerCountBand = when {
    residentFingerCount <= 3 -> FingerCountBand(dash_band_red_bg, dash_band_red_border, dash_band_red_text)
    residentFingerCount == 4 -> FingerCountBand(dash_band_amber_bg, dash_band_amber_border, dash_band_amber_text)
    residentFingerCount in 5..7 -> FingerCountBand(dash_band_green_bg, dash_band_green_border, dash_band_green_text)
    else -> FingerCountBand(dash_band_blue_bg, dash_band_blue_border, dash_band_blue_text)
}

// Diversity-tab row dots are decorative/category markers, not status indicators —
// gender dots reuse the per-tile hues from Overview, age dots follow the 4-band order.
fun genderDotColor(key: String): Color = when (key.uppercase()) {
    "MALE" -> dash_male_text
    "FEMALE" -> dash_female_text
    "OTHER" -> dash_other_text
    else -> dash_text_muted
}

fun ageGroupDotColor(index: Int): Color =
    listOf(dash_age1_text, dash_age2_text, dash_age3_text, dash_age4_text).getOrElse(index) { dash_text_muted }
