package com.music.myapplication.feature.player

import android.content.Context
import android.graphics.Bitmap
import com.music.myapplication.domain.model.LyricLine
import com.music.myapplication.domain.model.Track

enum class LyricsPosterTemplate(
    val displayName: String,
    internal val delegate: com.music.myapplication.feature.player.poster.LyricsPosterTemplate
) {
    AURORA("流光", com.music.myapplication.feature.player.poster.LyricsPosterTemplate.AURORA),
    PAPER("留白", com.music.myapplication.feature.player.poster.LyricsPosterTemplate.PAPER)
}

object LyricsPosterGenerator {

    suspend fun generate(
        context: Context,
        track: Track,
        lyricLine: LyricLine,
        template: LyricsPosterTemplate,
        width: Int = 1080,
        height: Int = 1920
    ): Bitmap = com.music.myapplication.feature.player.poster.LyricsPosterGenerator.generate(
        context = context,
        track = track,
        lyricLine = lyricLine,
        template = template.delegate,
        width = width,
        height = height
    )
}
