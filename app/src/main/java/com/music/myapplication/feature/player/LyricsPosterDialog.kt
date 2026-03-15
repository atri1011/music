package com.music.myapplication.feature.player

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.music.myapplication.domain.model.LyricLine
import com.music.myapplication.domain.model.Track
import com.music.myapplication.ui.theme.AppShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    var posterBitmap by remember(track.id, lyricLine.text, lyricLine.translation, selectedTemplate) {
        mutableStateOf<Bitmap?>(null)
    }
    var isGenerating by remember(track.id, lyricLine.text, lyricLine.translation, selectedTemplate) {
        mutableStateOf(true)
    }
    var isWorking by remember { mutableStateOf(false) }

    LaunchedEffect(track.id, lyricLine.text, lyricLine.translation, selectedTemplate) {
        isGenerating = true
        posterBitmap = runCatching {
            LyricsPosterGenerator.generate(
                context = context,
                track = track,
                lyricLine = lyricLine,
                template = selectedTemplate
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LyricsPosterTemplate.entries.forEach { template ->
                        if (template == selectedTemplate) {
                            FilledTonalButton(onClick = { selectedTemplate = template }) {
                                Text(template.displayName)
                            }
                        } else {
                            OutlinedButton(onClick = { selectedTemplate = template }) {
                                Text(template.displayName)
                            }
                        }
                    }
                }

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
                                val message = runCatching {
                                    savePosterBitmap(context, bitmap, track, selectedTemplate)
                                }.fold(
                                    onSuccess = { "已保存海报：$it" },
                                    onFailure = { it.message ?: "保存海报失败" }
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
                                val error = runCatching {
                                    sharePosterBitmap(context, bitmap, track, selectedTemplate)
                                }.exceptionOrNull()
                                error?.let {
                                    Toast.makeText(
                                        context,
                                        it.message ?: "分享海报失败",
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

private suspend fun savePosterBitmap(
    context: Context,
    bitmap: Bitmap,
    track: Track,
    template: LyricsPosterTemplate
): String = withContext(Dispatchers.IO) {
    val fileName = buildPosterFileName(track, template)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, POSTER_MIME_TYPE)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/MyApplication"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("系统相册写入失败")
        resolver.openOutputStream(uri)?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        } ?: error("海报输出流创建失败")
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return@withContext "系统相册 / Pictures/MyApplication"
    }

    val baseDirectory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        ?: context.filesDir
    val directory = File(baseDirectory, POSTER_DIRECTORY).apply { mkdirs() }
    val targetFile = File(directory, fileName)
    targetFile.outputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    }
    MediaScannerConnection.scanFile(
        context,
        arrayOf(targetFile.absolutePath),
        arrayOf(POSTER_MIME_TYPE),
        null
    )
    targetFile.absolutePath
}

private suspend fun sharePosterBitmap(
    context: Context,
    bitmap: Bitmap,
    track: Track,
    template: LyricsPosterTemplate
) = withContext(Dispatchers.IO) {
    val sharedDirectory = File(context.cacheDir, SHARED_POSTER_DIRECTORY).apply { mkdirs() }
    val targetFile = File(sharedDirectory, buildPosterFileName(track, template))
    targetFile.outputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    }

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        targetFile
    )
    withContext(Dispatchers.Main) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = POSTER_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${track.title} 歌词海报")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        val chooser = Intent.createChooser(intent, "分享歌词海报")
        if (context !is android.app.Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}

private fun buildPosterFileName(track: Track, template: LyricsPosterTemplate): String {
    val safeTitle = track.title
        .ifBlank { "lyrics_poster" }
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .take(32)
    return "${safeTitle}_${template.name.lowercase()}_${System.currentTimeMillis()}.png"
}

private const val POSTER_DIRECTORY = "lyrics_posters"
private const val SHARED_POSTER_DIRECTORY = "shared_images"
private const val POSTER_MIME_TYPE = "image/png"
