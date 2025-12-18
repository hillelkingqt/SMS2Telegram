package com.example.telegramforwarder.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// iOS-inspired color schemes with enhanced beauty
private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF0A84FF),
    secondary = androidx.compose.ui.graphics.Color(0xFF5E5CE6),
    tertiary = androidx.compose.ui.graphics.Color(0xFF30D158),
    background = androidx.compose.ui.graphics.Color(0xFF000000),
    surface = androidx.compose.ui.graphics.Color(0xFF1C1C1E),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2C2C2E),
    surfaceTint = androidx.compose.ui.graphics.Color(0xFF0A84FF),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    onBackground = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onSurface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFE5E5EA),
    outline = androidx.compose.ui.graphics.Color(0xFF38383A),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF48484A),
    error = androidx.compose.ui.graphics.Color(0xFFFF453A),
    errorContainer = androidx.compose.ui.graphics.Color(0xFF3A0A0A),
    onError = androidx.compose.ui.graphics.Color.White,
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFFF453A),
)

private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF007AFF),
    secondary = androidx.compose.ui.graphics.Color(0xFF5856D6),
    tertiary = androidx.compose.ui.graphics.Color(0xFF34C759),
    background = androidx.compose.ui.graphics.Color(0xFFF2F2F7),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFF2F2F7),
    surfaceTint = androidx.compose.ui.graphics.Color(0xFF007AFF),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    onBackground = androidx.compose.ui.graphics.Color(0xFF000000),
    onSurface = androidx.compose.ui.graphics.Color(0xFF000000),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF636366),
    outline = androidx.compose.ui.graphics.Color(0xFFC6C6C8),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFE5E5EA),
    error = androidx.compose.ui.graphics.Color(0xFFFF3B30),
    errorContainer = androidx.compose.ui.graphics.Color(0xFFFFEBEE),
    onError = androidx.compose.ui.graphics.Color.White,
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFD32F2F),
)

@Composable
fun TelegramForwarderTheme(
    themeMode: String = "system", // "system", "dark", "light"
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()

            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
