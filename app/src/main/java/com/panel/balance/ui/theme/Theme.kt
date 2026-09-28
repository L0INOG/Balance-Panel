package com.panel.balance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF5A55D6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E2FF),
    onPrimaryContainer = Color(0xFF191553),
    secondary = Color(0xFF00696D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCBF0F2),
    onSecondaryContainer = Color(0xFF002022),
    tertiary = Color(0xFF9A4600),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBC8),
    onTertiaryContainer = Color(0xFF331200),
    background = Color(0xFFF6F5F1),
    onBackground = Color(0xFF1A1B1E),
    surface = Color(0xFFFDFDFB),
    onSurface = Color(0xFF1A1B1E),
    surfaceVariant = Color(0xFFE9E8F0),
    onSurfaceVariant = Color(0xFF47464F),
    outline = Color(0xFF7A7580),
    outlineVariant = Color(0xFFDDDCE5),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color.White,
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC0BCFF),
    onPrimary = Color(0xFF271C77),
    primaryContainer = Color(0xFF3F3AA8),
    onPrimaryContainer = Color(0xFFE5E1FF),
    secondary = Color(0xFF84D3D6),
    onSecondary = Color(0xFF003739),
    secondaryContainer = Color(0xFF004F53),
    onSecondaryContainer = Color(0xFFCBF0F2),
    tertiary = Color(0xFFFFB68C),
    onTertiary = Color(0xFF512300),
    tertiaryContainer = Color(0xFF6A3100),
    onTertiaryContainer = Color(0xFFFFDBC8),
    background = Color(0xFF101014),
    onBackground = Color(0xFFE5E1E6),
    surface = Color(0xFF16161B),
    onSurface = Color(0xFFE5E1E6),
    surfaceVariant = Color(0xFF23232C),
    onSurfaceVariant = Color(0xFFC8C5D0),
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF46424C),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
)

val PanelShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** 应用主题：Material 3 明暗配色 + 统一圆角形状。 */
@Composable
fun PanelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = PanelShapes,
        content = content,
    )
}
