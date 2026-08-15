package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = EmeraldLight,
    onPrimary = Slate950,
    primaryContainer = Slate800,
    onPrimaryContainer = EmeraldMint,
    secondary = BlueCyan,
    onSecondary = Slate950,
    secondaryContainer = Slate850,
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = AmberWarning,
    background = DarkBackground,
    onBackground = Color(0xFFF1F5F9),
    surface = DarkSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Slate300,
    error = RoseDanger,
    errorContainer = Color(0xFF4C0519),
    onError = Color.White,
    onErrorContainer = RoseContainer,
    outline = GeometricBorderDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = SlateNavy,
    onPrimary = Color.White,
    primaryContainer = Slate100,
    onPrimaryContainer = SlateNavy,
    secondary = GeometricAccent,
    onSecondary = Color.White,
    secondaryContainer = GeometricAccentContainer,
    onSecondaryContainer = Color(0xFF115E59),
    tertiary = AmberWarning,
    background = Slate50,
    onBackground = SlateNavy,
    surface = Color.White,
    onSurface = SlateNavy,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    error = RoseDanger,
    errorContainer = RoseContainer,
    onError = Color.White,
    onErrorContainer = Color(0xFF881337),
    outline = GeometricBorder
  )


@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
