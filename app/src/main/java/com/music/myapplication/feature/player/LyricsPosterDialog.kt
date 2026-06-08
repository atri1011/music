package com.music.myapplication.feature.player

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.myapplication.domain.model.LyricLine
import com.music.myapplication.domain.model.Track
import com.music.myapplication.feature.player.actions.saveLyricsPosterMessage
import com.music.myapplication.feature.player.actions.shareLyricsPosterErrorMessage
import com.music.myapplication.ui.theme.AppShapes
import kotlinx.coroutines.launch

@Composable
fun LyricsPosterDialog(
    track: Track,
    lyricLine: LyricLine,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTemplate by remember(track.id, lyricLine.text, lyricLine.translation) {
        mutableStateOf(LyricsPosterTemplate.AURORA)
    }
    var customBackgroundUri by remember(track.id, lyricLine.text, lyricLine.translation) {
        mutableStateOf<String?>(null)
    }
    val backgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            customBackgroundUri = uri.toString()
            selectedTemplate = LyricsPosterTemplate.CUSTOM
        }
    }
    var posterBitmap by remember(track.id, lyricLine.text, lyricLine.translation, selectedTemplate, customBackgroundUri) {
        mutableStateOf<Bitmap?>(null)
    }
    var isGenerating by remember(track.id, lyricLine.text, lyricLine.translation, selectedTemplate, customBackgroundUri) {
        mutableStateOf(true)
    }
    var isWorking by remember { mutableStateOf(false) }

    LaunchedEffect(track.id, lyricLine.text, lyricLine.translation, selectedTemplate, customBackgroundUri) {
        isGenerating = true
        posterBitmap = runCatching {
            LyricsPosterGenerator.generate(
                context = context,
                track = track,
                lyricLine = lyricLine,
                template = selectedTemplate,
                customBackgroundUri = customBackgroundUri
            )
        }.getOrNull()
        isGenerating = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            shape = RoundedCornerShape(AppShapes.XLarge),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    text = "歌词海报",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = lyricLine.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(AppShapes.Large)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isGenerating -> CircularProgressIndicator()
                        posterBitmap != null -> Image(
                            bitmap = posterBitmap!!.asImageBitmap(),
                            contentDescription = "歌词海报预览",
                            modifier = Modifier.fillMaxWidth()
                        )
                        else -> Text(
                            text = "海报生成失败，换个模板再试试。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "模板",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LyricsPosterTemplate.entries.chunked(3).forEach { rowTemplates ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowTemplates.forEach { template ->
                                val buttonModifier = Modifier.weight(1f)
                                if (template == selectedTemplate) {
                                    FilledTonalButton(
                                        onClick = { selectedTemplate = template },
                                        modifier = buttonModifier
                                    ) {
                                        Text(template.displayName, maxLines = 1)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { selectedTemplate = template },
                                        modifier = buttonModifier
                                    ) {
                                        Text(template.displayName, maxLines = 1)
                                    }
                                }
                            }
                            repeat(3 - rowTemplates.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "自定义背景",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { backgroundPicker.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (customBackgroundUri == null) "选择图片" else "更换图片")
                    }
                    TextButton(
                        onClick = { customBackgroundUri = null },
                        enabled = customBackgroundUri != null
                    ) {
                        Text("清除")
                    }
                }
                Text(
                    text = if (customBackgroundUri == null) {
                        "选择图片后会自动切到“自定义”模板。"
                    } else {
                        "已选择背景图，生成时会叠加暗色遮罩保证歌词可读。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isWorking
                    ) {
                        Text("关闭")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            val bitmap = posterBitmap ?: return@OutlinedButton
                            scope.launch {
                                isWorking = true
                                val message = saveLyricsPosterMessage(
                                    context = context,
                                    bitmap = bitmap,
                                    track = track,
                                    template = selectedTemplate
                                )
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                isWorking = false
                            }
                        },
                        enabled = posterBitmap != null && !isGenerating && !isWorking
                    ) {
                        Text(if (isWorking) "处理中..." else "保存")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = {
                            val bitmap = posterBitmap ?: return@FilledTonalButton
                            scope.launch {
                                isWorking = true
                                val error = shareLyricsPosterErrorMessage(
                                    context = context,
                                    bitmap = bitmap,
                                    track = track,
                                    template = selectedTemplate
                                )
                                error?.let {
                                    Toast.makeText(
                                        context,
                                        it,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                isWorking = false
                            }
                        },
                        enabled = posterBitmap != null && !isGenerating && !isWorking
                    ) {
                        Text("分享")
                    }
                }
            }
        }
    }
}
