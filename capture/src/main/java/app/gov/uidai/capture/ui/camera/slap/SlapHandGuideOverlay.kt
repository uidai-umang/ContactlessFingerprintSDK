package app.gov.uidai.capture.ui.camera.slap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gov.uidai.capture.R
import app.gov.uidai.registration.ui.theme.Spacing
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlinx.coroutines.flow.first

// --- Hand-guide overlay: shown when the screen opens. The Lottie
// (res/raw/finger_guide.json) slides 4 finger bars in with gaps between
// them, so the resident knows to keep their fingers spread apart rather than
// pressed together. It is drawn for the LEFT hand (tips on the right); the
// RIGHT hand is the mirror image.
//
// The animation is 1s long; at 0.5x speed it plays once for 2s, then the
// overlay dismisses itself. Tapping dismisses it early.
private val GuideGapHint = Color(0xFF64748B)
private const val GuideSpeed = 0.5f

@Composable
fun SlapHandGuideOverlay(handType: String, onClick: () -> Unit = {}) {
    val isLeft = handType.equals("Left", ignoreCase = true)
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.finger_guide))
    val animationState = animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        speed = GuideSpeed
    )

    LaunchedEffect(composition) {
        if (composition == null) return@LaunchedEffect
        snapshotFlow { animationState.progress }.first { it >= 1f }
        onClick()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.L),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Place all 4 fingers of your $handType hand",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.size(Spacing.Xs))
            Text(
                text = "Keep a small gap between each finger",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp
            )

            // Takes every pixel left between the text above and the hint below.
            // Fit keeps the animation's own proportions (369 x 582), so it is never stretched.
            LottieAnimation(
                composition = composition,
                progress = { animationState.progress },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 28.dp)
                    .graphicsLayer(scaleX = if (isLeft) 1f else -1f)
            )

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