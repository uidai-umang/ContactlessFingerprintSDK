package app.gov.uidai.registration.ui.theme

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun AnimatedIconButton(
    isSelected: Boolean,
    icon1: ImageVector,
    icon2: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    icon1Tint: Color = LocalContentColor.current,
    icon2Tint: Color = LocalContentColor.current,
    iconContentDescription: String? = null,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
) {
    var clicked by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (clicked) 0.7f else 1f,
        animationSpec = tween(durationMillis = 150),
        finishedListener = { clicked = false }
    )

    IconButton(
        onClick = {
            clicked = true
            onClick()
        },
        enabled = enabled,
        colors = colors,
        modifier = modifier
    ) {
        Crossfade(targetState = isSelected) { selected ->
            if (selected) {
                Icon(
                    imageVector = icon1,
                    contentDescription = iconContentDescription,
                    tint = icon1Tint,
                    modifier = iconModifier.scale(scale)
                )
            } else {
                Icon(
                    imageVector = icon2,
                    contentDescription = iconContentDescription,
                    tint = icon2Tint,
                    modifier = iconModifier.scale(scale)
                )
            }
        }
    }
}