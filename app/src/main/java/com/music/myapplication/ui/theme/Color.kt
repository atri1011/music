package com.music.myapplication.ui.theme

import androidx.compose.ui.graphics.Color

// Brand colors
val QQMusicGreen = Color(0xFF31C27C)
val QQMusicGreenDark = Color(0xFF28A769)
val QQMusicGreenLight = Color(0xFF5DD99B)

// Light theme
val LightBackground = Color(0xFFFAFAFA)
val LightSurface = Color.White
val LightSurfaceVariant = Color(0xFFF2F2F7)
val LightOnSurface = Color(0xFF1C1B1F)
val LightOnSurfaceVariant = Color(0xFF6E6E73)
val LightOutline = Color(0xFFD1D1D6)

// Dark theme — deeper for OLED-friendly immersive feel
val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF1C1C1E)
val DarkSurfaceVariant = Color(0xFF2C2C2E)
val DarkSurfaceElevated = Color(0xFF3A3A3C)
val DarkOnSurface = Color(0xFFF2F2F7)
val DarkOnSurfaceVariant = Color(0xFF98989D)
val DarkOutline = Color(0xFF48484A)

// Glassmorphism
val GlassSurfaceLight = Color(0xFFF2F4F7).copy(alpha = 0.96f)
val GlassSurfaceDark = Color(0xFF1C1C1E).copy(alpha = 0.70f)
val GlassBorderLight = LightOutline.copy(alpha = 0.65f)
val GlassBorderDark = Color.White.copy(alpha = 0.10f)

// Player overlay
val PlayerScrimDark = Color.Black.copy(alpha = 0.55f)
val PlayerScrimLight = Color.Black.copy(alpha = 0.30f)

// Player gradient
val PlayerBgTop = Color(0xFFE8ECF4)
val PlayerBgMiddle = Color(0xFFDDE3ED)
val PlayerBgBottom = Color(0xFFD8DFE9)
