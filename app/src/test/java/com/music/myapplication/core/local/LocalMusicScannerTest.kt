package com.music.myapplication.core.local

import com.music.myapplication.core.database.entity.LocalTrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMusicScannerTest {

    @Test
    fun `buildLocalTrackSyncPlan only upserts changed tracks and deletes removed ids`() {
        val existing = listOf(
            entity(id = 1L, title = "A"),
            entity(id = 2L, title = "B"),
            entity(id = 3L, title = "C")
        )
        val scanned = listOf(
            entity(id = 1L, title = "A"),
            entity(id = 2L, title = "B (Remastered)"),
            entity(id = 4L, title = "D")
        )

        val plan = buildLocalTrackSyncPlan(existing, scanned)

        assertEquals(listOf(2L, 4L), plan.upserts.map { it.mediaStoreId })
        assertEquals(listOf(3L), plan.deleteIds)
    }

    @Test
    fun `buildLocalTrackSyncPlan is empty when scanned data matches existing data`() {
        val existing = listOf(
            entity(id = 1L, title = "A"),
            entity(id = 2L, title = "B")
        )

        val plan = buildLocalTrackSyncPlan(existing, existing)

        assertTrue(plan.upserts.isEmpty())
        assertTrue(plan.deleteIds.isEmpty())
    }

    private fun entity(id: Long, title: String) = LocalTrackEntity(
        mediaStoreId = id,
        title = title,
        artist = "Artist",
        album = "Album",
        durationMs = 120_000L,
        filePath = "content://media/external/audio/media/$id",
        fileSizeBytes = 1024L,
        mimeType = "audio/mpeg",
        addedAt = 1_700_000_000_000L
    )
}
