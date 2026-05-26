package com.example.diceroller.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val CasinoColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = CasinoBlack,
    primaryContainer = GoldDark,
    onPrimaryContainer = CasinoBlack,
    secondary = FeltGreen,
    onSecondary = GoldLight,
    secondaryContainer = FeltGreenDark,
    onSecondaryContainer = GoldLight,
    tertiary = DeepRed,
    onTertiary = GoldLight,
    background = CasinoBlack,
    onBackground = GoldLight,
    surface = CasinoBlackLight,
    onSurface = GoldLight,
    error = DeepRedLight,
    onError = CasinoBlack
)

@Composable
fun DiceRollerTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = CasinoBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = CasinoColorScheme,
        typography = Typography,
        content = content
    )
}
