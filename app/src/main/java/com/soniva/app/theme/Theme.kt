package com.soniva.app.theme
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.soniva.app.model.ThemeMode
private val DarkColors = darkColorScheme(primary = Color(0xFFE2C56A), onPrimary = Color(0xFF2A2100), background = Color(0xFF121014), onBackground = Color(0xFFF4EFE6), surface = Color(0xFF1A171C), onSurface = Color(0xFFF4EFE6), surfaceVariant = Color(0xFF2C2830), onSurfaceVariant = Color(0xFFD0C7B8))
private val LightColors = lightColorScheme(primary = Color(0xFF7A5C10), onPrimary = Color.White, background = Color(0xFFF7F1E6), onBackground = Color(0xFF1C1710), surface = Color(0xFFFFF8EE), onSurface = Color(0xFF1C1710), surfaceVariant = Color(0xFFEDE3D2), onSurfaceVariant = Color(0xFF524737))
private val Type = Typography(displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 42.sp), headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp), titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp), titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp), bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp), bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp))
@Composable
fun SonivaTheme(themeMode: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = when (themeMode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
    val context = LocalContext.current
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) } else if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = Type, content = content)
}
