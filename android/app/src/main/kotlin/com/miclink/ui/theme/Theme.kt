package com.miclink.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 清新简约的配色方案
object MicLinkColors {
    // 主色 - 清新蓝绿色
    val Primary = Color(0xFF00BFA5)
    val PrimaryLight = Color(0xFF5DF2D6)
    val PrimaryDark = Color(0xFF008E76)
    
    // 次要色 - 柔和蓝色
    val Secondary = Color(0xFF64B5F6)
    val SecondaryLight = Color(0xFF9BE7FF)
    val SecondaryDark = Color(0xFF2286C3)
    
    // 背景色 - 纯净白和淡灰
    val Background = Color(0xFFFAFAFA)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFF5F5F5)
    
    // 文字色 - 深灰而非纯黑
    val OnBackground = Color(0xFF2D3436)
    val OnSurface = Color(0xFF2D3436)
    val OnSurfaceVariant = Color(0xFF636E72)
    
    // 功能色
    val Error = Color(0xFFE74C3C)
    val Success = Color(0xFF27AE60)
    val Warning = Color(0xFFF39C12)
    
    // 暗色主题
    val DarkBackground = Color(0xFF1A1A2E)
    val DarkSurface = Color(0xFF16213E)
    val DarkSurfaceVariant = Color(0xFF0F3460)
    val DarkOnBackground = Color(0xFFE8E8E8)
    val DarkOnSurface = Color(0xFFE8E8E8)
    val DarkOnSurfaceVariant = Color(0xFFB0B0B0)
}

private val LightColorScheme = lightColorScheme(
    primary = MicLinkColors.Primary,
    onPrimary = Color.White,
    primaryContainer = MicLinkColors.PrimaryLight.copy(alpha = 0.3f),
    onPrimaryContainer = MicLinkColors.PrimaryDark,
    secondary = MicLinkColors.Secondary,
    onSecondary = Color.White,
    secondaryContainer = MicLinkColors.SecondaryLight.copy(alpha = 0.3f),
    onSecondaryContainer = MicLinkColors.SecondaryDark,
    background = MicLinkColors.Background,
    onBackground = MicLinkColors.OnBackground,
    surface = MicLinkColors.Surface,
    onSurface = MicLinkColors.OnSurface,
    surfaceVariant = MicLinkColors.SurfaceVariant,
    onSurfaceVariant = MicLinkColors.OnSurfaceVariant,
    error = MicLinkColors.Error,
    onError = Color.White,
    errorContainer = MicLinkColors.Error.copy(alpha = 0.1f),
    onErrorContainer = MicLinkColors.Error,
    outline = Color(0xFFDFE6E9),
    outlineVariant = Color(0xFFECF0F1)
)

private val DarkColorScheme = darkColorScheme(
    primary = MicLinkColors.PrimaryLight,
    onPrimary = MicLinkColors.DarkBackground,
    primaryContainer = MicLinkColors.Primary.copy(alpha = 0.3f),
    onPrimaryContainer = MicLinkColors.PrimaryLight,
    secondary = MicLinkColors.SecondaryLight,
    onSecondary = MicLinkColors.DarkBackground,
    secondaryContainer = MicLinkColors.Secondary.copy(alpha = 0.3f),
    onSecondaryContainer = MicLinkColors.SecondaryLight,
    background = MicLinkColors.DarkBackground,
    onBackground = MicLinkColors.DarkOnBackground,
    surface = MicLinkColors.DarkSurface,
    onSurface = MicLinkColors.DarkOnSurface,
    surfaceVariant = MicLinkColors.DarkSurfaceVariant,
    onSurfaceVariant = MicLinkColors.DarkOnSurfaceVariant,
    error = Color(0xFFFF6B6B),
    onError = Color.White,
    errorContainer = Color(0xFFFF6B6B).copy(alpha = 0.2f),
    onErrorContainer = Color(0xFFFF6B6B),
    outline = Color(0xFF3D5A80),
    outlineVariant = Color(0xFF293D55)
)

private val MicLinkTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
)

@Composable
fun MicLinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MicLinkTypography,
        content = content
    )
}
