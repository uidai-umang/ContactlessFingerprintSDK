package app.gov.uidai.registration.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Colors.Brand.Blue, onPrimary = Colors.Text.OnBrand,
    primaryContainer = Colors.Brand.BlueLight, onPrimaryContainer = Colors.Brand.BlueDark,
    secondary = Colors.Accent.Terracotta, onSecondary = Colors.Text.OnBrand,
    tertiary = Colors.Accent.Teal, onTertiary = Colors.Text.OnBrand,
    error = Colors.Failure.Main, onError = Colors.Text.OnBrand,
    errorContainer = Colors.Failure.Container, onErrorContainer = Colors.Failure.Text,
    background = Colors.Background.Primary, onBackground = Colors.Text.Primary,
    surface = Colors.Background.Primary, onSurface = Colors.Text.Primary,
    surfaceVariant = Colors.Background.Secondary, onSurfaceVariant = Colors.Text.Secondary,
    outline = Colors.Border.Secondary, outlineVariant = Colors.Border.Primary,
    surfaceContainerLowest = Colors.Background.Primary, surfaceContainerLow = Colors.Background.Primary,
    surfaceContainer = Colors.Background.Secondary, surfaceContainerHigh = Colors.Background.Secondary,
    surfaceContainerHighest = Colors.Background.Secondary
)

@Composable
fun AttendanceAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {


    val colorScheme = when {
        /*dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme*/
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(
            small = RoundedCornerShape(4.dp),
            medium = RoundedCornerShape(8.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(32.dp)
        ),
        content = content
    )
}