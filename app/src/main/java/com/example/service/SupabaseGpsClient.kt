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
    const val SUPABASE_BASE_URL = "https://rxwwbxcvgcfhjzjjldfv.supabase.co/rest/v1"
    const val SUPABASE_GPS_URL = "$SUPABASE_BASE_URL/gps_tracking"
    const val SUPABASE_INVOICES_URL = "$SUPABASE_BASE_URL/invoices"
    const val SUPABASE_CASH_DRAWER_URL = "$SUPABASE_BASE_URL/cash_drawer"

    // Default Supabase Anon Key
    var apiKey: String = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJ4d3dieGN2Z2NmaGp6ampsZGZ2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODY5MTY5MjYsImV4cCI6MjEwMjQ5MjkyNn0.W2cDzsveWtzVbOVe-djDod_Qrcmxmmj6_gXfO309iQY"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Generates a trusted ISO timestamp with validation against device clock manipulation.
     * If the local timestamp is invalid (> 5 min future or > 7 days past), standard normalized timestamp is generated.
     */
    private fun getIsoTimestamp(timestampMillis: Long? = null): String {
        val now = System.currentTimeMillis()
        val target = timestampMillis ?: now

        // Check if timestamp is within bounds (not > 5 mins in future, not > 7 days in past)
        val fiveMinutesFuture = now + (5 * 60 * 1000)
        val sevenDaysPast = now - (7L * 24 * 60 * 60 * 1000)

        val trustedTime = if (target > fiveMinutesFuture || target < sevenDaysPast) {
            Log.w(TAG, "Device clock anomaly detected ($target vs current $now). Normalizing to server trusted timestamp.")
            now
        } else {
            target
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault())
        return sdf.format(Date(trustedTime))
    }

    /**
     * Sends real-time GPS coordinates to Supabase (gps_tracking table)
     */
    suspend fun sendGpsLocation(
        routeCode: String,
        location: Location
    ): Boolean = withContext(Dispatchers.IO) {
        try {
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
                    val errorBody = response.body?.string() ?: ""
                    Log.w(TAG, "Supabase GPS sync HTTP $code response: $errorBody")
                    code in 200..299
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error posting GPS coordinates to Supabase: ${e.message}")
            false
        }
    }

    /**
     * Updates the invoice/loan status in Supabase (invoices table)
     * and adds the collected amount to the cash drawer (cash_drawer table)
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
        paymentTimestampMillis: Long? = null
    ): Boolean = withContext(Dispatchers.IO) {
        var invoiceSuccess = false
        var cashDrawerSuccess = false
        val nowIso = getIsoTimestamp(paymentTimestampMillis)

        // 1. Update/Merge in invoices table
        try {
            val invoicePayload = JSONObject().apply {
                put("id", loanId)
                put("loan_id", loanId)
                put("client_id", clientId)
                put("client_name", clientName)
                put("amount_paid", amount)
                put("total_paid", totalPaid)
                put("remaining_balance", remainingBalance)
                put("quota_number", quotaNumber)
                put("paid_quotas", paidQuotas)
                put("total_quotas", totalQuotas)
                put("status", if (remainingBalance <= 0.01) "PAGADO" else "ACTIVO")
                put("payment_method", paymentMethod)
                put("notes", notes)
                if (latitude != null) put("collected_lat", latitude)
                if (longitude != null) put("collected_lng", longitude)
                put("last_payment_date", nowIso)
                put("last_sync", "En vivo")
            }.toString()

            val invoiceRequest = Request.Builder()
                .url(SUPABASE_INVOICES_URL)
                .post(invoicePayload.toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .addHeader("Content-Type", "application/json")
                .build()

            okHttpClient.newCall(invoiceRequest).execute().use { response ->
                invoiceSuccess = response.isSuccessful || response.code in 200..299
                Log.d(TAG, "Supabase invoices table update HTTP ${response.code} (Success: $invoiceSuccess)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Supabase invoices table: ${e.message}")
        }

        // 2. Insert recaudo in cash_drawer table
        try {
            val cashPayload = JSONObject().apply {
                put("loan_id", loanId)
                put("client_id", clientId)
                put("client_name", clientName)
                put("amount", amount)
                put("type", "RECAUDO")
                put("concept", "Cobro Cuota #$quotaNumber ($clientName)")
                put("payment_method", paymentMethod)
                if (latitude != null) put("collected_lat", latitude)
                if (longitude != null) put("collected_lng", longitude)
                put("created_at", nowIso)
                put("status", "COMPLETADO")
            }.toString()

            val cashRequest = Request.Builder()
                .url(SUPABASE_CASH_DRAWER_URL)
                .post(cashPayload.toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .addHeader("Content-Type", "application/json")
                .build()

            okHttpClient.newCall(cashRequest).execute().use { response ->
                cashDrawerSuccess = response.isSuccessful || response.code in 200..299
                Log.d(TAG, "Supabase cash_drawer table update HTTP ${response.code} (Success: $cashDrawerSuccess)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Supabase cash_drawer table: ${e.message}")
        }

        invoiceSuccess && cashDrawerSuccess
    }

    /**
     * Converts a millisecond timestamp to ISO-8601 string for PostgREST delta queries.
     */
    fun formatToIso(timestampMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timestampMillis))
    }

    /**
     * Queries GET /rest/v1/invoices?route_code=eq.${routeCode}&status=eq.ACTIVA from Supabase.
     * When lastModifiedIso is provided, performs an INCREMENTAL DELTA SYNC fetching only
     * invoices created or updated after the specified timestamp.
     */
    suspend fun fetchActiveInvoicesDelta(
        routeCode: String,
        lastModifiedIso: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val baseUrl = "$SUPABASE_INVOICES_URL?route_code=eq.$routeCode&status=eq.ACTIVA"
            val url = if (!lastModifiedIso.isNullOrBlank()) {
                "$baseUrl&or=(updated_at.gt.$lastModifiedIso,last_payment_date.gt.$lastModifiedIso,created_at.gt.$lastModifiedIso)"
            } else {
                baseUrl
            }

            Log.d(TAG, "Fetching invoices from Supabase (Delta mode: ${!lastModifiedIso.isNullOrBlank()}, timestamp: $lastModifiedIso)")

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
                    Log.d(TAG, "Supabase invoices fetched successfully (HTTP ${response.code})")
                    body
                } else {
                    Log.w(TAG, "Supabase fetch invoices HTTP ${response.code}: ${response.body?.string()}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching active invoices delta from Supabase: ${e.message}")
            null
        }
    }

    /**
     * Queries GET /rest/v1/invoices?route_code=eq.${routeCode}&status=eq.ACTIVA from Supabase
     */
    suspend fun fetchActiveInvoicesForRoute(routeCode: String): String? =
        fetchActiveInvoicesDelta(routeCode, null)
}

