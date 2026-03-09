package com.music.myapplication.feature.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.myapplication.domain.model.Platform

@Composable
fun PlatformFilterChips(
    selectedPlatform: Platform,
    onPlatformSelected: (Platform) -> Unit,
    modifier: Modifier = Modifier,
    platforms: List<Platform> = Platform.entries.toList()
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (platform in platforms) {
            FilterChip(
                selected = platform == selectedPlatform,
                onClick = { onPlatformSelected(platform) },
                label = { Text(platform.displayName, maxLines = 1) },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}
