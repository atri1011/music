package com.music.myapplication.di

import com.music.myapplication.BuildConfig
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.core.network.interceptor.ApiKeyInterceptor
import com.music.myapplication.core.network.retrofit.JkApi
import com.music.myapplication.core.network.retrofit.NeteaseCloudApiEnhancedApi
import com.music.myapplication.core.network.interceptor.UserAgentInterceptor
import com.music.myapplication.core.network.retrofit.TuneHubApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class JkApiRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NeteaseCloudApiEnhancedRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://tunehub.sayqz.com/api/"
    private const val JKAPI_BASE_URL = "https://jkapi.com/"
    private const val NETEASE_CLOUD_API_ENHANCED_BASE_URL = "https://localhost/"
    private const val TIMEOUT_SECONDS = 15L

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(preferences: PlayerPreferences): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(UserAgentInterceptor())
        .addInterceptor(ApiKeyInterceptor(preferences, BuildConfig.TUNEHUB_API_KEY))
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        })
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideTuneHubApi(retrofit: Retrofit): TuneHubApi =
        retrofit.create(TuneHubApi::class.java)

    @Provides
    @Singleton
    @JkApiRetrofit
    fun provideJkApiRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(JKAPI_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideJkApi(@JkApiRetrofit retrofit: Retrofit): JkApi =
        retrofit.create(JkApi::class.java)

    @Provides
    @Singleton
    @NeteaseCloudApiEnhancedRetrofit
    fun provideNeteaseCloudApiEnhancedRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit = Retrofit.Builder()
        .baseUrl(NETEASE_CLOUD_API_ENHANCED_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideNeteaseCloudApiEnhancedApi(
        @NeteaseCloudApiEnhancedRetrofit retrofit: Retrofit
    ): NeteaseCloudApiEnhancedApi = retrofit.create(NeteaseCloudApiEnhancedApi::class.java)
}
