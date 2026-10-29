package com.jacob.albumwallpaper.ui.theme

import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val FallbackDark = darkColorScheme(
    primary          = Green80,
    onPrimary        = Color.Black,
    primaryContainer = Green40,
    background       = Surface,
    surface          = Surface,
    surfaceVariant   = Surface2,
    surfaceContainer = Surface2,
    surfaceContainerHigh = Surface3,
    onBackground     = Color.White,
    onSurface        = Color.White,
)

@Composable
fun AlbumWallpaperTheme(
    seedColor: Color? = null,
    content: @Composable () -> Unit
) {
    val scheme = when {
        seedColor != null -> darkColorScheme(
            primary          = seedColor,
            onPrimary        = Color.Black,
            primaryContainer = seedColor.copy(alpha = 0.3f),
            background       = Surface,
            surface          = Surface,
            surfaceVariant   = Surface2,
            surfaceContainer = Surface2,
            surfaceContainerHigh = Surface3,
            onBackground     = Color.White,
            onSurface        = Color.White,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicDarkColorScheme(LocalContext.current)
        else -> FallbackDark
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
