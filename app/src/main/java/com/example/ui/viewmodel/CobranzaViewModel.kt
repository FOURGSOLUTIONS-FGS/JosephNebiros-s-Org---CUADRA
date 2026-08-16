package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.AuthManager
import com.example.auth.CollectorUser
import com.example.data.db.AppDatabase
import com.example.data.model.ClientEntity
import com.example.data.model.ClientWithActiveLoan
import com.example.data.model.ExpenseEntity
import com.example.data.model.LoanEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.ReminderEntity
import com.example.data.model.RoutePointEntity
import com.example.data.model.TrackingSessionEntity
import com.example.data.repository.CobranzaRepository
import com.example.data.repository.SyncManager
import com.example.data.repository.SyncResult
import com.example.data.repository.SyncState
import com.example.service.LocationTrackingService
import com.example.util.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DailySummaryState(
    val totalExpectedToday: Double = 0.0,
    val totalCollectedToday: Double = 0.0,
    val totalDisbursedToday: Double = 0.0,
    val totalExpensesToday: Double = 0.0,
    val netCashInHand: Double = 0.0,
    val totalClientsCount: Int = 0,
    val collectedClientsCount: Int = 0,
    val pendingClientsCount: Int = 0,
    val progressPercentage: Float = 0f
)

class CobranzaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CobranzaRepository
    val networkMonitor: NetworkMonitor = NetworkMonitor.getInstance(application)
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    val syncManager: SyncManager?

    private val _pendingOfflineSyncCount = MutableStateFlow(0)
    val pendingOfflineSyncCount: StateFlow<Int>

    val syncState: StateFlow<SyncState>

    val isTrackingActive: StateFlow<Boolean> = LocationTrackingService.isTracking
    val currentLocation: StateFlow<Location?> = LocationTrackingService.currentLocation
    val pointsCount: StateFlow<Int> = LocationTrackingService.pointsRecordedCount
    val currentSessionId: StateFlow<String?> = LocationTrackingService.currentSession
    val liveDistanceMeters: StateFlow<Double> = LocationTrackingService.totalDistanceMeters
    val currentSpeedKmh: StateFlow<Float> = LocationTrackingService.currentSpeedKmh
    val currentBearing: StateFlow<Float> = LocationTrackingService.currentBearing
    val accuracyMeters: StateFlow<Float> = LocationTrackingService.accuracyMeters
    val elapsedSeconds: StateFlow<Long> = LocationTrackingService.elapsedSeconds
    val cloudSyncStatus: StateFlow<String> = LocationTrackingService.cloudSyncStatus
    val cloudSyncSuccessCount: StateFlow<Int> = LocationTrackingService.cloudSyncSuccessCount
    val lastCloudSyncTimestamp: StateFlow<Long?> = LocationTrackingService.lastCloudSyncTimestamp

    init {
        val database = AppDatabase.getDatabase(application)
        repository = CobranzaRepository(database, networkMonitor)
        syncManager = repository.syncManager

        pendingOfflineSyncCount = syncManager?.unsyncedPaymentsCount ?: _pendingOfflineSyncCount.asStateFlow()
        syncState = syncManager?.syncState ?: MutableStateFlow<SyncState>(SyncState.Idle()).asStateFlow()

        refreshPendingSyncCount()

        // Ensure Barranquilla coordinates and select initial Barranquilla route session
        viewModelScope.launch {
            repository.getAllTrackingSessions().collect { sessions ->
                if (_selectedSessionId.value == null && sessions.isNotEmpty()) {
                    _selectedSessionId.value = sessions.first().sessionId
                }
            }
        }

        viewModelScope.launch {
            repository.getAllClients().collect { clientList ->
                clientList.forEach { client ->
                    val lat = client.latitude
                    val lng = client.longitude
                    // If client coordinates are missing or outside Barranquilla bounding box
                    if (lat == null || lng == null || lat > 12.0 || lat < 10.0 || lng < -76.0 || lng > -73.0) {
                        val (newLat, newLng, newAddress) = when (client.visitOrder) {
                            1 -> Triple(10.9885, -74.7932, "Cra. 43 # 54-20, Barrio Boston")
                            2 -> Triple(10.9612, -74.7865, "Calle 45 (Murillo) # 21-35, San José")
                            3 -> Triple(10.9780, -74.7790, "Cra. 38 # 36-12, Paseo Bolívar")
                            4 -> Triple(10.9995, -74.8015, "Cra. 54 # 68-80, Barrio El Prado")
                            5 -> Triple(10.9525, -74.7810, "Calle 45 # 14-08, La Victoria")
                            else -> Triple(11.0080, -74.8180, "Calle 84 # 47-15, Alto Prado")
                        }
                        repository.updateClient(
                            client.copy(
                                latitude = newLat,
                                longitude = newLng,
                                address = if (client.address.contains("Zona") || client.address.isEmpty()) newAddress else client.address
                            )
                        )
                    }
                }
            }
        }
    }

    fun refreshPendingSyncCount() {
        viewModelScope.launch {
            val count = syncManager?.getPendingCount() ?: repository.getUnsyncedPaymentsCount()
            _pendingOfflineSyncCount.value = count
            networkMonitor.updatePendingSyncCount(count)
        }
    }

    fun syncPendingOfflineData(onResult: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val count = if (syncManager != null) {
                val res = syncManager.syncUnsyncedChanges()
                res.paymentsSynced
            } else {
                repository.syncPendingOfflinePayments()
            }
            refreshPendingSyncCount()
            onResult(count)
        }
    }

    fun triggerManualSync(routeCode: String = "RUTA_01", onResult: (SyncResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = syncManager?.fullSync(routeCode) ?: run {
                val count = repository.syncPendingOfflinePayments()
                SyncResult(success = true, paymentsSynced = count)
            }
            refreshPendingSyncCount()
            onResult(result)
        }
    }

    val dailyRouteList: StateFlow<List<ClientWithActiveLoan>> =
        repository.getDailyRouteClientsWithLoans()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val allClients: StateFlow<List<ClientEntity>> =
        repository.getAllClients()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val allPaymentsHistory: StateFlow<List<PaymentEntity>> =
        repository.getAllPayments()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val todayPayments: StateFlow<List<PaymentEntity>> =
        repository.getTodayPayments()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val todayExpenses: StateFlow<List<ExpenseEntity>> =
        repository.getTodayExpenses()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val allReminders: StateFlow<List<ReminderEntity>> =
        repository.getAllReminders()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val pendingReminders: StateFlow<List<ReminderEntity>> =
        repository.getPendingReminders()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val trackingSessions: StateFlow<List<TrackingSessionEntity>> =
        repository.getAllTrackingSessions()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    private val _selectedSessionId = MutableStateFlow<String?>(null)
    val selectedSessionId: StateFlow<String?> = _selectedSessionId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedSessionPoints: StateFlow<List<RoutePointEntity>> =
        _selectedSessionId.flatMapLatest { sessionId ->
            if (sessionId != null) {
                repository.getRoutePointsForSession(sessionId)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val dailySummary: StateFlow<DailySummaryState> =
        combine(
            dailyRouteList,
            todayPayments,
            todayExpenses
        ) { routeList, payments, expenses ->
            val totalExpected = routeList.sumOf { it.activeLoan?.quotaAmount ?: 0.0 }
            val totalCollected = payments.sumOf { it.amount }
            val totalExpenses = expenses.sumOf { it.amount }
            val netCash = totalCollected - totalExpenses

            val collectedCount = routeList.count { it.isCollectedToday }
            val totalCount = routeList.size
            val pendingCount = totalCount - collectedCount

            val progress = if (totalExpected > 0) {
                ((totalCollected / totalExpected) * 100f).coerceIn(0.0, 100.0).toFloat()
            } else 0f

            DailySummaryState(
                totalExpectedToday = totalExpected,
                totalCollectedToday = totalCollected,
                totalDisbursedToday = 0.0,
                totalExpensesToday = totalExpenses,
                netCashInHand = netCash,
                totalClientsCount = totalCount,
                collectedClientsCount = collectedCount,
                pendingClientsCount = pendingCount,
                progressPercentage = progress
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DailySummaryState()
        )

    fun selectSession(sessionId: String?) {
        _selectedSessionId.value = sessionId
    }

    fun startRouteTracking(context: Context) {
        val sid = "RUTA_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        _selectedSessionId.value = sid
        LocationTrackingService.startService(context, sid)
    }

    fun stopRouteTracking(context: Context) {
        LocationTrackingService.stopService(context)
    }

    fun registerPayment(
        client: ClientEntity,
        loan: LoanEntity,
        amount: Double,
        notes: String = "",
        paymentMethod: String = "EFECTIVO",
        photoUri: String? = null,
        onSuccess: ((Long) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val loc = currentLocation.value
            val lat = loc?.latitude ?: client.latitude
            val lng = loc?.longitude ?: client.longitude
            val nextQuotaNumber = loan.paidQuotas + 1

            val paymentId = repository.recordPayment(
                loanId = loan.id,
                clientId = client.id,
                amount = amount,
                quotaNumber = nextQuotaNumber,
                notes = notes,
                paymentMethod = paymentMethod,
                photoUri = photoUri,
                latitude = lat,
                longitude = lng
            )
            refreshPendingSyncCount()
            onSuccess?.invoke(paymentId)
        }
    }

    fun updateClientPhoto(clientId: Long, photoUri: String) {
        viewModelScope.launch {
            repository.updateClientPhoto(clientId, photoUri)
        }
    }

    fun createReminder(
        clientId: Long,
        clientName: String,
        title: String,
        dueTimeFormatted: String,
        notes: String = "",
        priority: String = "NORMAL"
    ) {
        viewModelScope.launch {
            repository.createReminder(
                clientId = clientId,
                clientName = clientName,
                title = title,
                dueTimeFormatted = dueTimeFormatted,
                notes = notes,
                priority = priority
            )
        }
    }

    fun setReminderCompleted(reminderId: Long, completed: Boolean) {
        viewModelScope.launch {
            repository.setReminderCompleted(reminderId, completed)
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            repository.deleteReminder(reminder)
        }
    }

    fun getPaymentsForClient(clientId: Long) = repository.getPaymentsForClient(clientId)

    fun createClient(
        name: String,
        aliasOrBusiness: String,
        phone: String,
        address: String,
        latitude: Double?,
        longitude: Double?,
        notes: String
    ) {
        viewModelScope.launch {
            val newClient = ClientEntity(
                name = name.trim(),
                aliasOrBusiness = aliasOrBusiness.trim(),
                phone = phone.trim(),
                address = address.trim(),
                latitude = latitude,
                longitude = longitude,
                notes = notes.trim(),
                visitOrder = allClients.value.size + 1
            )
            repository.saveClient(newClient)
        }
    }

    fun updateClient(client: ClientEntity) {
        viewModelScope.launch {
            repository.updateClient(client)
        }
    }

    fun assignCurrentGpsToClient(client: ClientEntity) {
        val loc = currentLocation.value ?: return
        viewModelScope.launch {
            repository.updateClient(
                client.copy(
                    latitude = loc.latitude,
                    longitude = loc.longitude
                )
            )
        }
    }

    fun deleteClient(client: ClientEntity) {
        viewModelScope.launch {
            repository.deleteClient(client)
        }
    }

    fun createLoan(
        clientId: Long,
        amountBorrowed: Double,
        interestRate: Double = 20.0,
        totalQuotas: Int = 24,
        frequency: String = "DIARIO"
    ) {
        viewModelScope.launch {
            val totalToPay = amountBorrowed * (1.0 + (interestRate / 100.0))
            val quotaAmount = (totalToPay / totalQuotas).let { Math.round(it * 100.0) / 100.0 }
            val daysInTerm = if (frequency == "LUNES_SABADO") (totalQuotas * 7) / 6 else totalQuotas

            val loan = LoanEntity(
                clientId = clientId,
                amountBorrowed = amountBorrowed,
                interestRate = interestRate,
                totalToPay = totalToPay,
                totalPaid = 0.0,
                remainingBalance = totalToPay,
                quotaAmount = quotaAmount,
                totalQuotas = totalQuotas,
                paidQuotas = 0,
                startDate = System.currentTimeMillis(),
                dueDate = System.currentTimeMillis() + (daysInTerm.toLong() * 24 * 60 * 60 * 1000),
                frequency = frequency,
                status = "ACTIVE"
            )
            repository.createLoan(loan)
        }
    }

    fun addExpense(concept: String, amount: Double, category: String = "RUTA") {
        viewModelScope.launch {
            repository.addExpense(concept, amount, category)
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    fun generatePaymentReceiptText(client: ClientEntity, loan: LoanEntity, amountPaid: Double): String {
        val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val curDate = df.format(Date())

        val balanceAfter = (loan.remainingBalance - amountPaid).coerceAtLeast(0.0)
        val quotasAfter = loan.paidQuotas + 1

        return """
            🧾 COMPROBANTE DE PAGO
            --------------------------------
            Cliente: ${client.name}
            Negocio: ${client.aliasOrBusiness.ifEmpty { "N/A" }}
            Dirección: ${client.address.ifEmpty { "Barranquilla" }}
            Fecha: $curDate
            --------------------------------
            Monto Cobrado: ${com.example.util.CurrencyUtils.format(amountPaid)}
            Cuota: $quotasAfter de ${loan.totalQuotas}
            Saldo Restante: ${com.example.util.CurrencyUtils.format(balanceAfter)}
            Estado Préstamo: ${if (balanceAfter <= 0.01) "PAGADO TOTAL" else "AL DÍA"}
            --------------------------------
            ¡Gracias por su puntual pago en Barranquilla!
        """.trimIndent()
    }

    fun generateDailyReportText(): String {
        val df = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val curDate = df.format(Date())
        val summary = dailySummary.value

        return """
            📊 REPORTE DE CIERRE DIARIO - BARRANQUILLA
            Fecha: $curDate
            --------------------------------
            Total Clientes en Ruta: ${summary.totalClientsCount}
            Clientes Cobrados: ${summary.collectedClientsCount}
            Clientes Pendientes: ${summary.pendingClientsCount}
            Efectividad: ${String.format(Locale.getDefault(), "%.1f", summary.progressPercentage)}%
            --------------------------------
            Esperado Hoy: ${com.example.util.CurrencyUtils.format(summary.totalExpectedToday)}
            Cobrado Total: ${com.example.util.CurrencyUtils.format(summary.totalCollectedToday)}
            Gastos de Ruta: ${com.example.util.CurrencyUtils.format(summary.totalExpensesToday)}
            --------------------------------
            💵 EFECTIVO NETO EN MANO: ${com.example.util.CurrencyUtils.format(summary.netCashInHand)}
        """.trimIndent()
    }

    private val _isSyncingRoute = MutableStateFlow(false)
    val isSyncingRoute: StateFlow<Boolean> = _isSyncingRoute.asStateFlow()

    private val _currentUser = MutableStateFlow<CollectorUser?>(AuthManager.getSavedUser(application))
    val currentUser: StateFlow<CollectorUser?> = _currentUser.asStateFlow()

    fun signInWithGoogle(
        context: Context,
        onResult: (Boolean, CollectorUser?, String?) -> Unit
    ) {
        viewModelScope.launch {
            val result = AuthManager.signInWithGoogle(context)
            result.onSuccess { user ->
                _currentUser.value = user
                onResult(true, user, null)
            }.onFailure { error ->
                onResult(false, null, error.message)
            }
        }
    }

    fun loginWithEmailPassword(
        context: Context,
        email: String,
        pass: String,
        onResult: (Boolean, CollectorUser?, String?) -> Unit
    ) {
        val result = AuthManager.loginWithEmailPassword(context, email, pass)
        result.onSuccess { user ->
            _currentUser.value = user
            onResult(true, user, null)
        }.onFailure { error ->
            onResult(false, null, error.message)
        }
    }

    fun loginQuick(user: CollectorUser) {
        AuthManager.saveUser(getApplication(), user)
        _currentUser.value = user
    }

    fun logout() {
        AuthManager.clearUser(getApplication())
        _currentUser.value = null
    }

    // Realtime WebSocket & Delta Sync observability
    val realtimeWsState = com.example.service.SupabaseRealtimeWebSocketClient.connectionState
    val dbWritesSavedCount = com.example.service.SupabaseRealtimeWebSocketClient.dbWritesSavedCount
    val wsStreamedPointsCount = com.example.service.SupabaseRealtimeWebSocketClient.streamedPointsCount

    fun syncRouteFromSupabase(
        routeCode: String = "RUTA_BARRANQUILLA_01",
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isSyncingRoute.value = true
            val mgr = syncManager
            if (mgr != null) {
                val result = mgr.deltaSync(routeCode)
                _isSyncingRoute.value = false
                if (result.success) {
                    onComplete(true, "Delta Sync exitoso: ${result.paymentsSynced} cobros subidos, ${result.invoicesMerged} facturas sincronizadas.")
                } else {
                    onComplete(false, "Modo sin conexión: ${result.errors.firstOrNull() ?: "Error de sincronización"}")
                }
            } else {
                val result = repository.syncRouteDeltaFromSupabase(routeCode)
                _isSyncingRoute.value = false
                result.onSuccess { count ->
                    onComplete(true, "Delta Sync exitoso: $count facturas procesadas.")
                }.onFailure { err ->
                    onComplete(false, "Modo sin conexión: ${err.message}")
                }
            }
        }
    }

    fun triggerDeltaSync(
        routeCode: String = "RUTA_BARRANQUILLA_01",
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isSyncingRoute.value = true
            val mgr = syncManager
            if (mgr != null) {
                val result = mgr.deltaSync(routeCode)
                _isSyncingRoute.value = false
                if (result.success) {
                    onComplete(true, "Delta Sync completado: ${result.paymentsSynced} pagos enviados, ${result.invoicesMerged} registros actualizados.")
                } else {
                    onComplete(false, "Fallo en Delta Sync: ${result.errors.firstOrNull() ?: "Sin conexión"}")
                }
            } else {
                val result = repository.syncRouteDeltaFromSupabase(routeCode)
                _isSyncingRoute.value = false
                result.onSuccess { count ->
                    onComplete(true, "Delta Sync completado: $count registros actualizados.")
                }.onFailure { err ->
                    onComplete(false, "Fallo en Delta Sync: ${err.message}")
                }
            }
        }
    }

    fun triggerFullSync(
        routeCode: String = "RUTA_BARRANQUILLA_01",
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isSyncingRoute.value = true
            val mgr = syncManager
            if (mgr != null) {
                val result = mgr.fullSync(routeCode)
                _isSyncingRoute.value = false
                if (result.success) {
                    onComplete(true, "Sincronización completa: ${result.paymentsSynced} cobros subidos, ${result.invoicesMerged} facturas recargadas.")
                } else {
                    onComplete(false, "Fallo en sincronización: ${result.errors.firstOrNull() ?: "Error de conexión"}")
                }
            } else {
                val result = repository.syncRouteFromSupabase(routeCode)
                _isSyncingRoute.value = false
                result.onSuccess { count ->
                    onComplete(true, "Sincronización completa: $count facturas descargadas.")
                }.onFailure { err ->
                    onComplete(false, "Fallo en sincronización: ${err.message}")
                }
            }
        }
    }
}

