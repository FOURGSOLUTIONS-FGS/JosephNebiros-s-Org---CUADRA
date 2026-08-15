package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

object SupabaseConfig {
    const val BASE_URL = "https://zgyhpjviwhckdpjmmdsx.supabase.co/rest/v1/"
    const val API_KEY = "sb_publishable_6qD62iUDo8v6lXJzA2SGng_6ows5wxG"
}

class SupabaseAuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("apikey", SupabaseConfig.API_KEY)
            .addHeader("Authorization", "Bearer " + SupabaseConfig.API_KEY)
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=merge-duplicates")
            .build()
        return chain.proceed(request)
    }
}

@JsonClass(generateAdapter = true)
data class GpsLocationDto(
    @Json(name = "route_code") val route_code: String,
    @Json(name = "lat") val lat: Double,
    @Json(name = "lng") val lng: Double,
    @Json(name = "speed") val speed: String,
    @Json(name = "last_sync") val last_sync: String = "En vivo"
)

@JsonClass(generateAdapter = true)
data class ActivityLogDto(
    @Json(name = "type") val type: String,
    @Json(name = "icon") val icon: String,
    @Json(name = "title") val title: String,
    @Json(name = "detail") val detail: String
)

interface SupabaseApi {
    @POST("gps_tracking")
    suspend fun sendGpsLocation(@Body location: GpsLocationDto): retrofit2.Response<Unit>

    @POST("activity_log")
    suspend fun sendActivityLog(@Body log: ActivityLogDto): retrofit2.Response<Unit>
}

object RetrofitClient {
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(SupabaseAuthInterceptor())
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val supabaseApi: SupabaseApi by lazy {
        Retrofit.Builder()
            .baseUrl(SupabaseConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(SupabaseApi::class.java)
    }
}
