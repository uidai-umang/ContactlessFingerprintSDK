package app.gov.uidai.registration.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ButtonTextStyle = TextStyle(
    fontFamily = NotoSansMedium,
    fontSize = 18.sp
)

val ButtonContentPadding = PaddingValues(16.dp)

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    isLoading: Boolean = false,
    icon: ImageVector? = null,
    iconDescription: String? = null,
    loadingText: String? = null,
    enabled: Boolean = true,
    isOutlined: Boolean = false,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled && isOutlined),
    colors: ButtonColors =
        if (isOutlined) ButtonDefaults.outlinedButtonColors() else ButtonDefaults.buttonColors(),
    shape: Shape = MaterialTheme.shapes.medium,
    elevation: ButtonElevation? = null,
    contentPadding: PaddingValues = ButtonContentPadding,
) {
    val buttonContent: @Composable RowScope.() -> Unit = {
        if (isLoading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = iconModifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            if (loadingText != null) {
                Text(loadingText, style = ButtonTextStyle, modifier = textModifier)
            } else {
                Text(text, style = ButtonTextStyle, modifier = textModifier)
            }
        } else {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = iconDescription,
                    modifier = iconModifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(text = text, style = ButtonTextStyle, modifier = textModifier)
        }
    }

    if(isOutlined){
        OutlinedButton (
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.fillMaxWidth(),
            colors = colors,
            contentPadding = contentPadding,
            border = border,
            shape = shape,
            elevation = elevation,
            content = buttonContent
        )
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.fillMaxWidth(),
            colors = colors,
            contentPadding = contentPadding,
            border = border,
            shape = shape,
            elevation = elevation,
            content = buttonContent
        )
    }
}


@Composable
fun BoxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textModifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconModifier: Modifier = Modifier,
    iconDescription: String? = null,
    isLoading: Boolean = false,
    loadingText: String? = null,
) {
    Button(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        onClick = onClick,
        enabled = enabled
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .defaultMinSize(minHeight = 56.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = iconModifier.size(32.dp),
                    strokeWidth = 3.dp,
                    color = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.scrim.copy(0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = loadingText ?: text,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = textModifier
                )
            } else {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = iconDescription,
                        modifier = iconModifier.size(32.dp),
                        tint = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.scrim.copy(0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = textModifier,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview
@Composable
fun PreviewBoxButton() {
    AttendanceAppTheme {
        BoxButton(
            text = "Test Button",
            onClick = {},
            enabled = false,
            isLoading = true,
            modifier = Modifier.size(120.dp)
        )
    }
}