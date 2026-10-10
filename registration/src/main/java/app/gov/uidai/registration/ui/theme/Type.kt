package app.gov.uidai.registration.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.gov.uidai.registration.R

// Set of Material typography styles to start with
// Load fonts from res/font
val NotoSansBold = FontFamily(Font(R.font.noto_sans_bold))
val NotoSansMedium = FontFamily(Font(R.font.noto_sans_medium))
val NotoSansRegular = FontFamily(Font(R.font.noto_sans_regular))
val ManropeMedium = FontFamily(Font(R.font.manrope_medium))
val ManropeLight = FontFamily(Font(R.font.manrope_light))

val Typography = Typography(
    headlineMedium = TextStyle(
        fontFamily = NotoSansRegular,
        fontSize = 28.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = NotoSansRegular,
        fontSize = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = NotoSansRegular,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = NotoSansRegular,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        color = md_theme_secondary
    ),
    bodyLarge = TextStyle(
        fontFamily = ManropeMedium,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ManropeMedium,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    bodySmall = TextStyle(
        fontFamily = ManropeMedium,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    ),
    labelLarge = TextStyle(
        fontFamily = ManropeLight,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    labelMedium = TextStyle(
        fontFamily = ManropeLight,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = ManropeLight,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp
    )
)

// Extra style for BodyXL (20sp) — not in default Material3 set
val BodyXL = TextStyle(
    fontFamily = ManropeMedium,
    fontWeight = FontWeight.Normal,
    fontSize = 20.sp
)