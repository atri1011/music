package com.music.myapplication.feature.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.myapplication.ui.theme.AppShapes
import com.music.myapplication.ui.theme.AppSpacing
import com.music.myapplication.ui.theme.verticalGradientScrim

@Composable
fun PlaylistCard(
    name: String,
    coverUrl: String,
    trackCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(AppShapes.Medium)
) {

    Box(
        modifier = modifier
            .width(140.dp)
            .height(140.dp)
            .clip(shape)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                shape = shape
            )
            .appPressClick(onClick = onClick)
    ) {
        CoverImage(
            url = coverUrl,
            contentDescription = name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Top gloss highlight (subtle, premium)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalGradientScrim(
                    color = Color.White.copy(alpha = 0.16f),
                    startY = 0f,
                    endY = 0.35f
                )
        )

        // Gradient overlay for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalGradientScrim(
                    color = Color.Black.copy(alpha = 0.55f),
                    startY = 0.40f,
                    endY = 1f
                )
        )

        // Name + track count at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = AppSpacing.Small, vertical = AppSpacing.XSmall)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (trackCount > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${trackCount} 首",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.86f),
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}
