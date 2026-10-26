package app.gov.uidai.registration.ui.theme

import androidx.compose.ui.graphics.Color

object Colors {

    object Background {
        val Primary = Color(0xFFFFFFFF)    // screens, dialogs, bottom bars
        val Secondary = Color(0xFFF5F5F4)  // cards, rows, chips, tab strips, dashboard bg
        val Tertiary = Color(0xFF1A1A1A)   // dark surfaces (camera)
        val Transparent = Color.Transparent
    }

    object Text {
        val Primary = Color(0xFF1A1A1A)
        val Secondary = Color(0xFF4A4540)
        val Tertiary = Color(0xFF8A7F78)   // muted, hints, inactive tabs
        val OnBrand = Color(0xFFFFFFFF)
        val OnDark = Color(0xFFFFFFFF)
    }

    object Border {
        val Primary = Color(0xFFD8D0C8)
        val Secondary = Color(0xFFB8AFA8)
    }

    object Brand {
        val Blue = Color(0xFF1A56A0)
        val BlueDark = Color(0xFF0F3460)
        val Navy = Color(0xFF0B1F3A)
        val BlueLight = Color(0xFFEBF3FD)
        val BlueSoft = Color(0xFFD6E8F8)
        val BlueBorder = Color(0xFF93C5FD)
    }

    object Button {
        val Primary = Brand.Blue
        val OnPrimary = Color(0xFFFFFFFF)
        val Disabled = Color(0xFFD8D0C8)
        val OnDisabled = Color(0xFF8A7F78)
    }

    object Accent {
        val Terracotta = Color(0xFF7C3D1E)
        val Teal = Color(0xFF00BFA5)
        val Purple = Color(0xFF534AB7)
        val PurpleContainer = Color(0xFFEEEDFE)
    }

    object Success {
        val Main = Color(0xFF16A34A)
        val Container = Color(0xFFDCFCE7)
        val Border = Color(0xFF86EFAC)
        val Text = Color(0xFF166534)
    }

    object Warning {
        val Main = Color(0xFFD97706)
        val Container = Color(0xFFFEF3C7)
        val Border = Color(0xFFFCD34D)
        val Text = Color(0xFF92400E)
    }

    object Failure {
        val Main = Color(0xFFDC2626)
        val Container = Color(0xFFFEE2E2)
        val Border = Color(0xFFFECACA)
        val Text = Color(0xFF991B1B)
    }

    object Info {
        val Main = Brand.Blue
        val Container = Brand.BlueLight
        val Border = Brand.BlueBorder
        val Text = Color(0xFF083459)
    }
}
