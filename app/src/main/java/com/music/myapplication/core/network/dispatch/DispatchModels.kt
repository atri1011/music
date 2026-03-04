package com.music.myapplication.core.network.dispatch

import com.music.myapplication.data.remote.dto.MethodsDataDto
import com.music.myapplication.data.remote.dto.TransformRuleDto

data class DispatchRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val transform: TransformRuleDto? = null
)

data class DispatchTemplate(
    val platform: String,
    val function: String,
    val data: MethodsDataDto,
    val cachedAt: Long = System.currentTimeMillis()
)
