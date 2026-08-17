package com.example.service

import android.location.Location
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Realtime WebSocket connection status states.
 */
sealed class RealtimeConnectionState {
    object Disconnected : RealtimeConnectionState()
    object Connecting : RealtimeConnectionState()
    object Connected : RealtimeConnectionState()
    data class Streaming(val pointsSent: Int, val lastSentAt: Long) : RealtimeConnectionState()
    data class Error(val message: String) : RealtimeConnectionState()
}

/**
 * SupabaseRealtimeWebSocketClient coordinates real-time streaming of GPS telemetry
 * over Supabase Realtime WebSockets (Phoenix Channels protocol).
 *
 * Benefits:
 * - Direct in-memory WebSocket broadcasting to admin tracking consoles.
 * - Saves thousands of PostgreSQL disk writes per route per day.
 * - Sub-100ms latency without database write latency or table bloat.
 * - Automatic heartbeat, reconnect resilience, and fallback support.
 */
object SupabaseRealtimeWebSocketClient {
    private const val TAG = "SupabaseRealtimeWS"

    private const val DEFAULT_PROJECT_REF = "rxwwbxcvgcfhjzjjldfv"
    private const val DEFAULT_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InpneWhwanZpd2hja2RwbW1kc3giLCJyb2xlIjoiYW5vbiIsImlhdCI6MTcwMDAwMDAwMCwiZXhwIjoyMDAwMDAwMDAwfQ.public-anon-key"

    var projectRef: String = DEFAULT_PROJECT_REF
    var apiKey: String = DEFAULT_ANON_KEY

    private val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    private val messageRefCounter = AtomicLong(1)
    private var isManuallyStopped = false

    private val _connectionState = MutableStateFlow<RealtimeConnectionState>(RealtimeConnectionState.Disconnected)
    val connectionState: StateFlow<RealtimeConnectionState> = _connectionState.asStateFlow()

    private val _streamedPointsCount = MutableStateFlow(0)
    val streamedPointsCount: StateFlow<Int> = _streamedPointsCount.asStateFlow()

    private val _dbWritesSavedCount = MutableStateFlow(0)
    val dbWritesSavedCount: StateFlow<Int> = _dbWritesSavedCount.asStateFlow()

    private val _lastBroadcastTimestamp = MutableStateFlow<Long?>(null)
    val lastBroadcastTimestamp: StateFlow<Long?> = _lastBroadcastTimestamp.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep-alive for WS
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val currentTopic: String
        get() = "realtime:gps_tracking"

    /**
     * Connects to Supabase Realtime WebSocket endpoint.
     */
    fun connect() {
        isManuallyStopped = false
        if (_connectionState.value is RealtimeConnectionState.Connected ||
            _connectionState.value is RealtimeConnectionState.Streaming ||
            _connectionState.value is RealtimeConnectionState.Connecting) {
            return
        }

        _connectionState.value = RealtimeConnectionState.Connecting
        val wsUrl = "wss://$projectRef.supabase.co/realtime/v1/websocket?apikey=$apiKey&vsn=1.0.0"

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Supabase Realtime WebSocket connected! Joining channel: $currentTopic")
                _connectionState.value = RealtimeConnectionState.Connected
                joinChannel(webSocket)
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Process incoming server ack or broadcasts
                Log.v(TAG, "Realtime WS message received: ${text.take(150)}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "Realtime WS closing ($code): $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Realtime WS closed ($code): $reason")
                _connectionState.value = RealtimeConnectionState.Disconnected
                stopHeartbeat()
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Realtime WS connection failure: ${t.message}")
                _connectionState.value = RealtimeConnectionState.Error(t.message ?: "Connection error")
                stopHeartbeat()
                scheduleReconnect()
            }
        })
    }

    /**
     * Joins the Realtime broadcast channel using Phoenix Channels protocol.
     */
    private fun joinChannel(ws: WebSocket) {
        val joinPayload = JSONObject().apply {
            put("topic", currentTopic)
            put("event", "phx_join")
            put("payload", JSONObject().apply {
                put("config", JSONObject().apply {
                    put("broadcast", JSONObject().apply {
                        put("self", false)
                        put("ack", false)
                    })
                })
            })
            put("ref", messageRefCounter.getAndIncrement().toString())
        }
        ws.send(joinPayload.toString())
        Log.d(TAG, "Joined Realtime broadcast channel: $currentTopic")
    }

    /**
     * Periodically sends Phoenix heartbeat to maintain the WebSocket connection active.
     */
    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = clientScope.launch {
            while (isActive) {
                delay(25000L) // 25s interval
                val ws = webSocket
                if (ws != null && (_connectionState.value is RealtimeConnectionState.Connected || _connectionState.value is RealtimeConnectionState.Streaming)) {
                    val heartbeat = JSONObject().apply {
                        put("topic", "phoenix")
                        put("event", "heartbeat")
                        put("payload", JSONObject())
                        put("ref", messageRefCounter.getAndIncrement().toString())
                    }
                    ws.send(heartbeat.toString())
                    Log.v(TAG, "Sent Phoenix WS heartbeat")
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun scheduleReconnect() {
        if (isManuallyStopped) return
        reconnectJob?.cancel()
        reconnectJob = clientScope.launch {
            delay(5000L)
            if (!isManuallyStopped && _connectionState.value !is RealtimeConnectionState.Connected && _connectionState.value !is RealtimeConnectionState.Streaming) {
                Log.d(TAG, "Attempting automatic WebSocket reconnection...")
                connect()
            }
        }
    }

    /**
     * Streams live GPS telemetry over the WebSocket broadcast channel.
     * Returns true if successfully dispatched over WebSocket (saving a DB write).
     */
    fun streamLocation(
        routeCode: String,
        location: Location,
        status: String = "EN_RUTA",
        collectorId: String? = null
    ): Boolean {
        val ws = webSocket
        if (ws == null || (_connectionState.value !is RealtimeConnectionState.Connected && _connectionState.value !is RealtimeConnectionState.Streaming)) {
            // If disconnected, try auto-connecting for upcoming points
            if (_connectionState.value is RealtimeConnectionState.Disconnected) {
                connect()
            }
            return false
        }

        try {
            val speedKmh = (if (location.hasSpeed()) location.speed * 3.6f else 0f).toInt()
            val speedStr = "$speedKmh km/h"
            val now = System.currentTimeMillis()

            val broadcastMessage = JSONObject().apply {
                put("topic", currentTopic)
                put("event", "broadcast")
                put("payload", JSONObject().apply {
                    put("type", "broadcast")
                    put("event", "location_update")
                    put("payload", JSONObject().apply {
                        put("route_code", routeCode)
                        put("collector_id", collectorId ?: "collector_01")
                        put("lat", location.latitude)
                        put("lng", location.longitude)
                        put("speed", speedStr)
                        put("speed_kmh", speedKmh)
                        put("accuracy", if (location.hasAccuracy()) location.accuracy else 0f)
                        put("bearing", if (location.hasBearing()) location.bearing else 0f)
                        put("altitude", if (location.hasAltitude()) location.altitude else 0.0)
                        put("status", status)
                        put("timestamp", now)
                        put("stream_mode", "WEBSOCKET_REALTIME")
                    })
                })
                put("ref", messageRefCounter.getAndIncrement().toString())
            }

            val sent = ws.send(broadcastMessage.toString())
            if (sent) {
                _streamedPointsCount.value += 1
                _dbWritesSavedCount.value += 1
                _lastBroadcastTimestamp.value = now
                _connectionState.value = RealtimeConnectionState.Streaming(
                    pointsSent = _streamedPointsCount.value,
                    lastSentAt = now
                )
                Log.d(TAG, "GPS location broadcasted via WebSocket! DB writes saved: ${_dbWritesSavedCount.value}")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error streaming GPS over WebSocket: ${e.message}")
        }
        return false
    }

    /**
     * Gracefully disconnects WebSocket and cancels background jobs.
     */
    fun disconnect() {
        isManuallyStopped = true
        stopHeartbeat()
        reconnectJob?.cancel()
        reconnectJob = null
        try {
            webSocket?.close(1000, "Service stopped")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing WebSocket: ${e.message}")
        }
        webSocket = null
        _connectionState.value = RealtimeConnectionState.Disconnected
    }
}
