package com.music.myapplication.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `recent_plays` ADD COLUMN `play_count` INTEGER NOT NULL DEFAULT 1"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
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
            CREATE TABLE IF NOT EXISTS `local_tracks` (
                `media_store_id` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `artist` TEXT NOT NULL,
                `album` TEXT NOT NULL DEFAULT '',
                `duration_ms` INTEGER NOT NULL DEFAULT 0,
                `file_path` TEXT NOT NULL DEFAULT '',
                `file_size_bytes` INTEGER NOT NULL DEFAULT 0,
                `mime_type` TEXT NOT NULL DEFAULT '',
                `added_at` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`media_store_id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_local_tracks_artist` ON `local_tracks` (`artist`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_local_tracks_album` ON `local_tracks` (`album`)")
    }
}
