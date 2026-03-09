package com.music.myapplication.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.myapplication.feature.player.state.SleepTimerMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepTimerPickerSheet(
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timerState by playerViewModel.sleepTimerState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val countdownOptions = listOf(15, 30, 45, 60, 90)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "定时关闭",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                if (timerState.isActive) {
                    IconButton(onClick = {
                        playerViewModel.cancelSleepTimer()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "取消定时"
                        )
                    }
                }
            }

            if (timerState.isActive) {
                Spacer(modifier = Modifier.height(8.dp))
                val statusText = when (timerState.mode) {
                    SleepTimerMode.COUNTDOWN -> "剩余 ${timerState.remainingMinutes} 分钟后暂停"
                    SleepTimerMode.AFTER_CURRENT_TRACK -> "播完当前歌曲后暂停"
                    SleepTimerMode.OFF -> ""
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = {
                    playerViewModel.cancelSleepTimer()
                }) {
                    Text("取消定时")
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    countdownOptions.forEach { minutes ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                playerViewModel.startSleepTimer(minutes)
                                onDismiss()
                            },
                            label = { Text("${minutes} 分钟") }
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = {
                            playerViewModel.startSleepTimerAfterTrack()
                            onDismiss()
                        },
                        label = { Text("播完当前歌曲") }
                    )
                }
            }
        }
    }
}
