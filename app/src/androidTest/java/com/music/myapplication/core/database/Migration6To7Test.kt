package com.music.myapplication.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration6To7Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate6To7AddsDownloadProgressAndFailureReasonColumns() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `downloaded_tracks` (
                    `song_id` TEXT NOT NULL,
                    `platform` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `artist` TEXT NOT NULL,
                    `album` TEXT NOT NULL DEFAULT '',
                    `cover_url` TEXT NOT NULL DEFAULT '',
                    `duration_ms` INTEGER NOT NULL DEFAULT 0,
                    `file_path` TEXT NOT NULL DEFAULT '',
                    `file_size_bytes` INTEGER NOT NULL DEFAULT 0,
                    `quality` TEXT NOT NULL DEFAULT '128k',
                    `download_status` TEXT NOT NULL DEFAULT 'downloading',
                    `downloaded_at` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`song_id`, `platform`)
                )
                """.trimIndent()
            )
            execSQL("CREATE INDEX IF NOT EXISTS `idx_downloaded_status` ON `downloaded_tracks` (`download_status`)")
            execSQL("CREATE INDEX IF NOT EXISTS `idx_downloaded_at` ON `downloaded_tracks` (`downloaded_at`)")
            execSQL(
                """
                INSERT INTO `downloaded_tracks`
                (`song_id`, `platform`, `title`, `artist`, `album`, `cover_url`, `duration_ms`, `file_path`, `file_size_bytes`, `quality`, `download_status`, `downloaded_at`)
                VALUES ('song-1', 'qq', '晴天', '周杰伦', '叶惠美', '', 258000, 'content://media/external/audio/media/88', 4096, '320k', 'success', 1700000000000)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7).apply {
            query(
                """
                SELECT `song_id`, `progress_percent`, `failure_reason`
                FROM `downloaded_tracks`
                WHERE `song_id` = 'song-1'
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("song-1", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
                assertEquals("", cursor.getString(2))
            }
            close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-6-7-test"
    }
}
