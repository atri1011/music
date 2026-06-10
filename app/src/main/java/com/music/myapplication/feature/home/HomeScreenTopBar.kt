package com.music.myapplication.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.myapplication.ui.theme.AppSpacing
import com.music.myapplication.ui.theme.QQMusicGreen
import com.music.myapplication.ui.theme.glassSurface

@Composable
fun HomeScreenTopBar(
    onNavigateToSearch: () -> Unit,
    onRefresh: () -> Unit,
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = AppSpacing.Small, bottom = AppSpacing.XSmall)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.Large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeSearchField(
                onClick = onNavigateToSearch,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(AppSpacing.Small))
            HomeTopActionButton(
                onClick = onNavigateToSearch,
                icon = Icons.Default.LibraryMusic,
                contentDescription = "乐库"
            )
            Spacer(modifier = Modifier.width(AppSpacing.XSmall))
            HomeTopActionButton(
                onClick = onRefresh,
                icon = Icons.Default.Refresh,
                contentDescription = "刷新"
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = AppSpacing.Large,
                top = AppSpacing.Medium,
                end = AppSpacing.Large,
                bottom = AppSpacing.XXSmall
            ),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Small)
        ) {
            items(tabs.size) { index ->
                HomeCategoryPill(
                    text = tabs[index],
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) }
                )
            }
        }
    }
}

@Composable
private fun HomeSearchField(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(42.dp)
            .glassSurface(shape = RoundedCornerShape(999.dp), pressScale = true)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.Small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(AppSpacing.XSmall))
        Text(
            text = "周杰伦 心动歌手",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomeTopActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .glassSurface(shape = RoundedCornerShape(999.dp), pressScale = true)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun HomeCategoryPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(999.dp)
    val backgroundColor = if (selected) {
        QQMusicGreen
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides contentColor) {
        Box(
            modifier = Modifier
                .height(34.dp)
                .background(backgroundColor, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = if (selected) AppSpacing.Medium else AppSpacing.XSmall),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}
