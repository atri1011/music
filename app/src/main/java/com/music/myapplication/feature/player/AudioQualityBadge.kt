package com.music.myapplication.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.ui.theme.AppShapes
import com.music.myapplication.ui.theme.AppSpacing

@Composable
internal fun AudioQualityBadge(
    quality: String,
    platform: Platform,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val normalized = quality.trim()
    val badgeText = if (compact) {
        audioQualityCompactLabel(normalized)
    } else {
        audioQualityDisplayLabel(normalized)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AppShapes.Small))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(
                horizontal = if (compact) AppSpacing.XXSmall else AppSpacing.XSmall,
                vertical = 3.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = platform.qualityBadgeMark(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
        )
        Text(
            text = badgeText,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

internal fun isPremiumAudioQuality(quality: String): Boolean {
    return quality.equals("320k", ignoreCase = true) ||
        quality.equals("flac", ignoreCase = true) ||
        quality.equals("flac24bit", ignoreCase = true)
}

private fun audioQualityDisplayLabel(quality: String): String = when {
    quality.equals("128k", ignoreCase = true) -> "标准 128K"
    quality.equals("320k", ignoreCase = true) -> "高品 320K"
    quality.equals("flac", ignoreCase = true) -> "无损 FLAC"
    quality.equals("flac24bit", ignoreCase = true) -> "Hi-Res"
    quality.isBlank() -> "标准"
    else -> quality.uppercase()
}

private fun audioQualityCompactLabel(quality: String): String = when {
    quality.equals("320k", ignoreCase = true) -> "高品"
    quality.equals("flac", ignoreCase = true) -> "无损"
    quality.equals("flac24bit", ignoreCase = true) -> "Hi-Res"
    quality.equals("128k", ignoreCase = true) -> "标准"
    quality.isBlank() -> "标准"
    else -> quality.uppercase()
}

private fun Platform.qualityBadgeMark(): String = when (this) {
    Platform.NETEASE -> "云"
    Platform.QQ -> "Q"
    Platform.KUWO -> "酷"
    Platform.LOCAL -> "本"
}
