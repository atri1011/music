package com.music.myapplication.feature.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.myapplication.ui.theme.AppSpacing
import com.music.myapplication.ui.theme.glassSurface

@Composable
fun HomeScreenTopBar(
    onNavigateToSearch: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.Large, vertical = AppSpacing.Small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "首页",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.weight(1f))
        HomeTopActionButton(
            onClick = { /* scan */ },
            icon = Icons.Default.QrCodeScanner,
            contentDescription = "扫码"
        )
        Spacer(modifier = Modifier.width(AppSpacing.XSmall))
        HomeTopActionButton(
            onClick = onNavigateToSearch,
            icon = Icons.Default.Search,
            contentDescription = "搜索"
        )
        Spacer(modifier = Modifier.width(AppSpacing.XSmall))
        HomeTopActionButton(
            onClick = onRefresh,
            icon = Icons.Default.Refresh,
            contentDescription = "刷新"
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
