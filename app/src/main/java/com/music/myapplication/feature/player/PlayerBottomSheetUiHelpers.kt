package com.music.myapplication.feature.player

internal fun calculateMiniPlayerProgress(positionMs: Long, durationMs: Long): Float =
    if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

internal fun shouldShowMiniPlayer(sheetFraction: Float): Boolean = sheetFraction < 0.5f

internal fun miniPlayerAlpha(sheetFraction: Float): Float = (1f - sheetFraction * 2f).coerceIn(0f, 1f)

internal fun shouldShowFullScreenPlayer(sheetFraction: Float): Boolean = sheetFraction > 0f

internal fun fullScreenPlayerAlpha(sheetFraction: Float): Float = (sheetFraction * 2f).coerceIn(0f, 1f)