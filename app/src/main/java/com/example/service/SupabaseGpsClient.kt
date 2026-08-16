package com.example.service

import android.location.Location
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object SupabaseGpsClient {
    private const val TAG = "SupabaseClient"
    const val SUPABASE_BASE_URL = "https://zgyhpjviwhckdpjmmdsx.supabase.co/rest/v1"
    const val SUPABASE_GPS_URL = "$SUPABASE_BASE_URL/gps_tracking"
    const val SUPABASE_INVOICES_URL = "$SUPABASE_BASE_URL/invoices"
    const val SUPABASE_CASH_DRAWER_URL = "$SUPABASE_BASE_URL/cash_drawer"
    const val SUPABASE_PAYMENTS_URL = "$SUPABASE_BASE_URL/payments"
    const val SUPABASE_ALERTS_URL = "$SUPABASE_BASE_URL/alerts"
    const val SUPABASE_VISIT_PROOFS_URL = "$SUPABASE_BASE_URL/visit_proofs"

    // Batching Buffer state (TraceOps Pattern)
    private var lastGpsFlushTime = 0L
    private const val FLUSH_INTERVAL_MS = 20_000L

    // Default Supabase Anon Key
    var apiKey: String = "sb_publishable_6qD62iUDo8v6lXJzA2SGng_6ows5wxG"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private fun getIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault())
        return sdf.format(Date())
    }

    /**
     * Sends real-time GPS coordinates to Supabase (gps_tracking table)
     */
        /**
     * Sends an emergency Panic / Incident Alert to Supabase (alerts table)
     * Broadcasts in real-time to the office dashboard.
     */
    suspend fun sendEmergencyAlert(
        routeCode: String,
        collectorName: String,
        alertType: String,
        location: Location?,
        batteryPct: Int = 100,
        notes: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        val nowIso = getIsoTimestamp()
        try {
            val speedKmh = if (location?.hasSpeed() == true) (location.speed * 3.6f).toInt() else 0
            val payload = JSONObject().apply {
                put("route_code", routeCode)
                put("collector_name", collectorName)
                put("alert_type", alertType)
                put("latitude", location?.latitude ?: 10.9878)
                put("longitude", location?.longitude ?: -74.7889)
                put("battery", "$batteryPct%")
                put("speed", "$speedKmh km/h")
                put("status", "ACTIVA")
                put("notes", notes)
                put("created_at", nowIso)
            }.toString()

            val request = Request.Builder()
                .url(SUPABASE_ALERTS_URL)
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val success = response.isSuccessful || response.code in 200..299
                Log.w(TAG, "🚨 ALERTA DE EMERGENCIA ($alertType) transmitida a Supabase: HTTP ${response.code} (Success: $success)")
                success
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando alerta de emergencia: ${e.message}")
            false
        }
    }

    /**
     * Sends real-time GPS coordinates to Supabase with Adaptive Batching (TraceOps Pattern)
     */
    suspend fun sendGpsLocation(
        routeCode: String,
        location: Location,
        forceImmediate: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val timeSinceLastFlush = now - lastGpsFlushTime

        // Buffer/Throttle: If moving, throttle to FLUSH_INTERVAL_MS unless forced
        if (!forceImmediate && timeSinceLastFlush < FLUSH_INTERVAL_MS) {
            return@withContext true
        }

        try {
            lastGpsFlushTime = now
            val speedKmh = (if (location.hasSpeed()) location.speed * 3.6f else 0f).toInt()
            val speedFormatted = "$speedKmh km/h"

            val payload = JSONObject().apply {
                put("route_code", routeCode)
                put("lat", location.latitude)
                put("lng", location.longitude)
                put("speed", speedFormatted)
                put("last_sync", "En vivo")
            }.toString()

            val requestBody = payload.toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(SUPABASE_GPS_URL)
                .post(requestBody)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .addHeader("Content-Type", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val code = response.code
                if (response.isSuccessful) {
                    Log.d(TAG, "Supabase GPS synced successfully (HTTP $code) for route: $routeCode")
                    true
                } else {
                    Log.w(TAG, "Supabase GPS sync HTTP $code: ${response.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Supabase GPS sync failed: ${e.message}")
            false
        }
    }

    /**
     * Atomically records a payment in Supabase (payments table).
     * PostgreSQL trigger handles deducting invoice balance and updating cash drawer atomically.
     */
    suspend fun recordPaymentToSupabase(
        loanId: Long,
        clientId: Long,
        clientName: String,
        amount: Double,
        quotaNumber: Int,
        totalPaid: Double,
        remainingBalance: Double,
        paidQuotas: Int,
        totalQuotas: Int,
        paymentMethod: String,
        notes: String,
        latitude: Double?,
        longitude: Double?,
        routeCode: String = "001",
        distanceToClientMeters: Double = 0.0,
        isOnSite: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        val nowIso = getIsoTimestamp()
        val invoiceIdStr = "F-$loanId"

        try {
            val paymentPayload = JSONObject().apply {
                put("invoice_id", invoiceIdStr)
                put("client_id", clientId.toString())
                put("client_name", clientName)
                put("route_code", routeCode)
                put("amount", amount)
                put("payment_method", paymentMethod.uppercase())
                put("quota_number", quotaNumber)
                if (latitude != null) put("collected_lat", latitude)
                if (longitude != null) put("collected_lng", longitude)
                put("distance_to_client_meters", distanceToClientMeters)
                put("is_on_site", isOnSite)
                put("collected_by", "COBRADOR")
                put("notes", notes)
                put("created_at", nowIso)
            }.toString()

            val request = Request.Builder()
                .url(SUPABASE_PAYMENTS_URL)
                .post(paymentPayload.toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val success = response.isSuccessful || response.code in 200..299
                Log.d(TAG, "Supabase atomic payment insert HTTP ${response.code} (Success: $success)")
                success
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error posting atomic payment to Supabase: ${e.message}")
            false
        }
    }

    suspend fun fetchActiveInvoicesForRoute(routeCode: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "$SUPABASE_INVOICES_URL?route_code=eq.$routeCode&status=eq.ACTIVA"
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    Log.d(TAG, "Supabase active invoices fetched successfully for route $routeCode: ${body?.take(200)}")
                    body
                } else {
                    Log.w(TAG, "Supabase fetch invoices HTTP ${response.code}: ${response.body?.string()}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching active invoices from Supabase: ${e.message}")
            null
        }
    }

    /**
     * Records a Proof of Visit (POD) when client does not pay (Absent, Refusal, Promise)
     */
    suspend fun recordVisitProofToSupabase(
        loanId: Long,
        clientId: Long,
        clientName: String,
        routeCode: String,
        visitStatus: String,
        notes: String,
        promiseDate: String?,
        photoBase64: String?,
        latitude: Double?,
        longitude: Double?,
        distanceToClientMeters: Double = 0.0,
        isOnSite: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        val nowIso = getIsoTimestamp()
        val invoiceIdStr = "F-$loanId"

        try {
            val payload = JSONObject().apply {
                put("invoice_id", invoiceIdStr)
                put("client_id", clientId.toString())
                put("client_name", clientName)
                put("route_code", routeCode)
                put("visit_status", visitStatus)
                put("notes", notes)
                if (!promiseDate.isNullOrEmpty()) put("promise_date", promiseDate)
                if (!photoBase64.isNullOrEmpty()) put("photo_url", photoBase64)
                if (latitude != null) put("collected_lat", latitude)
                if (longitude != null) put("collected_lng", longitude)
                put("distance_to_client_meters", distanceToClientMeters)
                put("is_on_site", isOnSite)
                put("created_at", nowIso)
            }.toString()

            val request = Request.Builder()
                .url(SUPABASE_VISIT_PROOFS_URL)
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val success = response.isSuccessful || response.code in 200..299
                Log.d(TAG, "📸 Evidencia de Visita ($visitStatus) registrada en Supabase: HTTP ${response.code} (Success: $success)")
                success
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando evidencia de visita: ${e.message}")
            false
        }
    }

}
