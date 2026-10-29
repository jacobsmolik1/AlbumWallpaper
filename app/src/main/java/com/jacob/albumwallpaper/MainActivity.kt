package com.jacob.albumwallpaper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jacob.albumwallpaper.ui.MainScreen
import com.jacob.albumwallpaper.ui.theme.AlbumWallpaperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlbumWallpaperTheme {
                MainScreen()
            }
        }
    }
}
