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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `playlist_remote_map` (
                `playlist_id` TEXT NOT NULL,
                `source_platform` TEXT NOT NULL,
                `source_playlist_id` TEXT NOT NULL,
                `owner_uid` TEXT NOT NULL,
                `last_synced_at` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`playlist_id`),
                FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`playlist_id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `idx_playlist_remote_map_source_unique`
            ON `playlist_remote_map` (`source_platform`, `source_playlist_id`, `owner_uid`)
            """.trimIndent()
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE `playlist_remote_map`
            ADD COLUMN `remote_signature` TEXT NOT NULL DEFAULT ''
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE `playlist_remote_map`
            ADD COLUMN `last_synced_song_signature` TEXT NOT NULL DEFAULT ''
            """.trimIndent()
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE `playlist_remote_map`
            ADD COLUMN `remote_order` INTEGER
            """.trimIndent()
        )
    }
}
