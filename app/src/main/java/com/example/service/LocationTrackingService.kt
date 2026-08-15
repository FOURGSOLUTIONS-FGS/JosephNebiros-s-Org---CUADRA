package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.db.AppDatabase
import com.example.data.remote.SupabaseClient
import com.example.data.remote.SupabaseGpsDto
import com.example.data.repository.CobranzaRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class LocationTrackingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var tickerJob: Job? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var repository: CobranzaRepository
    private var currentSessionId: String = ""
    private var lastRecordedLocation: Location? = null

    companion object {
        const val TAG = "LocationTrackingService"
        const val CHANNEL_ID = "channel_location_tracking"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"

        // Reactive location and tracking state shared with ViewModel and UI
        private val _currentLocation = MutableStateFlow<Location?>(null)
        val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

        private val _isTracking = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

        private val _currentSession = MutableStateFlow<String?>(null)
        val currentSession: StateFlow<String?> = _currentSession.asStateFlow()

        private val _pointsRecordedCount = MutableStateFlow(0)
        val pointsRecordedCount: StateFlow<Int> = _pointsRecordedCount.asStateFlow()

        private val _totalDistanceMeters = MutableStateFlow(0.0)
        val totalDistanceMeters: StateFlow<Double> = _totalDistanceMeters.asStateFlow()

        private val _currentSpeedKmh = MutableStateFlow(0f)
        val currentSpeedKmh: StateFlow<Float> = _currentSpeedKmh.asStateFlow()

        private val _currentBearing = MutableStateFlow(0f)
        val currentBearing: StateFlow<Float> = _currentBearing.asStateFlow()

        private val _accuracyMeters = MutableStateFlow(0f)
        val accuracyMeters: StateFlow<Float> = _accuracyMeters.asStateFlow()

        private val _elapsedSeconds = MutableStateFlow(0L)
        val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

        // Cloud real-time sync state (Supabase / Vercel Admin)
        private val _cloudSyncStatus = MutableStateFlow("En espera")
        val cloudSyncStatus: StateFlow<String> = _cloudSyncStatus.asStateFlow()

        private val _cloudSyncSuccessCount = MutableStateFlow(0)
        val cloudSyncSuccessCount: StateFlow<Int> = _cloudSyncSuccessCount.asStateFlow()

        private val _lastCloudSyncTimestamp = MutableStateFlow<Long?>(null)
        val lastCloudSyncTimestamp: StateFlow<Long?> = _lastCloudSyncTimestamp.asStateFlow()

        fun startService(context: Context, sessionId: String? = null) {
            val sid = sessionId ?: ("RUTA_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()))
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_ID, sid)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getDatabase(applicationContext)
        repository = CobranzaRepository(db)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        createNotificationChannel()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    handleNewLocation(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                    ?: ("RUTA_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()))
                startTracking(sessionId)
            }
            ACTION_STOP -> {
                stopTracking()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Seguimiento GPS de Ruta en Tiempo Real",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra información en tiempo real de la ruta y velocidad en primer plano"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hours = _elapsedSeconds.value / 3600
        val minutes = (_elapsedSeconds.value % 3600) / 60
        val seconds = _elapsedSeconds.value % 60
        val timeStr = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        val distKm = _totalDistanceMeters.value / 1000.0

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛰️ GPS Activo • $timeStr")
            .setContentText(statusText)
            .setSubText("${String.format(Locale.US, "%.2f", distKm)} km • ${_pointsRecordedCount.value} pts")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener Ruta", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun startTracking(sessionId: String) {
        currentSessionId = sessionId
        _currentSession.value = sessionId
        _isTracking.value = true
        _pointsRecordedCount.value = 0
        _totalDistanceMeters.value = 0.0
        _currentSpeedKmh.value = 0f
        _elapsedSeconds.value = 0L
        lastRecordedLocation = null
        lastCloudSyncLocation = null
        lastCloudSyncTimeMs = 0L

        val notification = buildNotification("Ruta iniciada ($sessionId) • Registrando recorrido")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            repository.startNewTrackingSession(sessionId)
        }

        // Timer job for elapsed seconds & periodic notification sync
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive && _isTracking.value) {
                delay(1000L)
                _elapsedSeconds.value += 1
                if (_elapsedSeconds.value % 10 == 0L) {
                    val distKm = _totalDistanceMeters.value / 1000.0
                    val speed = _currentSpeedKmh.value
                    val updatedNotification = buildNotification(
                        "Distancia: ${String.format(Locale.US, "%.2f", distKm)} km • Vel: ${String.format(Locale.US, "%.1f", speed)} km/h"
                    )
                    val manager = getSystemService(NotificationManager::class.java)
                    manager?.notify(NOTIFICATION_ID, updatedNotification)
                }
            }
        }

        try {
            // Immediate last known location lookup
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && _currentLocation.value == null) {
                    handleNewLocation(loc)
                }
            }

            // Continuous high-precision location updates
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2500L)
                .setMinUpdateIntervalMillis(1500L)
                .setMinUpdateDistanceMeters(2.5f)
                .setWaitForAccurateLocation(false)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates started for session $sessionId")
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing: ${e.message}")
        }
    }

    private fun handleNewLocation(location: Location) {
        val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }

        if (isMock) {
            Log.w(TAG, "⚠️ ALERTA DE SEGURIDAD: Ubicación GPS simulada detectada (Mock Provider). Coordenada descartada.")
            _cloudSyncStatus.value = "⚠️ GPS simulado detectado"
            return
        }

        _currentLocation.value = location
        _accuracyMeters.value = if (location.hasAccuracy()) location.accuracy else 0f
        if (location.hasBearing()) {
            _currentBearing.value = location.bearing
        }
        val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
        _currentSpeedKmh.value = speedKmh

        // Calculate delta distance from previous point
        lastRecordedLocation?.let { prev ->
            val deltaMeters = calculateDistanceMeters(
                prev.latitude, prev.longitude,
                location.latitude, location.longitude
            )
            if (deltaMeters in 1.0..5000.0) { // filter out GPS jumps
                _totalDistanceMeters.value += deltaMeters
            }
        }
        lastRecordedLocation = location
        _pointsRecordedCount.value += 1

        // 1. Record point locally in Room Database
        serviceScope.launch {
            try {
                repository.recordRoutePoint(
                    sessionId = currentSessionId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    speed = if (location.hasSpeed()) location.speed else 0f,
                    altitude = if (location.hasAltitude()) location.altitude else 0.0,
                    accuracy = if (location.hasAccuracy()) location.accuracy else 0f
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error saving route point locally: ${e.message}")
            }
        }

        // 2. Transmit coordinates in real-time to Supabase (gps_tracking table)
        // Adaptive Throttling: send immediately if moved >= 2.5m OR after 8s heartbeat when stationary
        val now = System.currentTimeMillis()
        val prevSyncLoc = lastCloudSyncLocation
        val distSinceLastSync = if (prevSyncLoc != null) {
            calculateDistanceMeters(prevSyncLoc.latitude, prevSyncLoc.longitude, location.latitude, location.longitude)
        } else {
            Double.MAX_VALUE
        }

        val shouldSyncCloud = prevSyncLoc == null || distSinceLastSync >= 2.5 || (now - lastCloudSyncTimeMs) >= 8000L

        if (shouldSyncCloud) {
            lastCloudSyncLocation = location
            lastCloudSyncTimeMs = now

            val routeCode = currentSessionId.ifEmpty { "RUTA_BARRANQUILLA_01" }
            val speedIntKmh = speedKmh.toInt()
            val speedFormatted = "$speedIntKmh km/h"

            serviceScope.launch {
            try {
                val gpsDto = SupabaseGpsDto(
                    routeCode = routeCode,
                    lat = location.latitude,
                    lng = location.longitude,
                    speed = speedFormatted,
                    lastSync = "En vivo"
                )
                val response = SupabaseClient.apiService.postGpsTracking(gpsDto)
                if (response.isSuccessful || response.code() in 200..299) {
                    _cloudSyncSuccessCount.value += 1
                    _lastCloudSyncTimestamp.value = System.currentTimeMillis()
                    _cloudSyncStatus.value = "Sincronizado Supabase en vivo"
                } else {
                    _cloudSyncStatus.value = "Transmisión enviada"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error transmitting GPS via Retrofit, trying direct client: ${e.message}")
                try {
                    val isSuccess = SupabaseGpsClient.sendGpsLocation(routeCode, location)
                    if (isSuccess) {
                        _cloudSyncSuccessCount.value += 1
                        _lastCloudSyncTimestamp.value = System.currentTimeMillis()
                        _cloudSyncStatus.value = "Sincronizado Supabase en vivo"
                    } else {
                        _cloudSyncStatus.value = "Transmisión enviada"
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "Error in Supabase GPS fallback: ${e2.message}")
                    _cloudSyncStatus.value = "Error conexión Supabase"
                }
            }
        }
    }
}

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun stopTracking() {
        tickerJob?.cancel()
        tickerJob = null

        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates: ${e.message}")
        }

        serviceScope.launch {
            if (currentSessionId.isNotEmpty()) {
                repository.stopTrackingSession(currentSessionId)
            }
        }

        _isTracking.value = false
        _currentSession.value = null
        _currentSpeedKmh.value = 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
