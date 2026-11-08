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

// Katib's shared royal-navy and muted-gold design tokens.
// Keep Material components aligned with AppThemeData instead of Android
// wallpaper-derived colors so dialogs, fields, and system surfaces stay coherent.
private val DarkColorScheme = darkColorScheme(
  primary = Color(0xFFD2B16B),
  onPrimary = Color(0xFF142744),
  secondary = Color(0xFFB9934C),
  onSecondary = Color(0xFF142744),
  tertiary = Color(0xFFE2C98F),
  background = Color(0xFF101827),
  onBackground = Color(0xFFF2F4F8),
  surface = Color(0xFF182438),
  onSurface = Color(0xFFF2F4F8),
  surfaceVariant = Color(0xFF263449),
  onSurfaceVariant = Color(0xFFCDD5E1),
  outline = Color(0xFF43516A)
)

private val LightColorScheme = lightColorScheme(
  primary = Color(0xFF142744),
  onPrimary = Color.White,
  secondary = Color(0xFFB9934C),
  onSecondary = Color(0xFF142744),
  tertiary = Color(0xFF8B6B35),
  background = Color(0xFFF6F7FA),
  onBackground = Color(0xFF18263B),
  surface = Color.White,
  onSurface = Color(0xFF18263B),
  surfaceVariant = Color(0xFFF0F3F8),
  onSurfaceVariant = Color(0xFF536176),
  outline = Color(0xFFD5DCE6)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color remains an explicit opt-in; the Katib brand palette is the default.
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
