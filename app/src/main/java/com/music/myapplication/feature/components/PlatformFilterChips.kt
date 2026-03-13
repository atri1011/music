package com.music.myapplication.feature.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.myapplication.domain.model.Platform

@Composable
fun PlatformFilterChips(
    selectedPlatform: Platform,
    onPlatformSelected: (Platform) -> Unit,
    modifier: Modifier = Modifier,
    platforms: List<Platform> = Platform.onlinePlatforms
) {
    SegmentedChoiceRow(
        items = platforms,
        selectedItem = selectedPlatform,
        onItemSelected = onPlatformSelected,
        modifier = modifier
    ) { platform, selected ->
        Text(
            text = platform.displayName,
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
fun <T> SegmentedChoiceRow(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (item: T, selected: Boolean) -> Unit
) {
    val palette = rememberChoiceTogglePalette()
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = palette.activeContainerColor,
        activeContentColor = palette.activeContentColor,
        activeBorderColor = palette.activeBorderColor,
        inactiveContainerColor = palette.inactiveContainerColor,
        inactiveContentColor = palette.inactiveContentColor,
        inactiveBorderColor = palette.inactiveBorderColor
    )

    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth()
    ) {
        items.forEachIndexed { index, item ->
            val selected = item == selectedItem
            SegmentedButton(
                selected = selected,
                onClick = { onItemSelected(item) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = items.size
                ),
                modifier = Modifier.weight(1f),
                colors = colors,
                label = { label(item, selected) }
            )
        }
    }
}

@Composable
fun ChoicePill(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit
) {
    val palette = rememberChoiceTogglePalette()
    val shape = RoundedCornerShape(999.dp)
    val containerColor = if (selected) palette.activeContainerColor else palette.inactiveContainerColor
    val contentColor = if (selected) palette.activeContentColor else palette.inactiveContentColor
    val borderColor = if (selected) palette.activeBorderColor else palette.inactiveBorderColor

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier
                .clip(shape)
                .background(containerColor)
                .border(width = 1.dp, color = borderColor, shape = shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            label()
        }
    }
}

@Immutable
private data class ChoiceTogglePalette(
    val activeContainerColor: androidx.compose.ui.graphics.Color,
    val activeContentColor: androidx.compose.ui.graphics.Color,
    val activeBorderColor: androidx.compose.ui.graphics.Color,
    val inactiveContainerColor: androidx.compose.ui.graphics.Color,
    val inactiveContentColor: androidx.compose.ui.graphics.Color,
    val inactiveBorderColor: androidx.compose.ui.graphics.Color
)

@Composable
private fun rememberChoiceTogglePalette(): ChoiceTogglePalette = ChoiceTogglePalette(
    activeContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    activeContentColor = MaterialTheme.colorScheme.onSurface,
    activeBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
    inactiveContainerColor = MaterialTheme.colorScheme.surface,
    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    inactiveBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
)
