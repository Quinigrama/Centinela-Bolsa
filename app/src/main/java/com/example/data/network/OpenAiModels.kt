package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OpenAiChatRequest(
    @Json(name = "model") val model: String,
    @Json(name = "messages") val messages: List<OpenAiMessage>,
    @Json(name = "temperature") val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiMessage(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class OpenAiChatResponse(
    @Json(name = "id") val id: String? = null,
    @Json(name = "choices") val choices: List<OpenAiChoice>? = null,
    @Json(name = "error") val error: OpenAiError? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiChoice(
    @Json(name = "index") val index: Int? = null,
    @Json(name = "message") val message: OpenAiMessage? = null,
    @Json(name = "finish_reason") val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiError(
    @Json(name = "message") val message: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "code") val code: String? = null
)
