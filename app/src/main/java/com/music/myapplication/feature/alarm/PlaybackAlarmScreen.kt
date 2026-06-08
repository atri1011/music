package com.music.myapplication.feature.alarm

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.domain.model.Playlist
import com.music.myapplication.ui.theme.AppShapes
import com.music.myapplication.ui.theme.AppSpacing
import com.music.myapplication.ui.theme.QQMusicGreen
import com.music.myapplication.ui.theme.appPremiumBackground
import com.music.myapplication.ui.theme.glassSurface

@Composable
fun PlaybackAlarmScreen(
    onBack: () -> Unit,
    viewModel: PlaybackAlarmViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .appPremiumBackground()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            PlaybackAlarmTopBar(onBack = onBack)
            Spacer(modifier = Modifier.height(AppSpacing.Medium))

            state.scheduledAlarm?.let { alarm ->
                CurrentAlarmCard(alarm = alarm)
                Spacer(modifier = Modifier.height(AppSpacing.Medium))
            }

            AlarmTimeCard(
                hourText = state.hourText,
                minuteText = state.minuteText,
                onHourChange = viewModel::setHour,
                onMinuteChange = viewModel::setMinute
            )
            Spacer(modifier = Modifier.height(AppSpacing.Medium))

            PlaylistPickerCard(
                playlists = state.playlists,
                selectedPlaylistId = state.selectedPlaylistId,
                onSelect = viewModel::selectPlaylist
            )
            Spacer(modifier = Modifier.height(AppSpacing.Large))

            Button(
                onClick = viewModel::saveAlarm,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.scheduledAlarm == null) "设置定时播放" else "更新定时播放")
            }
            if (state.scheduledAlarm != null) {
                TextButton(
                    onClick = viewModel::cancelAlarm,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("取消定时播放")
                }
            }
        }
    }
}

@Composable
private fun PlaybackAlarmTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Spacer(modifier = Modifier.width(AppSpacing.Small))
        Text(
            text = "定时播放",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CurrentAlarmCard(alarm: com.music.myapplication.core.datastore.PlaybackAlarmSchedule) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape = RoundedCornerShape(AppShapes.Medium))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBubble(icon = Icons.Outlined.Schedule)
        Spacer(modifier = Modifier.width(AppSpacing.Medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatTriggerTime(alarm.triggerAtMs),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = alarm.playlistName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AlarmTimeCard(
    hourText: String,
    minuteText: String,
    onHourChange: (String) -> Unit,
    onMinuteChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape = RoundedCornerShape(AppShapes.Medium))
            .padding(16.dp)
    ) {
        SectionTitle(icon = Icons.Default.Alarm, title = "时间")
        Spacer(modifier = Modifier.height(AppSpacing.Small))
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Small)) {
            OutlinedTextField(
                value = hourText,
                onValueChange = onHourChange,
                label = { Text("小时") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = minuteText,
                onValueChange = onMinuteChange,
                label = { Text("分钟") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PlaylistPickerCard(
    playlists: List<Playlist>,
    selectedPlaylistId: String,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape = RoundedCornerShape(AppShapes.Medium))
            .padding(16.dp)
    ) {
        SectionTitle(icon = Icons.AutoMirrored.Outlined.QueueMusic, title = "歌单")
        Spacer(modifier = Modifier.height(AppSpacing.Small))
        if (playlists.isEmpty()) {
            Text(
                text = "暂无本地歌单",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        } else {
            playlists.forEach { playlist ->
                PlaylistAlarmRow(
                    playlist = playlist,
                    selected = playlist.id == selectedPlaylistId,
                    onClick = { onSelect(playlist.id) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistAlarmRow(
    playlist: Playlist,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppShapes.Small))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${playlist.trackCount} 首",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionTitle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBubble(icon = icon)
        Spacer(modifier = Modifier.width(AppSpacing.Small))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun IconBubble(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .padding(1.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .glassSurface(shape = CircleShape)
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = QQMusicGreen,
            modifier = Modifier.size(19.dp)
        )
    }
}
