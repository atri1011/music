package com.music.myapplication.core.download

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.myapplication.core.database.AppDatabase
import com.music.myapplication.core.database.dao.DownloadedTracksDao
import com.music.myapplication.core.database.dao.LocalTracksDao
import com.music.myapplication.core.database.entity.DownloadedTrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadRequestIdentityTest {

    private lateinit var database: AppDatabase
    private lateinit var downloadedTracksDao: DownloadedTracksDao
    private lateinit var localTracksDao: LocalTracksDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        downloadedTracksDao = database.downloadedTracksDao()
        localTracksDao = database.localTracksDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun staleRequestCannotOverrideNewProgressOrFinalState() {
        runBlocking {
            insertCurrentDownload()

            assertEquals(0, downloadedTracksDao.updateProgress(SONG_ID, PLATFORM, OLD_REQUEST_ID, 42))
            assertEquals(1, downloadedTracksDao.updateProgress(SONG_ID, PLATFORM, NEW_REQUEST_ID, 28))
            assertEquals(0, downloadedTracksDao.markFailed(SONG_ID, PLATFORM, OLD_REQUEST_ID, "旧任务失败", 42))
            assertEquals(
                0,
                downloadedTracksDao.updateDownloadComplete(
                    songId = SONG_ID,
                    platform = PLATFORM,
                    requestId = OLD_REQUEST_ID,
                    status = DownloadedTrackEntity.DownloadStatus.SUCCESS,
                    filePath = PLAYABLE_URI,
                    fileSize = 4096,
                    downloadedAt = 1700000000000
                )
            )
            assertEquals(0, downloadedTracksDao.delete(SONG_ID, PLATFORM, OLD_REQUEST_ID))

            val entity = downloadedTracksDao.get(SONG_ID, PLATFORM)
            assertNotNull(entity)
            assertEquals(DownloadedTrackEntity.DownloadStatus.DOWNLOADING, entity!!.downloadStatus)
            assertEquals(28, entity.progressPercent)
            assertEquals("", entity.filePath)
            assertEquals(NEW_REQUEST_ID, entity.requestId)
        }
    }

    @Test
    fun staleRequestCannotPersistLocalTrackWhenNewRetryOwnsRow() {
        runBlocking {
            insertCurrentDownload()

            try {
                persistCompletedDownload(
                    appDatabase = database,
                    downloadedTracksDao = downloadedTracksDao,
                    localTracksDao = localTracksDao,
                    songId = SONG_ID,
                    platform = PLATFORM,
                    requestId = OLD_REQUEST_ID,
                    title = "晴天",
                    artist = "周杰伦",
                    album = "叶惠美",
                    durationMs = 258000,
                    storedArtifact = artifact()
                )
                fail("Expected stale request to abort completion")
            } catch (_: StaleDownloadRequestException) {
                // expected
            }

            val entity = downloadedTracksDao.get(SONG_ID, PLATFORM)
            assertNotNull(entity)
            assertEquals(DownloadedTrackEntity.DownloadStatus.DOWNLOADING, entity!!.downloadStatus)
            assertEquals("", entity.filePath)
            assertEquals(NEW_REQUEST_ID, entity.requestId)
            assertTrue(localTracksDao.getAllOnce().isEmpty())
        }
    }

    @Test
    fun currentRequestCanPersistSuccessAndLocalTrack() {
        runBlocking {
            insertCurrentDownload()

            persistCompletedDownload(
                appDatabase = database,
                downloadedTracksDao = downloadedTracksDao,
                localTracksDao = localTracksDao,
                songId = SONG_ID,
                platform = PLATFORM,
                requestId = NEW_REQUEST_ID,
                title = "晴天",
                artist = "周杰伦",
                album = "叶惠美",
                durationMs = 258000,
                storedArtifact = artifact(),
                downloadedAt = 1700000000000
            )

            val entity = downloadedTracksDao.get(SONG_ID, PLATFORM)
            assertNotNull(entity)
            assertEquals(DownloadedTrackEntity.DownloadStatus.SUCCESS, entity!!.downloadStatus)
            assertEquals(100, entity.progressPercent)
            assertEquals(PLAYABLE_URI, entity.filePath)
            assertEquals("", entity.failureReason)
            assertEquals("", entity.requestId)
            assertEquals(1, localTracksDao.getAllOnce().size)
            assertEquals(PLAYABLE_URI, localTracksDao.getAllOnce().first().filePath)
        }
    }

    private suspend fun insertCurrentDownload() {
        downloadedTracksDao.insert(
            DownloadedTrackEntity(
                songId = SONG_ID,
                platform = PLATFORM,
                title = "晴天",
                artist = "周杰伦",
                album = "叶惠美",
                quality = "320k",
                requestId = NEW_REQUEST_ID,
                downloadStatus = DownloadedTrackEntity.DownloadStatus.DOWNLOADING
            )
        )
    }

    private fun artifact() = StoredDownloadArtifact(
        playableUri = PLAYABLE_URI,
        mediaStoreId = 88L,
        fileSizeBytes = 4096L,
        mimeType = "audio/mpeg"
    )

    private companion object {
        const val SONG_ID = "song-1"
        const val PLATFORM = "qq"
        const val NEW_REQUEST_ID = "req-new"
        const val OLD_REQUEST_ID = "req-old"
        const val PLAYABLE_URI = "content://media/external/audio/media/88"
    }
}
