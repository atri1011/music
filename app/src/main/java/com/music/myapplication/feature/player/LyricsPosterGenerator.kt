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
    PAPER("留白", com.music.myapplication.feature.player.poster.LyricsPosterTemplate.PAPER),
    VINYL("黑胶", com.music.myapplication.feature.player.poster.LyricsPosterTemplate.VINYL),
    MIDNIGHT("午夜", com.music.myapplication.feature.player.poster.LyricsPosterTemplate.MIDNIGHT),
    SUNSET("日落", com.music.myapplication.feature.player.poster.LyricsPosterTemplate.SUNSET),
    NEON("霓虹", com.music.myapplication.feature.player.poster.LyricsPosterTemplate.NEON),
    MONO("黑白", com.music.myapplication.feature.player.poster.LyricsPosterTemplate.MONO),
    FILM("胶片", com.music.myapplication.feature.player.poster.LyricsPosterTemplate.FILM),
    OCEAN("海盐", com.music.myapplication.feature.player.poster.LyricsPosterTemplate.OCEAN),
    FOREST("森屿", com.music.myapplication.feature.player.poster.LyricsPosterTemplate.FOREST),
    CLASSIC("唱片", com.music.myapplication.feature.player.poster.LyricsPosterTemplate.CLASSIC),
    MINIMAL("极简", com.music.myapplication.feature.player.poster.LyricsPosterTemplate.MINIMAL),
    CUSTOM("自定义", com.music.myapplication.feature.player.poster.LyricsPosterTemplate.CUSTOM)
}

object LyricsPosterGenerator {

    suspend fun generate(
        context: Context,
        track: Track,
        lyricLine: LyricLine,
        template: LyricsPosterTemplate,
        customBackgroundUri: String? = null,
        width: Int = 1080,
        height: Int = 1920
    ): Bitmap = com.music.myapplication.feature.player.poster.LyricsPosterGenerator.generate(
        context = context,
        track = track,
        lyricLine = lyricLine,
        template = template.delegate,
        customBackgroundUri = customBackgroundUri,
        width = width,
        height = height
    )
}
