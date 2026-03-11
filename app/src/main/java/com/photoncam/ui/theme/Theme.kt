package com.photoncam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Rangefinder-inspired palette — dark metal, leather accents
private val CameraBlack = Color(0xFF0E0E0E)
private val MetalGray = Color(0xFF2C2C2C)
private val ChromeAccent = Color(0xFFB8B8B8)
private val LeatherBrown = Color(0xFF3D2B1F)
private val AmberText = Color(0xFFFF8C00)

private val PhotonCamColorScheme = darkColorScheme(
    primary = ChromeAccent,
    onPrimary = CameraBlack,
    secondary = AmberText,
    onSecondary = CameraBlack,
    background = CameraBlack,
    surface = MetalGray,
    onBackground = Color.White,
    onSurface = Color.White,
    tertiary = LeatherBrown,
)

@Composable
fun PhotonCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PhotonCamColorScheme,
        content = content,
    )
}
