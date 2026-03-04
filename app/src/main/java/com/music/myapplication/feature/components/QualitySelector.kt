package com.music.myapplication.feature.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val QUALITIES = listOf("128k", "320k", "flac", "flac24bit")
private val QUALITY_LABELS = mapOf(
    "128k" to "标准",
    "320k" to "高品质",
    "flac" to "无损",
    "flac24bit" to "Hi-Res"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySelector(
    currentQuality: String,
    onQualitySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    OutlinedButton(
        onClick = { showSheet = true },
        modifier = modifier
    ) {
        Text(QUALITY_LABELS[currentQuality] ?: currentQuality)
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = "音质选择",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                QUALITIES.forEach { quality ->
                    ListItem(
                        headlineContent = { Text(QUALITY_LABELS[quality] ?: quality) },
                        supportingContent = { Text(quality) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onQualitySelected(quality)
                                scope.launch {
                                    sheetState.hide()
                                    showSheet = false
                                }
                            },
                        trailingContent = if (quality == currentQuality) {
                            { Text("✓", color = MaterialTheme.colorScheme.primary) }
                        } else null
                    )
                }
            }
        }
    }
}
