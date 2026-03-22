package com.music.myapplication.core.download

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume

internal val DOWNLOAD_RELATIVE_PATH = "${Environment.DIRECTORY_MUSIC}/Music Player"

internal data class StoredDownloadArtifact(
    val playableUri: String,
    val mediaStoreId: Long,
    val fileSizeBytes: Long,
    val mimeType: String
)

internal enum class DownloadFailureAction {
    RETRY,
    FAIL
}

internal fun hasLegacyPublicWritePermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
    return ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

internal fun sanitizeDownloadFileName(
    title: String,
    artist: String,
    songId: String,
    extension: String
): String {
    val safeTitle = title.trim().ifBlank { "未知歌曲" }
        .replace(Regex("[^\\w\\u4e00-\\u9fff\\-. ]"), "_")
    val safeArtist = artist.trim().ifBlank { "未知艺术家" }
        .replace(Regex("[^\\w\\u4e00-\\u9fff\\-. ]"), "_")
    val safeExtension = extension.lowercase().ifBlank { "mp3" }
    return "${safeArtist} - ${safeTitle}_${songId}.$safeExtension"
}

internal fun inferDownloadMimeType(contentTypeHeader: String?, requestUrl: String): String {
    val normalizedHeader = contentTypeHeader
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        .orEmpty()
    if (normalizedHeader.startsWith("audio/")) {
        return normalizedHeader
    }

    return when (inferDownloadExtension(contentTypeHeader, requestUrl)) {
        "flac" -> "audio/flac"
        "m4a", "mp4" -> "audio/mp4"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        "aac" -> "audio/aac"
        "amr" -> "audio/amr"
        else -> "audio/mpeg"
    }
}

internal fun inferDownloadExtension(contentTypeHeader: String?, requestUrl: String): String {
    val extensionFromUrl = requestUrl
        .substringBefore('?')
        .substringAfterLast('/', "")
        .substringAfterLast('.', "")
        .lowercase()
        .takeIf { it in KNOWN_AUDIO_EXTENSIONS }
    if (extensionFromUrl != null) {
        return extensionFromUrl
    }

    return when (
        contentTypeHeader
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()
    ) {
        "audio/flac", "application/flac" -> "flac"
        "audio/mp4", "audio/x-m4a", "video/mp4" -> "m4a"
        "audio/ogg", "audio/opus" -> "ogg"
        "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
        "audio/aac", "audio/x-aac" -> "aac"
        "audio/amr" -> "amr"
        else -> "mp3"
    }
}

internal fun classifyDownloadFailure(
    throwable: Throwable? = null,
    httpCode: Int? = null
): DownloadFailureAction {
    if (throwable is CancellationException) return DownloadFailureAction.FAIL
    if (httpCode != null) {
        return when {
            httpCode == 408 || httpCode == 429 || httpCode in 500..599 -> DownloadFailureAction.RETRY
            else -> DownloadFailureAction.FAIL
        }
    }
    return when (throwable) {
        is SocketTimeoutException -> DownloadFailureAction.RETRY
        is IOException -> {
            if (throwable is FileNotFoundException) DownloadFailureAction.FAIL
            else DownloadFailureAction.RETRY
        }
        else -> DownloadFailureAction.FAIL
    }
}

internal fun isLocalPlayableUriAvailable(context: Context, rawUri: String): Boolean {
    val value = rawUri.trim()
    if (value.isBlank()) return false

    return when {
        value.startsWith("content://", ignoreCase = true) -> {
            runCatching {
                context.contentResolver.openAssetFileDescriptor(Uri.parse(value), "r")?.use { true } ?: false
            }.getOrDefault(false)
        }

        value.startsWith("file://", ignoreCase = true) -> {
            runCatching { File(Uri.parse(value).path.orEmpty()).exists() }.getOrDefault(false)
        }

        else -> File(value).exists()
    }
}

internal fun deleteLocalPlayableUri(context: Context, rawUri: String): Boolean {
    val value = rawUri.trim()
    if (value.isBlank()) return false

    return when {
        value.startsWith("content://", ignoreCase = true) -> {
            runCatching {
                context.contentResolver.delete(Uri.parse(value), null, null) >= 0
            }.getOrDefault(false)
        }

        value.startsWith("file://", ignoreCase = true) -> {
            runCatching { File(Uri.parse(value).path.orEmpty()).delete() }.getOrDefault(false)
        }

        else -> runCatching { File(value).delete() }.getOrDefault(false)
    }
}

internal fun mediaStoreIdFromPlayableUri(rawUri: String): Long? {
    if (!rawUri.startsWith("content://", ignoreCase = true)) return null
    return Uri.parse(rawUri).lastPathSegment?.toLongOrNull()
}

internal suspend fun writeTrackToPublicStorage(
    context: Context,
    songId: String,
    title: String,
    artist: String,
    album: String,
    requestUrl: String,
    contentTypeHeader: String?,
    inputStream: InputStream,
    expectedLength: Long,
    onProgress: suspend (Int) -> Unit,
    shouldAbort: () -> Boolean
): StoredDownloadArtifact {
    val mimeType = inferDownloadMimeType(contentTypeHeader, requestUrl)
    val extension = inferDownloadExtension(contentTypeHeader, requestUrl)
    val displayName = sanitizeDownloadFileName(title, artist, songId, extension)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        writeTrackToMediaStore(
            context = context,
            displayName = displayName,
            title = title,
            artist = artist,
            album = album,
            mimeType = mimeType,
            inputStream = inputStream,
            expectedLength = expectedLength,
            onProgress = onProgress,
            shouldAbort = shouldAbort
        )
    } else {
        writeTrackToLegacyPublicDirectory(
            context = context,
            displayName = displayName,
            mimeType = mimeType,
            inputStream = inputStream,
            expectedLength = expectedLength,
            onProgress = onProgress,
            shouldAbort = shouldAbort
        )
    }
}

private suspend fun writeTrackToMediaStore(
    context: Context,
    displayName: String,
    title: String,
    artist: String,
    album: String,
    mimeType: String,
    inputStream: InputStream,
    expectedLength: Long,
    onProgress: suspend (Int) -> Unit,
    shouldAbort: () -> Boolean
): StoredDownloadArtifact {
    val resolver = context.contentResolver
    val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, DOWNLOAD_RELATIVE_PATH)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
        put(MediaStore.Audio.Media.TITLE, title.ifBlank { "未知歌曲" })
        put(MediaStore.Audio.Media.ARTIST, artist.ifBlank { "未知艺术家" })
        put(MediaStore.Audio.Media.ALBUM, album)
        put(MediaStore.Audio.Media.IS_MUSIC, 1)
    }
    val itemUri = resolver.insert(collection, values)
        ?: throw IOException("写入媒体库失败")

    return try {
        val writtenSize = resolver.openOutputStream(itemUri, "w")?.use { outputStream ->
            copyStreamWithProgress(
                inputStream = inputStream,
                outputStream = outputStream,
                expectedLength = expectedLength,
                onProgress = onProgress,
                shouldAbort = shouldAbort
            )
        } ?: throw IOException("无法打开媒体库输出流")

        ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }.also { resolver.update(itemUri, it, null, null) }

        StoredDownloadArtifact(
            playableUri = itemUri.toString(),
            mediaStoreId = ContentUris.parseId(itemUri),
            fileSizeBytes = writtenSize,
            mimeType = mimeType
        )
    } catch (throwable: Throwable) {
        resolver.delete(itemUri, null, null)
        throw throwable
    }
}

@Suppress("DEPRECATION")
private suspend fun writeTrackToLegacyPublicDirectory(
    context: Context,
    displayName: String,
    mimeType: String,
    inputStream: InputStream,
    expectedLength: Long,
    onProgress: suspend (Int) -> Unit,
    shouldAbort: () -> Boolean
): StoredDownloadArtifact {
    val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
    val targetDirectory = File(musicDir, "Music Player").apply {
        if (!exists()) mkdirs()
    }
    if (!targetDirectory.exists()) {
        throw IOException("公共音乐目录不可用")
    }

    val outputFile = uniqueFile(targetDirectory, displayName)
    return try {
        val writtenSize = outputFile.outputStream().use { outputStream ->
            copyStreamWithProgress(
                inputStream = inputStream,
                outputStream = outputStream,
                expectedLength = expectedLength,
                onProgress = onProgress,
                shouldAbort = shouldAbort
            )
        }

        val scannedUri = scanLegacyAudioFile(context, outputFile, mimeType)
            ?: throw IOException("媒体库扫描失败")

        StoredDownloadArtifact(
            playableUri = scannedUri.toString(),
            mediaStoreId = mediaStoreIdFromPlayableUri(scannedUri.toString())
                ?: throw IOException("媒体库条目无效"),
            fileSizeBytes = writtenSize,
            mimeType = mimeType
        )
    } catch (throwable: Throwable) {
        outputFile.delete()
        throw throwable
    }
}

private suspend fun copyStreamWithProgress(
    inputStream: InputStream,
    outputStream: OutputStream,
    expectedLength: Long,
    onProgress: suspend (Int) -> Unit,
    shouldAbort: () -> Boolean
): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var totalBytes = 0L
    var lastProgress = -1

    while (true) {
        if (shouldAbort()) {
            throw CancellationException("下载已取消")
        }

        val read = inputStream.read(buffer)
        if (read == -1) break

        outputStream.write(buffer, 0, read)
        totalBytes += read

        if (expectedLength > 0L) {
            val progress = ((totalBytes * 100L) / expectedLength)
                .toInt()
                .coerceIn(0, 99)
            if (progress != lastProgress) {
                lastProgress = progress
                onProgress(progress)
            }
        }
    }

    outputStream.flush()
    return totalBytes
}

private suspend fun scanLegacyAudioFile(
    context: Context,
    file: File,
    mimeType: String
): Uri? = suspendCancellableCoroutine { continuation ->
    MediaScannerConnection.scanFile(
        context,
        arrayOf(file.absolutePath),
        arrayOf(mimeType)
    ) { _, uri ->
        if (continuation.isActive) {
            continuation.resume(uri)
        }
    }
}

private fun uniqueFile(directory: File, displayName: String): File {
    val extension = displayName.substringAfterLast('.', "")
    val baseName = displayName.removeSuffix(if (extension.isBlank()) "" else ".$extension")
    var candidate = File(directory, displayName)
    var index = 1
    while (candidate.exists()) {
        val suffix = if (extension.isBlank()) "" else ".$extension"
        candidate = File(directory, "$baseName ($index)$suffix")
        index += 1
    }
    return candidate
}

private val KNOWN_AUDIO_EXTENSIONS = setOf(
    "mp3",
    "flac",
    "m4a",
    "mp4",
    "ogg",
    "opus",
    "wav",
    "aac",
    "amr"
)
