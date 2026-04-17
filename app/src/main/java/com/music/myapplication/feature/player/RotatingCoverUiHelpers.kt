package com.music.myapplication.feature.player

import androidx.compose.ui.graphics.Color

internal const val ROTATING_COVER_BREATHING_DURATION_MS = 3000
internal const val ROTATING_COVER_IDLE_SCALE = 1.0f
internal const val ROTATING_COVER_ACTIVE_SCALE = 1.02f
internal const val ROTATING_COVER_IDLE_GLOW_ALPHA = 0.35f
internal const val ROTATING_COVER_ACTIVE_GLOW_ALPHA = 0.55f
internal const val ROTATING_COVER_GLOW_SCALE = 1.15f
internal const val ROTATING_COVER_GLOW_DRAW_ALPHA = 0.45f

internal fun rotatingCoverScale(isPlaying: Boolean, animatedScale: Float): Float =
    if (isPlaying) animatedScale else ROTATING_COVER_IDLE_SCALE

internal fun rotatingCoverGlowAlpha(isPlaying: Boolean, animatedGlowAlpha: Float): Float =
    if (isPlaying) animatedGlowAlpha else ROTATING_COVER_IDLE_GLOW_ALPHA

internal fun shouldShowRotatingCoverGlow(glowColor: Color): Boolean =
    glowColor != Color.Transparent

internal fun rotatingCoverGlowDrawColor(glowColor: Color): Color =
    glowColor.copy(alpha = ROTATING_COVER_GLOW_DRAW_ALPHA)