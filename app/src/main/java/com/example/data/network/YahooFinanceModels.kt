package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class YahooFinanceResponse(
    @Json(name = "chart") val chart: ChartResponse
)

@JsonClass(generateAdapter = true)
data class ChartResponse(
    @Json(name = "result") val result: List<ChartResult>?,
    @Json(name = "error") val error: ChartError? = null
)

@JsonClass(generateAdapter = true)
data class ChartResult(
    @Json(name = "meta") val meta: ChartMeta,
    @Json(name = "timestamp") val timestamp: List<Long>?,
    @Json(name = "indicators") val indicators: Indicators? = null
)

@JsonClass(generateAdapter = true)
data class ChartMeta(
    @Json(name = "currency") val currency: String? = null,
    @Json(name = "symbol") val symbol: String,
    @Json(name = "regularMarketPrice") val regularMarketPrice: Double? = null,
    @Json(name = "chartPreviousClose") val chartPreviousClose: Double? = null,
    @Json(name = "regularMarketVolume") val regularMarketVolume: Long? = null,
    @Json(name = "longName") val longName: String? = null
)

@JsonClass(generateAdapter = true)
data class Indicators(
    @Json(name = "quote") val quote: List<QuoteData>? = null
)

@JsonClass(generateAdapter = true)
data class QuoteData(
    @Json(name = "close") val close: List<Double?>? = null,
    @Json(name = "volume") val volume: List<Long?>? = null,
    @Json(name = "high") val high: List<Double?>? = null,
    @Json(name = "low") val low: List<Double?>? = null
)

@JsonClass(generateAdapter = true)
data class ChartError(
    @Json(name = "code") val code: String? = null,
    @Json(name = "description") val description: String? = null
)
