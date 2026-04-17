package com.music.myapplication.feature.player.state

val sleepTimerCountdownOptions = listOf(15, 30, 45, 60, 90)

fun SleepTimerState.statusText(): String = when (mode) {
    SleepTimerMode.COUNTDOWN -> "剩余 ${remainingMinutes} 分钟后暂停"
    SleepTimerMode.AFTER_CURRENT_TRACK -> "播完当前歌曲后暂停"
    SleepTimerMode.OFF -> ""
}