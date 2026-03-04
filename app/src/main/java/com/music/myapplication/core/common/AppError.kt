package com.music.myapplication.core.common

sealed class AppError(
    open val message: String,
    open val cause: Throwable? = null
) {
    data class Network(
        override val message: String = "网络连接失败",
        override val cause: Throwable? = null,
        val code: Int? = null
    ) : AppError(message, cause)

    data class Api(
        override val message: String,
        override val cause: Throwable? = null,
        val code: Int = -1
    ) : AppError(message, cause)

    data class Template(
        override val message: String = "方法模板获取失败",
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class Parse(
        override val message: String = "数据解析失败",
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class Playback(
        override val message: String = "播放失败",
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class Database(
        override val message: String = "数据库操作失败",
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class Unknown(
        override val message: String = "未知错误",
        override val cause: Throwable? = null
    ) : AppError(message, cause)
}
