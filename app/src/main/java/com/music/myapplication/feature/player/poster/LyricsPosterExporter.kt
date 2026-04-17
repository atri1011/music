package com.music.myapplication.feature.player.poster

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.music.myapplication.domain.model.Track
import com.music.myapplication.feature.player.LyricsPosterTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object LyricsPosterExporter {
    suspend fun savePosterBitmap(
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

    suspend fun sharePosterBitmap(
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
}