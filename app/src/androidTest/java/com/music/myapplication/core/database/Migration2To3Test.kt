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
class Migration2To3Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate2To3PreservesExistingDataAndCreatesOfflineTables() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `favorites` (
                    `song_id` TEXT NOT NULL,
                    `platform` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `artist` TEXT NOT NULL,
                    `album` TEXT NOT NULL DEFAULT '',
                    `cover_url` TEXT NOT NULL DEFAULT '',
                    `duration_ms` INTEGER NOT NULL DEFAULT 0,
                    `added_at` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`song_id`, `platform`)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `recent_plays` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `song_id` TEXT NOT NULL,
                    `platform` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `artist` TEXT NOT NULL,
                    `cover_url` TEXT NOT NULL DEFAULT '',
                    `duration_ms` INTEGER NOT NULL DEFAULT 0,
                    `played_at` INTEGER NOT NULL DEFAULT 0,
                    `position_ms` INTEGER NOT NULL DEFAULT 0,
                    `play_count` INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
            execSQL(
                "CREATE INDEX IF NOT EXISTS `idx_recent_played_at` ON `recent_plays` (`played_at`)"
            )
            execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_recent_plays_song_id_platform` ON `recent_plays` (`song_id`, `platform`)"
            )
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
                "CREATE INDEX IF NOT EXISTS `idx_playlists_updated_at` ON `playlists` (`updated_at`)"
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playlist_songs` (
                    `playlist_id` TEXT NOT NULL,
                    `song_id` TEXT NOT NULL,
                    `platform` TEXT NOT NULL,
                    `order_in_playlist` INTEGER NOT NULL,
                    `added_at` INTEGER NOT NULL DEFAULT 0,
                    `title` TEXT NOT NULL,
                    `artist` TEXT NOT NULL,
                    `cover_url` TEXT NOT NULL DEFAULT '',
                    `duration_ms` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`playlist_id`, `song_id`, `platform`),
                    FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`playlist_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            execSQL(
                "CREATE INDEX IF NOT EXISTS `idx_playlist_songs_order` ON `playlist_songs` (`playlist_id`, `order_in_playlist`)"
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `lyrics_cache` (
                    `cache_key` TEXT NOT NULL,
                    `lyric_text` TEXT NOT NULL,
                    `source` TEXT NOT NULL DEFAULT '',
                    `updated_at` INTEGER NOT NULL DEFAULT 0,
                    `expires_at` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`cache_key`)
                )
                """.trimIndent()
            )
            execSQL(
                "CREATE INDEX IF NOT EXISTS `idx_lyrics_expires_at` ON `lyrics_cache` (`expires_at`)"
            )

            execSQL(
                "INSERT INTO `favorites` (`song_id`, `platform`, `title`, `artist`, `album`, `cover_url`, `duration_ms`, `added_at`) VALUES ('song-1', 'qq', '晴天', '周杰伦', '叶惠美', '', 240000, 1700000000000)"
            )
            execSQL(
                "INSERT INTO `recent_plays` (`song_id`, `platform`, `title`, `artist`, `cover_url`, `duration_ms`, `played_at`, `position_ms`, `play_count`) VALUES ('song-1', 'qq', '晴天', '周杰伦', '', 240000, 1700000000100, 12345, 3)"
            )
            execSQL(
                "INSERT INTO `playlists` (`playlist_id`, `name`, `cover_url`, `created_at`, `updated_at`) VALUES ('local-playlist', '本地歌单', '', 1700000000200, 1700000000200)"
            )
            execSQL(
                "INSERT INTO `playlist_songs` (`playlist_id`, `song_id`, `platform`, `order_in_playlist`, `added_at`, `title`, `artist`, `cover_url`, `duration_ms`) VALUES ('local-playlist', 'song-1', 'qq', 0, 1700000000300, '晴天', '周杰伦', '', 240000)"
            )
            execSQL(
                "INSERT INTO `lyrics_cache` (`cache_key`, `lyric_text`, `source`, `updated_at`, `expires_at`) VALUES ('qq:song-1', '歌词内容', 'test', 1700000000400, 2700000000400)"
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3).apply {
            query("SELECT COUNT(*) FROM `favorites`").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            query("SELECT COUNT(*) FROM `recent_plays`").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            query("SELECT COUNT(*) FROM `downloaded_tracks`").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            query("SELECT COUNT(*) FROM `local_tracks`").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-2-3-test"
    }
}
