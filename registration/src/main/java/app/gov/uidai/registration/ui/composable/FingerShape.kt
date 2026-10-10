package app.gov.uidai.registration.ui.composable

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

class FingerShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            // Normalized values are based on fractions of the total size.
            // The original code used a rectangle with semicircles on top and bottom.
            // RECT_HEIGHT_F and SEMICIRCLE_RADIUS_F define the proportions.
            // Here, we define them relative to the total height of the shape.
            val radiusRatio = 0.5f

            val centerX = size.width / 2f
            val centerY = size.height / 2f

            val radiusPx = size.width * radiusRatio
            val rectHalfHeightPx = (size.height - 2 * radiusPx) / 2

            val topArcRect = Rect(
                left = centerX - radiusPx,
                top = centerY - rectHalfHeightPx - radiusPx,
                right = centerX + radiusPx,
                bottom = centerY - rectHalfHeightPx + radiusPx
            )
            val bottomArcRect = Rect(
                left = centerX - radiusPx,
                top = centerY + rectHalfHeightPx - radiusPx,
                right = centerX + radiusPx,
                bottom = centerY + rectHalfHeightPx + radiusPx
            )

            // Start at the bottom-left of the rectangle part
            moveTo(centerX - radiusPx, centerY + rectHalfHeightPx)

            // Draw the left vertical line
            lineTo(centerX - radiusPx, centerY - rectHalfHeightPx)

            // Draw the top semicircle (arc)
            arcTo(
                rect = topArcRect,
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )

            // Draw the right vertical line
            lineTo(centerX + radiusPx, centerY + rectHalfHeightPx)

            // Draw the bottom semicircle (arc)
            arcTo(
                rect = bottomArcRect,
                startAngleDegrees = 0f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )

            // Close the path to complete the shape
            close()
        }
        return Outline.Generic(path)
    }
}