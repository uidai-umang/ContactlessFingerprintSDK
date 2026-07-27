package app.gov.uidai.capture.ui.camera

import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.gov.uidai.capture.ui.camera.model.CaptureState
import kotlinx.coroutines.launch
import kotlin.math.min

enum class OverlayVisualState(val color: Color, val hasThinBase: Boolean) {
    INITIAL(Color(0xFFFFFFFF), false),
    WARNING(Color(0xFFDC2626), false),
    PRE_AUTO_CAPTURE(Color(0xFFEAB308), true),
    AUTO_CAPTURE_SUCCESS(Color(0xFF16A34A), true),
    SUCCESS(Color(0xFF16A34A), false),
    FAILURE(Color(0xFFDC2626), false)
}

fun CaptureState.toOverlayVisualState(): OverlayVisualState = when (this) {
    is CaptureState.Initial -> OverlayVisualState.INITIAL
    is CaptureState.Warn -> OverlayVisualState.WARNING
    is CaptureState.AutoCaptureTrigger -> OverlayVisualState.PRE_AUTO_CAPTURE
    is CaptureState.AutoCaptureSuccess -> OverlayVisualState.AUTO_CAPTURE_SUCCESS
    is CaptureState.Success -> OverlayVisualState.SUCCESS
    is CaptureState.Failed -> OverlayVisualState.FAILURE
}

private val OVAL_WIDTH = 180.dp
private val OVAL_HEIGHT = 240.dp
private val THICK_STROKE = 8.dp
private val THIN_STROKE = 2.dp
private const val DASH_LENGTH = 150f
private const val DASH_GAP = 60f

@Composable
fun CaptureOverlay(
    state: OverlayVisualState,
    progressAnimationDurationMs: Long,
    cutoutBoundsHolder: CutoutBoundsHolder,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val color = remember {
        Animatable(state.color)
    }
    val strokeWidth = remember {
        Animatable(with(density) { THICK_STROKE.toPx() })
    }
    val dashGap = remember {
        Animatable(DASH_GAP)
    }
    val lineAlpha = remember {
        Animatable(0f)
    }
    val progress = remember {
        Animatable(0f)
    }
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")

    val spinnerStart by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = 1333,
                easing = FastOutSlowInEasing
            )
        ),
        label = "spinnerStart"
    )

    val spinnerEnd by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = 1333,
                easing = FastOutSlowInEasing
            ), initialStartOffset = StartOffset(666)
        ),
        label = "spinnerEnd"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1333, easing = LinearEasing)),
        label = "spinnerRotation"
    )

    LaunchedEffect(state) {
        launch {
            color.animateTo(
                targetValue = state.color,
                animationSpec = tween(durationMillis = 300)
            )
        }
        launch {
            strokeWidth.animateTo(
                targetValue = with(density) {
                    (if (state.hasThinBase) THIN_STROKE else THICK_STROKE).toPx()
                },
                animationSpec = tween(durationMillis = 300)
            )
        }
        launch {
            dashGap.animateTo(
                targetValue = if (state == OverlayVisualState.INITIAL) DASH_GAP else 0f,
                animationSpec = tween(durationMillis = 300)
            )
        }
        launch {
            lineAlpha.animateTo(
                targetValue = if (state.hasThinBase) 1f else 0f,
                animationSpec = tween(durationMillis = 300)
            )
        }
        if (state == OverlayVisualState.PRE_AUTO_CAPTURE) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = progressAnimationDurationMs.toInt(),
                    easing = LinearEasing
                )
            )
        }
    }

    Box(
        modifier = modifier.size(OVAL_WIDTH, OVAL_HEIGHT)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInParent()
                cutoutBoundsHolder.rect = RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
            }
    ) {
        Canvas(
            modifier = Modifier.size(OVAL_WIDTH, OVAL_HEIGHT)
        ) {
            val radius = min(size.width, size.height) / 2f
            val path = Path().apply {
                val rectHalfHeight = (size.height - size.width) / 2f
                val cx = size.width / 2f
                val topArc = RectF(0f, 0f, size.width, size.height)
                val bottomArc = RectF(0f, size.height - size.width, size.width, size.height)
                moveTo(0f, size.height - size.width)
                lineTo(0f, rectHalfHeight)
                arcTo(topArc, 180f, 180f)
                lineTo(size.width, size.height - rectHalfHeight)
                arcTo(bottomArc, 0f, 180f)
                close()
            }
            val composePath = path.asComposePath()

            // Base line — solid or dashed depending on animated dashGap
            val effect = if(dashGap.value > 0.1f) {
                PathEffect.dashPathEffect(floatArrayOf(DASH_LENGTH, dashGap.value))
            } else null

            drawPath(
                path = composePath,
                color = color.value,
                style = Stroke(width = strokeWidth.value, pathEffect = effect)
            )

            if(lineAlpha.value > 0f) {
                val measure = PathMeasure(path, false)
                val length = measure.length
                if(length > 0f) {
                    val segment = Path()
                    when(state) {
                        OverlayVisualState.PRE_AUTO_CAPTURE -> {
                            measure.getSegment(0f, length*progress.value, segment, true)
                        }

                        OverlayVisualState.AUTO_CAPTURE_SUCCESS -> {
                            val startT = (spinnerStart + rotation) % 1f
                            val endT = (spinnerEnd + rotation) % 1f
                            val startD = length * startT
                            val stopD = length * endT
                            if(endT < startT) {
                                measure.getSegment(startD, length, segment, true)
                                val overflow = Path()
                                measure.getSegment(0f, stopD, overflow, true)
                                segment.addPath(overflow)
                            } else {
                                measure.getSegment(startD, stopD, segment, true)
                            }
                        }

                        else -> {}
                    }

                    drawPath(
                        path = segment.asComposePath(),
                        color = color.value.copy(alpha = lineAlpha.value),

                    )
                }
            }
        }
    }
}