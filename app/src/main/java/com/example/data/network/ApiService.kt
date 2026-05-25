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

interface OpenAiApiService {
    @POST("v1/chat/completions")
    suspend fun generateChatCompletion(
        @Header("Authorization") authHeader: String,
        @Body request: OpenAiChatRequest
    ): OpenAiChatResponse
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

    private val yahooOkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(headersInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    private val geminiOkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private val openAiOkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private val yahooRetrofit = Retrofit.Builder()
        .baseUrl("https://query1.finance.yahoo.com/")
        .client(yahooOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val geminiRetrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(geminiOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val deepseekRetrofit = Retrofit.Builder()
        .baseUrl("https://api.deepseek.com/")
        .client(openAiOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val kimiRetrofit = Retrofit.Builder()
        .baseUrl("https://api.moonshot.cn/")
        .client(openAiOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val yahooService: YahooFinanceService by lazy {
        yahooRetrofit.create(YahooFinanceService::class.java)
    }

    val geminiService: GeminiApiService by lazy {
        geminiRetrofit.create(GeminiApiService::class.java)
    }

    val deepseekService: OpenAiApiService by lazy {
        deepseekRetrofit.create(OpenAiApiService::class.java)
    }

    val kimiService: OpenAiApiService by lazy {
        kimiRetrofit.create(OpenAiApiService::class.java)
    }

    suspend fun generateContentSafe(
        model: String,
        apiKey: String,
        request: GeminiContentRequest
    ): GeminiContentResponse {
        val modelsToTry = mutableListOf<String>()
        modelsToTry.add(model)
        
        val defaultModels = listOf(
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-1.5-flash-8b",
            "gemini-2.0-flash-exp",
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-1.5-pro",
            "gemini-flash-latest",
            "gemini-2.5-flash-latest"
        )
        for (m in defaultModels) {
            if (m != model) {
                modelsToTry.add(m)
            }
        }

        var lastException: Exception? = null
        for (candidate in modelsToTry) {
            try {
                if (candidate != model) {
                    android.util.Log.w("RetrofitClient", "Attempting fallback to Gemini model: $candidate")
                }
                return geminiService.generateContent(candidate, apiKey, request)
            } catch (e: retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                val detailedMsg = "HttpException ${e.code()} (${e.message()}): $errorBody"
                android.util.Log.e("RetrofitClient", "Gemini call HTTP error with model $candidate: $detailedMsg")
                lastException = Exception(detailedMsg, e)
            } catch (e: Exception) {
                android.util.Log.e("RetrofitClient", "Gemini call failed with model $candidate: ${e.message}")
                lastException = e
            }
        }
        throw lastException ?: Exception("All Gemini models in the fallback chain failed.")
    }
}
