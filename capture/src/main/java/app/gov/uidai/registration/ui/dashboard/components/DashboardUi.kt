package app.gov.uidai.registration.ui.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Transgender
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

internal object DashColors {
    val TopBarNavy = Color(0xFF072441)
    val PrimaryBlue = Color(0xFF045299)
    val HeroBlueStart = Color(0xFF064887)
    val HeroBlueEnd = Color(0xFF0A5BA8)
    val SelectedTabBg = Color(0xFFEDF6FF)
    val ScreenBg = Color(0xFFF4F7FA)
    val LabelGray = Color(0xFF5A626A)
    val TextDark = Color(0xFF1A1D21)
    val GreenPillBg = Color(0xFFE6F9E8)
    val GreenPillText = Color(0xFF1D7A2C)
    val FingerBoxBg = Color(0xFFEAF4FE)
    val TabDivider = Color(0xFFE2E6EA)
}

// 1,49,999 style grouping.
internal fun formatCount(value: Int): String =
    NumberFormat.getInstance(Locale.forLanguageTag("en-IN")).format(value)

internal fun genderIcon(key: String): ImageVector = when (key.uppercase()) {
    "MALE" -> Icons.Default.Male
    "FEMALE" -> Icons.Default.Female
    else -> Icons.Default.Transgender
}

internal fun genderLabel(key: String): String =
    if (key.equals("OTHER", ignoreCase = true)) "OTHERS" else key.uppercase()

@Composable
internal fun WhiteSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
internal fun HeroBannerCard(
    showPhoneGraphic: Boolean,
    leftContent: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(155.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(DashColors.HeroBlueStart, DashColors.HeroBlueEnd)
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ringCenter = Offset(x = size.width * 0.1f, y = size.height * 0.55f)
            for (i in 1..8) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = i * 24f,
                    center = ringCenter,
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )
            }

            val rightCenter = Offset(x = size.width * 0.85f, y = size.height * 0.72f)
            drawCircle(
                color = Color(0xFF9AD3F5).copy(alpha = 0.20f),
                radius = size.height * 0.82f,
                center = rightCenter
            )
            drawCircle(
                color = Color(0xFF9AD3F5).copy(alpha = 0.35f),
                radius = size.height * 0.70f,
                center = rightCenter
            )
            drawCircle(
                color = Color(0xFFB3E1FA),
                radius = size.height * 0.58f,
                center = rightCenter
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            content = leftContent
        )

        if (showPhoneGraphic) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 22.dp)
                    .width(72.dp)
                    .height(92.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(Color(0xFF0A2444))
                    .border(
                        width = 3.dp,
                        color = Color(0xFF041428),
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(54.dp)
                )
            }
        }
    }
}