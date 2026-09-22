package app.gov.uidai.capture.ui.camera.slap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Hand-guide overlay: shown for the first 3s on entering the screen,
// showing 4 finger-shaped guides (varying heights: index/middle/ring/little
// proportions) with gaps between them, so the resident knows to keep their
// fingers spread apart rather than pressed together. Mirrored by hand --
// LEFT lays out little->ring->middle->index left to right (thumb would be
// off-screen to the right); RIGHT is the mirror image (thumb off-screen to
// the left).
private val GuideFingerColor = Color(0xFF3B82F6)
private val GuideFingerFill = Color(0x2A3B82F6)
private val GuideGapHint = Color(0xFF64748B)

@Composable
fun SlapHandGuideOverlay(handType: String, onClick: () -> Unit = {}) {
    val isLeft = handType.equals("Left", ignoreCase = true)
    val relativeLengths = if(isLeft){
        listOf(0.68f, 1.00f, 0.88f, 0.82f)
    }
    else {
        listOf(0.68f, 0.88f, 1.0f, 0.82f)
    }
    val orderedLengths = if (isLeft) relativeLengths else relativeLengths.reversed()
    val tipOnRight = isLeft

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.22f))
            .clickable{onClick()}
        ,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Place all 4 fingers of your $handType hand",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = "Keep a small gap between each finger",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.size(28.dp))

            val maxFingerLength = 360.dp
            val fingerThickness = 92.dp
            val gapWidth = 18.dp
            val alignment = if(isLeft) {
                Alignment.Start
            } else {
                Alignment.End
            }

            Column(
                horizontalAlignment = alignment,
                verticalArrangement = Arrangement.spacedBy(gapWidth)
            ) {
                orderedLengths.forEach { relativeLength ->
                    val fingerLength = maxFingerLength * relativeLength
                    Canvas(
                        modifier = Modifier
                            .width(fingerLength)
                            .height(fingerThickness)
                    ) {
                        val w = size.width
                        val h = size.height
                        val radius = h / 2f
                        // Half-cylinder / capsule shape: rounded top (the
                        // fingertip), flat bottom (where it'd meet the palm,
                        // cropped off by the overlay's bottom edge).
                        val path = Path().apply {
                            if (tipOnRight) {
                                moveTo(0f, 0f)
                                lineTo(w - radius, 0f)
                                arcTo(
                                    rect = androidx.compose.ui.geometry.Rect(w - radius * 2, 0f, w, h),
                                    startAngleDegrees = 270f,
                                    sweepAngleDegrees = 180f,
                                    forceMoveTo = false
                                )
                                lineTo(0f, h)
                                close()
                            } else {
                                moveTo(w, 0f)
                                lineTo(radius, 0f)
                                arcTo(
                                    rect = androidx.compose.ui.geometry.Rect(0f, 0f, radius * 2, h),
                                    startAngleDegrees = 270f,
                                    sweepAngleDegrees = -180f,
                                    forceMoveTo = false
                                )
                                lineTo(w, h)
                                close()
                            }
                        }
                        drawPath(path = path, color = GuideFingerFill)
                        drawPath(
                            path = path,
                            color = GuideFingerColor,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.size(24.dp))
            Text(
                text = "Starting camera…",
                color = GuideGapHint,
                fontSize = 12.sp
            )
        }
    }
}

@Preview
@Composable
fun Preview() {
    SlapHandGuideOverlay("Right")
}