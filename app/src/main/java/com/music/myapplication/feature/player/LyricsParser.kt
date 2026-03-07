package com.music.myapplication.feature.player

import com.music.myapplication.domain.model.LyricLine

object LyricsParser {

    private val TIME_REGEX = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})]""")

    fun parse(lrcText: String): List<LyricLine> {
        if (lrcText.isBlank()) return emptyList()
        val lines = mutableListOf<LyricLine>()
        lrcText.lines().forEach { line ->
            val matches = TIME_REGEX.findAll(line)
            val text = TIME_REGEX.replace(line, "").trim()
            if (text.isBlank()) return@forEach
            matches.forEach { match ->
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val ms = match.groupValues[3].let { raw ->
                    val v = raw.toLongOrNull() ?: 0L
                    if (raw.length == 2) v * 10 else v
                }
                lines.add(LyricLine(timeMs = min * 60000 + sec * 1000 + ms, text = text))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    fun parseMerged(originalLrc: String, translationLrc: String?): List<LyricLine> {
        val originalLines = parse(originalLrc)
        if (translationLrc.isNullOrBlank()) return originalLines

        val translationLines = parse(translationLrc)
        val translationMap = translationLines.associateBy { it.timeMs }

        return originalLines.map { line ->
            val transLine = translationMap[line.timeMs]
            if (transLine != null) line.copy(translation = transLine.text) else line
        }
    }

    fun findCurrentIndex(lyrics: List<LyricLine>, positionMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        var result = 0
        for (i in lyrics.indices) {
            if (lyrics[i].timeMs <= positionMs) result = i
            else break
        }
        return result
    }
}
