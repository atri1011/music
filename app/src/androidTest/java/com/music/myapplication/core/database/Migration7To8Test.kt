package com.music.myapplication.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration7To8Test {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migrate7To8AddsRequestIdColumnWithDefaultValue() {
        context.deleteDatabase(TEST_DB)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(7) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
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
                                    `progress_percent` INTEGER NOT NULL DEFAULT 0,
                                    `failure_reason` TEXT NOT NULL DEFAULT '',
                                    `download_status` TEXT NOT NULL DEFAULT 'downloading',
                                    `downloaded_at` INTEGER NOT NULL DEFAULT 0,
                                    PRIMARY KEY(`song_id`, `platform`)
                                )
                                """.trimIndent()
                            )
                            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_downloaded_status` ON `downloaded_tracks` (`download_status`)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_downloaded_at` ON `downloaded_tracks` (`downloaded_at`)")
                            db.execSQL(
                                """
                                INSERT INTO `downloaded_tracks`
                                (`song_id`, `platform`, `title`, `artist`, `album`, `cover_url`, `duration_ms`, `file_path`, `file_size_bytes`, `quality`, `progress_percent`, `failure_reason`, `download_status`, `downloaded_at`)
                                VALUES ('song-1', 'qq', '晴天', '周杰伦', '叶惠美', '', 258000, '', 0, '320k', 17, '已暂停', 'failed', 1700000000000)
                                """.trimIndent()
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) = Unit
                    }
                )
                .build()
        )

        val database = helper.writableDatabase
        MIGRATION_7_8.migrate(database)

        database.query(
            """
            SELECT `song_id`, `request_id`, `progress_percent`, `failure_reason`
            FROM `downloaded_tracks`
            WHERE `song_id` = 'song-1'
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("song-1", cursor.getString(0))
            assertEquals("", cursor.getString(1))
            assertEquals(17, cursor.getInt(2))
            assertEquals("已暂停", cursor.getString(3))
        }

        helper.close()
    }

    private companion object {
        const val TEST_DB = "migration-7-8-test"
    }
}
