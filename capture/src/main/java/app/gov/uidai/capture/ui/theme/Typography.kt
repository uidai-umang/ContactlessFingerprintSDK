package app.gov.uidai.capture.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.gov.uidai.capture.R

private val Roboto = FontFamily(Font(R.font.roboto_regular))

object Typography {
    // ── heading ──
    val heading1 = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontFamily = Roboto, fontWeight = FontWeight(700))
    val heading2 = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontFamily = Roboto, fontWeight = FontWeight(700)) // CONFIRMED
    val heading3 = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontFamily = Roboto, fontWeight = FontWeight(700))

    // ── body ──
    val body1 = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontFamily = Roboto, fontWeight = FontWeight(400))
    val body2 = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, fontFamily = Roboto, fontWeight = FontWeight(400))
    val body3 = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontFamily = Roboto, fontWeight = FontWeight(400)) // CONFIRMED

    // ── labels ──
    val labels1 = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontFamily = Roboto, fontWeight = FontWeight(600))
    val labels2 = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontFamily = Roboto, fontWeight = FontWeight(700))
    val labels3 = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontFamily = Roboto, fontWeight = FontWeight(700))
    val labels4 = TextStyle(fontSize = 10.sp, lineHeight = 18.sp, fontFamily = Roboto, fontWeight = FontWeight(700)) // CONFIRMED
}