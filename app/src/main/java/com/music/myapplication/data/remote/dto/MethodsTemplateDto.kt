package com.music.myapplication.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class MethodsTemplateDto(
    @SerialName("code") val code: Int = 0,
    @SerialName("msg") val msg: String = "",
    @SerialName("data") val data: MethodsDataDto? = null
)

@Serializable
data class MethodsDataDto(
    @SerialName("url") val url: String = "",
    @SerialName("method") val method: String = "GET",
    @SerialName("headers") val headers: Map<String, String> = emptyMap(),
    @SerialName("params") val params: Map<String, String> = emptyMap(),
    @SerialName("body") val body: JsonElement? = null,
    @SerialName("transform") val transform: JsonElement? = null
)

@Serializable
data class TransformRuleDto(
    @SerialName("root") val root: String = "",
    @SerialName("fields") val fields: Map<String, String> = emptyMap()
)
