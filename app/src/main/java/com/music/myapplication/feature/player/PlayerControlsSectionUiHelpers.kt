package com.music.myapplication.feature.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.myapplication.domain.model.PlaybackMode

internal data class PlayerControlsPalette(
    val contentColor: Color,
    val subtleColor: Color,
    val activeAccent: Color,
    val toolBackground: Color,
    val toolBorder: Color
)

internal fun playerControlsPalette(
    useLightContent: Boolean,
    accentColor: Color,
    colorScheme: ColorScheme
): PlayerControlsPalette = if (useLightContent) {
    PlayerControlsPalette(
        contentColor = Color.White,
        subtleColor = Color.White.copy(alpha = 0.64f),
        activeAccent = accentColor,
        toolBackground = Color.White.copy(alpha = 0.10f),
        toolBorder = Color.White.copy(alpha = 0.12f)
    )
} else {
    PlayerControlsPalette(
        contentColor = colorScheme.onSurface,
        subtleColor = colorScheme.onSurfaceVariant,
        activeAccent = colorScheme.primary,
        toolBackground = colorScheme.surfaceContainerHigh,
        toolBorder = colorScheme.outline.copy(alpha = 0.14f)
    )
}

internal fun playerControlsThumbRadius(sliderDragging: Boolean): Dp =
    if (sliderDragging) 8.dp else 6.dp

internal fun playerControlsProgressFraction(
    durationMs: Long,
    positionMs: Long,
    sliderDragging: Boolean,
    sliderPosition: Float
): Float = if (durationMs > 0L) {
    if (sliderDragging) sliderPosition else positionMs.toFloat() / durationMs.toFloat()
} else {
    0f
}

internal fun playerControlsDisplayedPositionMs(
    durationMs: Long,
    positionMs: Long,
    sliderDragging: Boolean,
    sliderPosition: Float
): Long = if (sliderDragging) {
    playerControlsSeekPositionMs(durationMs = durationMs, fraction = sliderPosition)
} else {
    positionMs
}

internal fun playerControlsFractionFromOffset(offsetX: Float, width: Float): Float =
    if (width > 0f) (offsetX / width).coerceIn(0f, 1f) else 0f

internal fun playerControlsDraggedSliderPosition(
    sliderPosition: Float,
    dragAmount: Float,
    width: Float
): Float = if (width > 0f) {
    (sliderPosition + dragAmount / width).coerceIn(0f, 1f)
} else {
    sliderPosition.coerceIn(0f, 1f)
}

internal fun playerControlsSeekPositionMs(durationMs: Long, fraction: Float): Long =
    (fraction.coerceIn(0f, 1f) * durationMs).toLong()

internal fun playerControlsTrackHeight(): Dp = 4.dp

internal fun playerControlsThumbHaloRadius(thumbRadius: Dp): Dp =
    thumbRadius + 2.dp

internal fun playerControlsThumbHaloAlpha(): Float = 0.15f

internal fun playerControlsModeIcon(playbackMode: PlaybackMode): ImageVector = when (playbackMode) {
    PlaybackMode.SEQUENTIAL -> Icons.Default.Repeat
    PlaybackMode.SHUFFLE -> Icons.Default.Shuffle
    PlaybackMode.REPEAT_ONE -> Icons.Default.RepeatOne
}

internal fun playerControlsFavoriteIcon(isFavorite: Boolean): ImageVector =
    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder

internal fun playerControlsFavoriteTint(
    isFavorite: Boolean,
    activeAccent: Color,
    subtleColor: Color
): Color = if (isFavorite) activeAccent else subtleColor

internal fun playerControlsPlayPauseIcon(isPlaying: Boolean): ImageVector =
    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow

internal fun playerControlsPlayPauseContentDescription(isPlaying: Boolean): String =
    if (isPlaying) "暂停" else "播放"

internal fun playerControlsDurationTextStyle(typography: Typography): TextStyle =
    typography.labelSmall.copy(fontFeatureSettings = "tnum")

internal fun playerControlsUtilityButtonSize(): Dp = 44.dp

internal fun playerControlsUtilityIconSize(): Dp = 22.dp

internal fun playerControlsTransportButtonSize(primary: Boolean): Dp =
    if (primary) 72.dp else 52.dp

internal fun playerControlsTransportIconSize(primary: Boolean): Dp =
    if (primary) 34.dp else 28.dp

internal fun playerControlsDefaultPressedScale(): Float = 0.94f

internal fun playerControlsPrimaryPressedScale(): Float = 0.92f
