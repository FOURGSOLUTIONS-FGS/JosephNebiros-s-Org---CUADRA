package com.example.data.repository

import android.util.Log
import com.example.data.db.AppDatabase
import com.example.data.model.ClientEntity
import com.example.data.model.LoanEntity
import com.example.data.model.PaymentEntity
import com.example.service.SupabaseGpsClient
import com.example.util.NetworkMonitor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sealed representation of current synchronization status.
 */
sealed class SyncState {
    data class Idle(
        val lastSyncedAt: Long? = null,
        val pendingCount: Int = 0
    ) : SyncState()

    data class Syncing(
        val totalToSync: Int,
        val processedCount: Int = 0,
        val currentStep: String = "Sincronizando con Supabase..."
    ) : SyncState()

    data class Success(
        val syncedCount: Int,
        val timestamp: Long = System.currentTimeMillis(),
        val message: String = "Sincronización completada con éxito"
    ) : SyncState()

    data class Error(
        val errorMessage: String,
        val pendingCount: Int,
        val timestamp: Long = System.currentTimeMillis()
    ) : SyncState()
}

/**
 * Summary metrics of a sync execution run.
 */
data class SyncResult(
    val success: Boolean,
    val paymentsSynced: Int = 0,
    val clientsSynced: Int = 0,
    val invoicesMerged: Int = 0,
    val errors: List<String> = emptyList(),
    val totalPendingRemaining: Int = 0
)

/**
 * SyncManager coordinates offline-first data synchronization between Room (SQLite)
 * and Supabase Cloud.
 *
 * It monitors device network status via NetworkMonitor and automatically initiates
 * synchronization whenever connectivity is restored or validated, ensuring full
 * data integrity and preventing data loss.
 */
class SyncManager(
    private val database: AppDatabase,
    private val networkMonitor: NetworkMonitor,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)
) {
    private val tag = "SyncManager"
    private val clientDao = database.clientDao()
    private val loanDao = database.loanDao()
    private val paymentDao = database.paymentDao()
    private val expenseDao = database.expenseDao()

    private val syncMutex = Mutex()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow<Long?>(null)
    val lastSyncTimestamp: StateFlow<Long?> = _lastSyncTimestamp.asStateFlow()

    private val _unsyncedPaymentsCount = MutableStateFlow(0)
    val unsyncedPaymentsCount: StateFlow<Int> = _unsyncedPaymentsCount.asStateFlow()

    init {
        // 1. Observe real-time count of unsynced payments from Room DAO
        coroutineScope.launch {
            paymentDao.getUnsyncedPaymentsCountFlow()
                .distinctUntilChanged()
                .collect { count ->
                    _unsyncedPaymentsCount.value = count
                    networkMonitor.updatePendingSyncCount(count)
                    if (_syncState.value is SyncState.Idle) {
                        _syncState.value = SyncState.Idle(
                            lastSyncedAt = _lastSyncTimestamp.value,
                            pendingCount = count
                        )
                    }
                }
        }

        // 2. Automatically trigger sync when network transitions from offline to online
        coroutineScope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                if (isOnline) {
                    val pending = paymentDao.getUnsyncedPaymentsCount()
                    if (pending > 0) {
                        Log.d(tag, "Internet connectivity restored! Found $pending unsynced payments in Room. Triggering auto-sync...")
                        syncUnsyncedChanges()
                    }
                }
            }
        }

        // 3. Register callback on NetworkMonitor as secondary trigger
        networkMonitor.setOnNetworkRestoredListener {
            coroutineScope.launch {
                Log.d(tag, "Network callback restored event received. Triggering auto-sync.")
                syncUnsyncedChanges()
            }
        }
    }

    /**
     * Checks if there are pending unsynced changes in Room.
     */
    suspend fun getPendingCount(): Int = withContext(ioDispatcher) {
        paymentDao.getUnsyncedPaymentsCount()
    }

    /**
     * Synchronizes all unsynced local Room payments to Supabase.
     * Uses Mutex to ensure thread safety and avoid duplicate execution.
     */
    suspend fun syncUnsyncedChanges(routeCode: String = "RUTA_01"): SyncResult = withContext(ioDispatcher) {
        if (!syncMutex.tryLock()) {
            Log.w(tag, "Sync is already in progress, skipping concurrent trigger.")
            val remaining = paymentDao.getUnsyncedPaymentsCount()
            return@withContext SyncResult(
                success = false,
                errors = listOf("Sincronización en curso"),
                totalPendingRemaining = remaining
            )
        }

        try {
            if (!networkMonitor.isOnline.value) {
                val pending = paymentDao.getUnsyncedPaymentsCount()
                Log.w(tag, "Cannot sync: Device is currently offline. $pending items remain safely stored in Room.")
                _syncState.value = SyncState.Idle(
                    lastSyncedAt = _lastSyncTimestamp.value,
                    pendingCount = pending
                )
                return@withContext SyncResult(
                    success = false,
                    errors = listOf("Sin conexión a internet"),
                    totalPendingRemaining = pending
                )
            }

            val unsyncedPayments = paymentDao.getUnsyncedPayments()
            val totalToSync = unsyncedPayments.size

            if (totalToSync == 0) {
                Log.d(tag, "No unsynced local changes found in Room. Everything is up to date.")
                _syncState.value = SyncState.Idle(
                    lastSyncedAt = _lastSyncTimestamp.value,
                    pendingCount = 0
                )
                return@withContext SyncResult(
                    success = true,
                    paymentsSynced = 0,
                    totalPendingRemaining = 0
                )
            }

            _syncState.value = SyncState.Syncing(
                totalToSync = totalToSync,
                processedCount = 0,
                currentStep = "Subiendo $totalToSync cobro(s) pendientes a Supabase..."
            )

            var syncedCount = 0
            val errors = mutableListOf<String>()

            for ((index, payment) in unsyncedPayments.withIndex()) {
                _syncState.value = SyncState.Syncing(
                    totalToSync = totalToSync,
                    processedCount = index + 1,
                    currentStep = "Sincronizando cobro ${index + 1} de $totalToSync..."
                )

                try {
                    val loan = loanDao.getLoanById(payment.loanId)
                    val client = clientDao.getClientById(payment.clientId)
                    val clientName = client?.name ?: "Cliente #${payment.clientId}"

                    val totalPaid = loan?.totalPaid ?: payment.amount
                    val remainingBalance = loan?.remainingBalance ?: 0.0
                    val paidQuotas = loan?.paidQuotas ?: payment.quotaNumber
                    val totalQuotas = loan?.totalQuotas ?: 24

                    val success = SupabaseGpsClient.recordPaymentToSupabase(
                        loanId = payment.loanId,
                        clientId = payment.clientId,
                        clientName = clientName,
                        amount = payment.amount,
                        quotaNumber = payment.quotaNumber,
                        totalPaid = totalPaid,
                        remainingBalance = remainingBalance,
                        paidQuotas = paidQuotas,
                        totalQuotas = totalQuotas,
                        paymentMethod = payment.paymentMethod,
                        notes = payment.notes,
                        latitude = payment.collectedLatitude,
                        longitude = payment.collectedLongitude,
                        paymentTimestampMillis = payment.date
                    )

                    if (success) {
                        paymentDao.markPaymentSynced(payment.id)
                        syncedCount++
                        Log.d(tag, "Synced payment ID ${payment.id} for client $clientName to Supabase.")
                    } else {
                        val errMsg = "Error del servidor al subir pago #${payment.id}"
                        errors.add(errMsg)
                        Log.w(tag, errMsg)
                    }
                } catch (e: Exception) {
                    val errMsg = "Excepción al sincronizar pago #${payment.id}: ${e.message}"
                    errors.add(errMsg)
                    Log.e(tag, errMsg, e)
                }
            }

            val remainingPending = paymentDao.getUnsyncedPaymentsCount()
            _unsyncedPaymentsCount.value = remainingPending
            networkMonitor.updatePendingSyncCount(remainingPending)

            val now = System.currentTimeMillis()
            _lastSyncTimestamp.value = now

            if (errors.isEmpty()) {
                _syncState.value = SyncState.Success(
                    syncedCount = syncedCount,
                    timestamp = now,
                    message = "Se sincronizaron $syncedCount cobro(s) correctamente con Supabase"
                )
            } else {
                _syncState.value = SyncState.Error(
                    errorMessage = "Sincronización parcial: $syncedCount de $totalToSync completados. Errores: ${errors.size}",
                    pendingCount = remainingPending,
                    timestamp = now
                )
            }

            SyncResult(
                success = errors.isEmpty(),
                paymentsSynced = syncedCount,
                errors = errors,
                totalPendingRemaining = remainingPending
            )
        } finally {
            syncMutex.unlock()
        }
    }

    /**
     * Performs an INCREMENTAL DELTA SYNC (Fase 1):
     * 1. Pushes local pending offline Room payments to Supabase.
     * 2. Pulls ONLY modified or newly created invoice records from Supabase since lastDeltaSyncTimestamp.
     * 3. Merges delta records into local Room database without overwriting unaffected records.
     */
    suspend fun deltaSync(routeCode: String = "RUTA_01"): SyncResult = withContext(ioDispatcher) {
        val pushResult = syncUnsyncedChanges(routeCode)
        if (!pushResult.success && pushResult.errors.contains("Sin conexión a internet")) {
            return@withContext pushResult
        }

        var deltaRecordsMerged = 0
        val pullErrors = mutableListOf<String>()

        if (networkMonitor.isOnline.value) {
            try {
                val lastTimestamp = _lastSyncTimestamp.value
                val lastIso = if (lastTimestamp != null && lastTimestamp > 0) {
                    SupabaseGpsClient.formatToIso(lastTimestamp)
                } else null

                Log.d(tag, "Starting Incremental Delta Sync (lastIso: $lastIso) for route $routeCode")

                val jsonString = SupabaseGpsClient.fetchActiveInvoicesDelta(routeCode, lastIso)
                if (jsonString != null) {
                    val jsonArray = JSONArray(jsonString)
                    Log.d(tag, "Delta Sync received ${jsonArray.length()} modified/new record(s)")

                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        val clientId = item.optLong("client_id", item.optLong("clientId", if (item.has("id")) item.optLong("id") else (i + 1).toLong()))
                        val clientName = item.optString("client_name", item.optString("customer_name", item.optString("name", "Cliente #$clientId")))
                        val alias = item.optString("alias_or_business", item.optString("business_name", item.optString("alias", "")))
                        val phone = item.optString("phone", item.optString("telephone", ""))
                        val address = item.optString("address", item.optString("address_line", ""))

                        val lat = if (item.has("lat") && !item.isNull("lat")) item.optDouble("lat")
                            else if (item.has("latitude") && !item.isNull("latitude")) item.optDouble("latitude")
                            else if (item.has("collected_lat") && !item.isNull("collected_lat")) item.optDouble("collected_lat")
                            else null

                        val lng = if (item.has("lng") && !item.isNull("lng")) item.optDouble("lng")
                            else if (item.has("longitude") && !item.isNull("longitude")) item.optDouble("longitude")
                            else if (item.has("collected_lng") && !item.isNull("collected_lng")) item.optDouble("collected_lng")
                            else null

                        val existingClient = clientDao.getClientById(clientId)
                        val clientEntity = ClientEntity(
                            id = clientId,
                            name = clientName,
                            aliasOrBusiness = alias,
                            phone = phone,
                            address = address,
                            latitude = if (lat != null && !lat.isNaN()) lat else existingClient?.latitude,
                            longitude = if (lng != null && !lng.isNaN()) lng else existingClient?.longitude,
                            notes = item.optString("notes", existingClient?.notes ?: ""),
                            visitOrder = item.optInt("visit_order", item.optInt("order", existingClient?.visitOrder ?: (i + 1))),
                            isActive = true
                        )
                        clientDao.insertClient(clientEntity)

                        val loanId = item.optLong("loan_id", item.optLong("loanId", item.optLong("id", clientId)))
                        val amountBorrowed = item.optDouble("amount_borrowed", item.optDouble("amount", 1000000.0))
                        val interestRate = item.optDouble("interest_rate", 20.0)
                        val totalToPay = item.optDouble("total_to_pay", amountBorrowed * 1.20)
                        val totalPaid = item.optDouble("total_paid", 0.0)
                        val remainingBalance = item.optDouble("remaining_balance", totalToPay - totalPaid)
                        val totalQuotas = item.optInt("total_quotas", 24)
                        val quotaAmount = item.optDouble("quota_amount", totalToPay / totalQuotas)
                        val paidQuotas = item.optInt("paid_quotas", (totalPaid / (if (quotaAmount > 0) quotaAmount else 1.0)).toInt())

                        val existingLoan = loanDao.getLoanById(loanId)
                        val loanEntity = LoanEntity(
                            id = loanId,
                            clientId = clientId,
                            amountBorrowed = amountBorrowed,
                            interestRate = interestRate,
                            totalToPay = totalToPay,
                            totalPaid = if (existingLoan != null && existingLoan.totalPaid > totalPaid) existingLoan.totalPaid else totalPaid,
                            remainingBalance = if (existingLoan != null && existingLoan.remainingBalance < remainingBalance) existingLoan.remainingBalance else remainingBalance.coerceAtLeast(0.0),
                            quotaAmount = quotaAmount,
                            totalQuotas = totalQuotas,
                            paidQuotas = if (existingLoan != null && existingLoan.paidQuotas > paidQuotas) existingLoan.paidQuotas else paidQuotas,
                            frequency = item.optString("frequency", "DIARIO"),
                            status = if (remainingBalance <= 0.01) "PAID" else "ACTIVE"
                        )
                        loanDao.insertLoan(loanEntity)
                        deltaRecordsMerged++
                    }

                    // Update timestamp upon successful delta sync
                    _lastSyncTimestamp.value = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                val errMsg = "Error en Delta Sync Incremental: ${e.message}"
                pullErrors.add(errMsg)
                Log.e(tag, errMsg, e)
            }
        }

        val allErrors = pushResult.errors + pullErrors
        val remaining = paymentDao.getUnsyncedPaymentsCount()
        val finalResult = SyncResult(
            success = allErrors.isEmpty(),
            paymentsSynced = pushResult.paymentsSynced,
            invoicesMerged = deltaRecordsMerged,
            errors = allErrors,
            totalPendingRemaining = remaining
        )

        if (allErrors.isEmpty()) {
            _syncState.value = SyncState.Success(
                syncedCount = pushResult.paymentsSynced,
                message = "Delta Sync completado: ${pushResult.paymentsSynced} cobros subidos, $deltaRecordsMerged registros actualizados"
            )
        }

        finalResult
    }

    /**
     * Performs a full bi-directional sync:
     * 1. Pushes all unsynced local Room payments to Supabase.
     * 2. Pulls and refreshes active route invoices from Supabase into Room.
     */
    suspend fun fullSync(routeCode: String = "RUTA_01"): SyncResult = withContext(ioDispatcher) {
        val pushResult = syncUnsyncedChanges(routeCode)
        if (!pushResult.success && pushResult.errors.contains("Sin conexión a internet")) {
            return@withContext pushResult
        }

        // Pull active route data if online
        var invoicesMerged = 0
        val pullErrors = mutableListOf<String>()
        if (networkMonitor.isOnline.value) {
            try {
                val jsonString = SupabaseGpsClient.fetchActiveInvoicesForRoute(routeCode)
                if (jsonString != null) {
                    val jsonArray = JSONArray(jsonString)
                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        val clientId = item.optLong("client_id", item.optLong("clientId", if (item.has("id")) item.optLong("id") else (i + 1).toLong()))
                        val clientName = item.optString("client_name", item.optString("customer_name", item.optString("name", "Cliente #$clientId")))
                        val alias = item.optString("alias_or_business", item.optString("business_name", item.optString("alias", "")))
                        val phone = item.optString("phone", item.optString("telephone", ""))
                        val address = item.optString("address", item.optString("address_line", ""))

                        val lat = if (item.has("lat") && !item.isNull("lat")) item.optDouble("lat")
                            else if (item.has("latitude") && !item.isNull("latitude")) item.optDouble("latitude")
                            else if (item.has("collected_lat") && !item.isNull("collected_lat")) item.optDouble("collected_lat")
                            else null

                        val lng = if (item.has("lng") && !item.isNull("lng")) item.optDouble("lng")
                            else if (item.has("longitude") && !item.isNull("longitude")) item.optDouble("longitude")
                            else if (item.has("collected_lng") && !item.isNull("collected_lng")) item.optDouble("collected_lng")
                            else null

                        val existingClient = clientDao.getClientById(clientId)
                        val clientEntity = ClientEntity(
                            id = clientId,
                            name = clientName,
                            aliasOrBusiness = alias,
                            phone = phone,
                            address = address,
                            latitude = if (lat != null && !lat.isNaN()) lat else existingClient?.latitude,
                            longitude = if (lng != null && !lng.isNaN()) lng else existingClient?.longitude,
                            notes = item.optString("notes", existingClient?.notes ?: ""),
                            visitOrder = item.optInt("visit_order", item.optInt("order", existingClient?.visitOrder ?: (i + 1))),
                            isActive = true
                        )
                        clientDao.insertClient(clientEntity)

                        val loanId = item.optLong("loan_id", item.optLong("loanId", item.optLong("id", clientId)))
                        val amountBorrowed = item.optDouble("amount_borrowed", item.optDouble("amount", 1000000.0))
                        val interestRate = item.optDouble("interest_rate", 20.0)
                        val totalToPay = item.optDouble("total_to_pay", amountBorrowed * 1.20)
                        val totalPaid = item.optDouble("total_paid", 0.0)
                        val remainingBalance = item.optDouble("remaining_balance", totalToPay - totalPaid)
                        val totalQuotas = item.optInt("total_quotas", 24)
                        val quotaAmount = item.optDouble("quota_amount", totalToPay / totalQuotas)
                        val paidQuotas = item.optInt("paid_quotas", (totalPaid / (if (quotaAmount > 0) quotaAmount else 1.0)).toInt())

                        val existingLoan = loanDao.getLoanById(loanId)
                        val loanEntity = LoanEntity(
                            id = loanId,
                            clientId = clientId,
                            amountBorrowed = amountBorrowed,
                            interestRate = interestRate,
                            totalToPay = totalToPay,
                            totalPaid = if (existingLoan != null && existingLoan.totalPaid > totalPaid) existingLoan.totalPaid else totalPaid,
                            remainingBalance = if (existingLoan != null && existingLoan.remainingBalance < remainingBalance) existingLoan.remainingBalance else remainingBalance.coerceAtLeast(0.0),
                            quotaAmount = quotaAmount,
                            totalQuotas = totalQuotas,
                            paidQuotas = if (existingLoan != null && existingLoan.paidQuotas > paidQuotas) existingLoan.paidQuotas else paidQuotas,
                            frequency = item.optString("frequency", "DIARIO"),
                            status = if (remainingBalance <= 0.01) "PAID" else "ACTIVE"
                        )
                        loanDao.insertLoan(loanEntity)
                        invoicesMerged++
                    }

                    _lastSyncTimestamp.value = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                val errMsg = "Error al descargar datos de ruta desde Supabase: ${e.message}"
                pullErrors.add(errMsg)
                Log.e(tag, errMsg, e)
            }
        }

        val allErrors = pushResult.errors + pullErrors
        val remaining = paymentDao.getUnsyncedPaymentsCount()
        SyncResult(
            success = allErrors.isEmpty(),
            paymentsSynced = pushResult.paymentsSynced,
            invoicesMerged = invoicesMerged,
            errors = allErrors,
            totalPendingRemaining = remaining
        )
    }
}
