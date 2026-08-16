package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.QueryMap
import java.util.concurrent.TimeUnit

/**
 * Data Transfer Objects for Supabase PostgREST tables
 */
@JsonClass(generateAdapter = true)
data class SupabaseInvoiceDto(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "loan_id") val loanId: Long? = null,
    @Json(name = "client_id") val clientId: Long? = null,
    @Json(name = "client_name") val clientName: String? = null,
    @Json(name = "route_code") val routeCode: String? = null,
    @Json(name = "amount_paid") val amountPaid: Double? = null,
    @Json(name = "total_paid") val totalPaid: Double? = null,
    @Json(name = "remaining_balance") val remainingBalance: Double? = null,
    @Json(name = "quota_number") val quotaNumber: Int? = null,
    @Json(name = "paid_quotas") val paidQuotas: Int? = null,
    @Json(name = "total_quotas") val totalQuotas: Int? = null,
    @Json(name = "quota_amount") val quotaAmount: Double? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "payment_method") val paymentMethod: String? = null,
    @Json(name = "notes") val notes: String? = null,
    @Json(name = "collected_lat") val collectedLat: Double? = null,
    @Json(name = "collected_lng") val collectedLng: Double? = null,
    @Json(name = "last_payment_date") val lastPaymentDate: String? = null,
    @Json(name = "last_sync") val lastSync: String? = "En vivo"
)

@JsonClass(generateAdapter = true)
data class SupabaseCashDrawerDto(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "loan_id") val loanId: Long? = null,
    @Json(name = "client_id") val clientId: Long? = null,
    @Json(name = "client_name") val clientName: String? = null,
    @Json(name = "amount") val amount: Double,
    @Json(name = "type") val type: String = "RECAUDO",
    @Json(name = "concept") val concept: String? = null,
    @Json(name = "payment_method") val paymentMethod: String = "EFECTIVO",
    @Json(name = "collected_lat") val collectedLat: Double? = null,
    @Json(name = "collected_lng") val collectedLng: Double? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "status") val status: String = "COMPLETADO"
)

@JsonClass(generateAdapter = true)
data class SupabaseGpsDto(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "route_code") val routeCode: String,
    @Json(name = "lat") val lat: Double,
    @Json(name = "lng") val lng: Double,
    @Json(name = "speed") val speed: String? = "0 km/h",
    @Json(name = "last_sync") val lastSync: String = "En vivo",
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseClientDto(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "name") val name: String,
    @Json(name = "alias_or_business") val aliasOrBusiness: String? = null,
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "address") val address: String? = null,
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null,
    @Json(name = "notes") val notes: String? = null,
    @Json(name = "visit_order") val visitOrder: Int = 1,
    @Json(name = "is_active") val isActive: Boolean = true
)

/**
 * Retrofit interface for Supabase REST API (PostgREST)
 */
interface SupabaseApiService {

    // --- Invoices ---
    @GET("invoices")
    suspend fun getActiveInvoicesForRoute(
        @Query("route_code") routeCodeFilter: String, // e.g. "eq.RUTA_01"
        @Query("status") statusFilter: String = "eq.ACTIVA"
    ): Response<List<SupabaseInvoiceDto>>

    @GET("invoices")
    suspend fun getInvoices(
        @QueryMap filters: Map<String, String>
    ): Response<List<SupabaseInvoiceDto>>

    @Headers("Prefer: resolution=merge-duplicates")
    @POST("invoices")
    suspend fun upsertInvoice(
        @Body invoice: SupabaseInvoiceDto
    ): Response<Void>

    @Headers("Prefer: resolution=merge-duplicates")
    @POST("invoices")
    suspend fun upsertInvoices(
        @Body invoices: List<SupabaseInvoiceDto>
    ): Response<Void>

    @PATCH("invoices")
    suspend fun updateInvoice(
        @Query("id") idFilter: String, // e.g. "eq.123"
        @Body invoice: SupabaseInvoiceDto
    ): Response<Void>

    // --- Cash Drawer (Recaudos & Gastos) ---
    @GET("cash_drawer")
    suspend fun getCashDrawerEntries(
        @QueryMap filters: Map<String, String> = emptyMap()
    ): Response<List<SupabaseCashDrawerDto>>

    @POST("cash_drawer")
    suspend fun recordCashEntry(
        @Body entry: SupabaseCashDrawerDto
    ): Response<Void>

    // --- GPS Tracking ---
    @Headers("Prefer: resolution=merge-duplicates")
    @POST("gps_tracking")
    suspend fun postGpsTracking(
        @Body gps: SupabaseGpsDto
    ): Response<Void>

    @GET("gps_tracking")
    suspend fun getGpsTracking(
        @Query("route_code") routeCodeFilter: String
    ): Response<List<SupabaseGpsDto>>

    // --- Clients ---
    @GET("clients")
    suspend fun getClients(
        @QueryMap filters: Map<String, String> = emptyMap()
    ): Response<List<SupabaseClientDto>>

    @Headers("Prefer: resolution=merge-duplicates")
    @POST("clients")
    suspend fun upsertClient(
        @Body client: SupabaseClientDto
    ): Response<Void>
}

/**
 * Singleton client configuring Retrofit and OkHttp for Supabase REST API
 */
object SupabaseClient {

    private const val DEFAULT_BASE_URL = "https://zgyhpjviwhckdpjmmdsx.supabase.co/rest/v1/"
    private const val DEFAULT_API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InpneWhwanZpd2hja2RwbW1kc3giLCJyb2xlIjoiYW5vbiIsImlhdCI6MTcwMDAwMDAwMCwiZXhwIjoyMDAwMDAwMDAwfQ.public-anon-key"

    var baseUrl: String = DEFAULT_BASE_URL
        private set

    var apiKey: String = DEFAULT_API_KEY
        private set

    fun configure(customBaseUrl: String? = null, customApiKey: String? = null) {
        if (!customBaseUrl.isNullOrBlank()) {
            baseUrl = if (customBaseUrl.endsWith("/")) customBaseUrl else "$customBaseUrl/"
        }
        if (!customApiKey.isNullOrBlank()) {
            apiKey = customApiKey
        }
        // Rebuild service instance on re-configuration
        _apiService = null
    }

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()
            .header("apikey", apiKey)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")

        chain.proceed(requestBuilder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private var _apiService: SupabaseApiService? = null

    val apiService: SupabaseApiService
        get() {
            return _apiService ?: synchronized(this) {
                _apiService ?: createRetrofitService().also { _apiService = it }
            }
        }

    private fun createRetrofitService(): SupabaseApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(SupabaseApiService::class.java)
    }
}
