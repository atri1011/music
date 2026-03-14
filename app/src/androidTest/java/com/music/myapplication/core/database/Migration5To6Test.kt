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
class Migration5To6Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate5To6AddsRemoteOrderColumnWithNullDefault() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playlists` (
                    `playlist_id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `cover_url` TEXT NOT NULL DEFAULT '',
                    `created_at` INTEGER NOT NULL DEFAULT 0,
                    `updated_at` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`playlist_id`)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playlist_remote_map` (
                    `playlist_id` TEXT NOT NULL,
                    `source_platform` TEXT NOT NULL,
                    `source_playlist_id` TEXT NOT NULL,
                    `owner_uid` TEXT NOT NULL,
                    `remote_signature` TEXT NOT NULL DEFAULT '',
                    `last_synced_song_signature` TEXT NOT NULL DEFAULT '',
                    `last_synced_at` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`playlist_id`),
                    FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`playlist_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS `idx_playlist_remote_map_source_unique`
                ON `playlist_remote_map` (`source_platform`, `source_playlist_id`, `owner_uid`)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO `playlists` (`playlist_id`, `name`, `cover_url`, `created_at`, `updated_at`)
                VALUES ('playlist-local-1', '本地歌单', '', 1700000000000, 1700000000000)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO `playlist_remote_map`
                (`playlist_id`, `source_platform`, `source_playlist_id`, `owner_uid`, `remote_signature`, `last_synced_song_signature`, `last_synced_at`)
                VALUES ('playlist-local-1', 'netease', '10001', '9527', 'sig', 'songs', 1700000001000)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6).apply {
            query(
                """
                SELECT `source_playlist_id`, `remote_signature`, `last_synced_song_signature`, `remote_order`
                FROM `playlist_remote_map`
                WHERE `playlist_id` = 'playlist-local-1'
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("10001", cursor.getString(0))
                assertEquals("sig", cursor.getString(1))
                assertEquals("songs", cursor.getString(2))
                assertTrue(cursor.isNull(3))
            }
            close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-5-6-test"
    }
}