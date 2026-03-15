package com.music.myapplication.data.repository

internal fun String.isDigitsOnly(): Boolean = isNotEmpty() && all(Char::isDigit)
