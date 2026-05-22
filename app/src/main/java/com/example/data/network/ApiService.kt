package com.example.data.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface YahooFinanceService {
    @GET("v8/finance/chart/{ticker}")
    suspend fun getChartData(
        @Path("ticker") ticker: String,
        @Query("range") range: String = "5d",
        @Query("interval") interval: String = "1d"
    ): YahooFinanceResponse
}

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiContentRequest
    ): GeminiContentResponse
}

object RetrofitClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val headersInterceptor = Interceptor { chain ->
        val original = chain.request()
        val request = original.newBuilder()
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
            .header("Accept", "application/json")
            .build()
        chain.proceed(request)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(headersInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    private val yahooRetrofit = Retrofit.Builder()
        .baseUrl("https://query1.finance.yahoo.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val geminiRetrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val yahooService: YahooFinanceService by lazy {
        yahooRetrofit.create(YahooFinanceService::class.java)
    }

    val geminiService: GeminiApiService by lazy {
        geminiRetrofit.create(GeminiApiService::class.java)
    }

    suspend fun generateContentSafe(
        model: String,
        apiKey: String,
        request: GeminiContentRequest
    ): GeminiContentResponse {
        try {
            return geminiService.generateContent(model, apiKey, request)
        } catch (e: Exception) {
            android.util.Log.e("RetrofitClient", "Gemini call failed with model $model: ${e.message}")
            if (model == "gemini-3.5-flash") {
                android.util.Log.w("RetrofitClient", "Attempting fallback to gemini-2.5-flash...")
                try {
                    return geminiService.generateContent("gemini-2.5-flash", apiKey, request)
                } catch (inner: Exception) {
                    android.util.Log.e("RetrofitClient", "Fallback to gemini-2.5-flash also failed: ${inner.message}")
                    throw inner
                }
            }
            throw e
        }
    }
}
