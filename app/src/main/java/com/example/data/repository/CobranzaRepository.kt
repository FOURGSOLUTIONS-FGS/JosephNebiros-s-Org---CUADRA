package com.example.data.repository

import android.util.Log
import com.example.data.db.AppDatabase
import com.example.data.model.ClientEntity
import com.example.data.model.ClientWithActiveLoan
import com.example.data.model.ExpenseEntity
import com.example.data.model.LoanEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.ReminderEntity
import com.example.data.model.RoutePointEntity
import com.example.data.model.TrackingSessionEntity
import com.example.service.SupabaseGpsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class CobranzaRepository(private val database: AppDatabase) {
    private val clientDao = database.clientDao()
    private val loanDao = database.loanDao()
    private val paymentDao = database.paymentDao()
    private val routeDao = database.routeDao()
    private val expenseDao = database.expenseDao()
    private val reminderDao = database.reminderDao()

    fun getTodayStartAndEndTimestamps(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis

        return Pair(startOfDay, endOfDay)
    }

    fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    // Combine clients, active loans, reminders, and today payments for reactive daily route list
    fun getDailyRouteClientsWithLoans(): Flow<List<ClientWithActiveLoan>> {
        val (startOfDay, endOfDay) = getTodayStartAndEndTimestamps()
        val clientsFlow = clientDao.getAllActiveClients()
        val loansFlow = loanDao.getAllActiveLoans()
        val todayPaymentsFlow = paymentDao.getPaymentsForDay(startOfDay, endOfDay)
        val pendingRemindersFlow = reminderDao.getPendingReminders()

        return combine(clientsFlow, loansFlow, todayPaymentsFlow, pendingRemindersFlow) { clients, loans, payments, reminders ->
            clients.map { client ->
                val activeLoan = loans.find { it.clientId == client.id }
                val todayPayment = if (activeLoan != null) {
                    payments.find { it.loanId == activeLoan.id }
                } else null
                val clientRemindersCount = reminders.count { it.clientId == client.id }
                ClientWithActiveLoan(
                    client = client,
                    activeLoan = activeLoan,
                    todayPayment = todayPayment,
                    isCollectedToday = todayPayment != null,
                    pendingRemindersCount = clientRemindersCount
                )
            }
        }
    }

    fun getAllClients(): Flow<List<ClientEntity>> = clientDao.getAllClients()

    suspend fun getClientById(id: Long): ClientEntity? = clientDao.getClientById(id)

    suspend fun saveClient(client: ClientEntity): Long = clientDao.insertClient(client)

    suspend fun updateClient(client: ClientEntity) = clientDao.updateClient(client)

    suspend fun updateClientPhoto(clientId: Long, photoUri: String) {
        val client = clientDao.getClientById(clientId)
        if (client != null) {
            clientDao.updateClient(client.copy(photoUri = photoUri))
        }
    }

    suspend fun deleteClient(client: ClientEntity) = clientDao.deleteClient(client)

    fun getLoansForClient(clientId: Long): Flow<List<LoanEntity>> = loanDao.getLoansForClient(clientId)

    suspend fun getActiveLoanForClient(clientId: Long): LoanEntity? = loanDao.getActiveLoanForClient(clientId)

    suspend fun createLoan(loan: LoanEntity): Long = loanDao.insertLoan(loan)

    suspend fun recordPayment(
        loanId: Long,
        clientId: Long,
        amount: Double,
        quotaNumber: Int,
        notes: String = "",
        paymentMethod: String = "EFECTIVO",
        photoUri: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ): Long {
        if (amount <= 0.0 || amount.isNaN() || amount.isInfinite()) return -1L
        val payment = PaymentEntity(
            loanId = loanId,
            clientId = clientId,
            amount = amount,
            date = System.currentTimeMillis(),
            quotaNumber = quotaNumber,
            notes = notes,
            collectedLatitude = latitude,
            collectedLongitude = longitude,
            paymentMethod = paymentMethod,
            photoUri = photoUri,
            isSyncedWithCloud = false
        )
        val paymentId = paymentDao.insertPayment(payment)
        loanDao.recordPaymentOnLoan(loanId, amount)

        // Asynchronously sync with Supabase (update invoices table & insert into cash_drawer)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val updatedLoan = loanDao.getLoanById(loanId)
                val client = clientDao.getClientById(clientId)
                val clientName = client?.name ?: "Cliente #$clientId"

                val totalPaid = updatedLoan?.totalPaid ?: amount
                val remainingBalance = updatedLoan?.remainingBalance ?: 0.0
                val paidQuotas = updatedLoan?.paidQuotas ?: quotaNumber
                val totalQuotas = updatedLoan?.totalQuotas ?: 24

                val success = SupabaseGpsClient.recordPaymentToSupabase(
                    loanId = loanId,
                    clientId = clientId,
                    clientName = clientName,
                    amount = amount,
                    quotaNumber = quotaNumber,
                    totalPaid = totalPaid,
                    remainingBalance = remainingBalance,
                    paidQuotas = paidQuotas,
                    totalQuotas = totalQuotas,
                    paymentMethod = paymentMethod,
                    notes = notes,
                    latitude = latitude,
                    longitude = longitude
                )
                if (success) {
                    paymentDao.markPaymentSynced(paymentId)
                }
            } catch (e: Exception) {
                Log.e("CobranzaRepository", "Error syncing payment to Supabase (stored offline): ${e.message}")
            }
        }

        return paymentId
    }

    /**
     * Sincroniza todos los pagos que se registraron mientras el dispositivo estaba offline.
     */
    suspend fun syncPendingOfflinePayments(): Int = withContext(Dispatchers.IO) {
        val unsynced = paymentDao.getUnsyncedPayments()
        var syncedCount = 0
        for (payment in unsynced) {
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
                    paymentMethod = paymentMethodToLabel(payment.paymentMethod),
                    notes = payment.notes,
                    latitude = payment.collectedLatitude,
                    longitude = payment.collectedLongitude
                )
                if (success) {
                    paymentDao.markPaymentSynced(payment.id)
                    syncedCount++
                }
            } catch (e: Exception) {
                Log.e("CobranzaRepository", "Failed sync offline payment ${payment.id}: ${e.message}")
            }
        }
        syncedCount
    }

    private fun paymentMethodToLabel(method: String): String = method

    suspend fun getUnsyncedPaymentsCount(): Int = withContext(Dispatchers.IO) {
        paymentDao.getUnsyncedPayments().size
    }

    // Reminders
    fun getAllReminders(): Flow<List<ReminderEntity>> = reminderDao.getAllReminders()

    fun getPendingReminders(): Flow<List<ReminderEntity>> = reminderDao.getPendingReminders()

    fun getRemindersForClient(clientId: Long): Flow<List<ReminderEntity>> = reminderDao.getRemindersForClient(clientId)

    suspend fun createReminder(
        clientId: Long,
        clientName: String,
        title: String,
        dueTimeFormatted: String,
        dueDateMillis: Long = System.currentTimeMillis(),
        notes: String = "",
        priority: String = "NORMAL"
    ): Long {
        val reminder = ReminderEntity(
            clientId = clientId,
            clientName = clientName,
            title = title,
            dueTimeFormatted = dueTimeFormatted,
            dueDateMillis = dueDateMillis,
            isCompleted = false,
            notes = notes,
            priority = priority
        )
        return reminderDao.insertReminder(reminder)
    }

    suspend fun setReminderCompleted(reminderId: Long, isCompleted: Boolean) {
        reminderDao.setReminderCompleted(reminderId, isCompleted)
    }

    suspend fun deleteReminder(reminder: ReminderEntity) {
        reminderDao.deleteReminder(reminder)
    }

    fun getAllPayments(): Flow<List<PaymentEntity>> = paymentDao.getAllPayments()

    fun getPaymentsForClient(clientId: Long): Flow<List<PaymentEntity>> = paymentDao.getPaymentsForClient(clientId)

    fun getTodayPayments(): Flow<List<PaymentEntity>> {
        val (startOfDay, endOfDay) = getTodayStartAndEndTimestamps()
        return paymentDao.getPaymentsForDay(startOfDay, endOfDay)
    }

    fun getTodayExpenses(): Flow<List<ExpenseEntity>> {
        val (startOfDay, endOfDay) = getTodayStartAndEndTimestamps()
        return expenseDao.getExpensesForDay(startOfDay, endOfDay)
    }

    suspend fun addExpense(concept: String, amount: Double, category: String = "RUTA"): Long {
        val expense = ExpenseEntity(
            concept = concept,
            amount = amount,
            date = System.currentTimeMillis(),
            category = category
        )
        return expenseDao.insertExpense(expense)
    }

    suspend fun deleteExpense(expense: ExpenseEntity) = expenseDao.deleteExpense(expense)

    // Route tracking methods
    suspend fun recordRoutePoint(
        sessionId: String,
        latitude: Double,
        longitude: Double,
        speed: Float,
        altitude: Double,
        accuracy: Float
    ) {
        val point = RoutePointEntity(
            sessionId = sessionId,
            latitude = latitude,
            longitude = longitude,
            speed = speed,
            altitude = altitude,
            accuracy = accuracy,
            timestamp = System.currentTimeMillis()
        )
        routeDao.insertPoint(point)

        // Update session distance & point count
        val session = routeDao.getSessionById(sessionId)
        if (session != null) {
            val allPoints = routeDao.getPointsForSessionSync(sessionId)
            val distance = calculateTotalDistance(allPoints)
            val updatedSession = session.copy(
                totalDistanceMeters = distance,
                pointsCount = allPoints.size,
                isActive = true
            )
            routeDao.insertOrUpdateSession(updatedSession)
        } else {
            val newSession = TrackingSessionEntity(
                sessionId = sessionId,
                dateFormatted = getTodayDateString(),
                startTime = System.currentTimeMillis(),
                totalDistanceMeters = 0.0,
                pointsCount = 1,
                isActive = true
            )
            routeDao.insertOrUpdateSession(newSession)
        }
    }

    fun getRoutePointsForSession(sessionId: String): Flow<List<RoutePointEntity>> {
        return routeDao.getPointsForSession(sessionId)
    }

    fun getAllTrackingSessions(): Flow<List<TrackingSessionEntity>> {
        return routeDao.getAllSessions()
    }

    suspend fun getActiveTrackingSession(): TrackingSessionEntity? {
        return routeDao.getActiveSession()
    }

    suspend fun startNewTrackingSession(sessionId: String): TrackingSessionEntity {
        val session = TrackingSessionEntity(
            sessionId = sessionId,
            dateFormatted = getTodayDateString(),
            startTime = System.currentTimeMillis(),
            isActive = true
        )
        routeDao.insertOrUpdateSession(session)
        return session
    }

    suspend fun stopTrackingSession(sessionId: String) {
        val session = routeDao.getSessionById(sessionId)
        if (session != null) {
            val updated = session.copy(
                endTime = System.currentTimeMillis(),
                isActive = false
            )
            routeDao.insertOrUpdateSession(updated)
        }
    }

    private fun calculateTotalDistance(points: List<RoutePointEntity>): Double {
        if (points.size < 2) return 0.0
        var totalDist = 0.0
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            totalDist += haversineDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
        }
        return totalDist
    }

    private fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Consulta GET /rest/v1/invoices?route_code=eq.${routeCode}&status=eq.ACTIVA en Supabase
     * y actualiza la base de datos local de Room para permitir funcionamiento offline en caso
     * de pérdida de conectividad en la calle.
     */
    suspend fun syncRouteFromSupabase(routeCode: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val jsonString = SupabaseGpsClient.fetchActiveInvoicesForRoute(routeCode)
                ?: return@withContext Result.failure(Exception("Sin respuesta del servidor Supabase o fallo de red."))

            val jsonArray = JSONArray(jsonString)
            var syncedCount = 0

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)

                // 1. Extraer datos del cliente
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

                val notes = item.optString("notes", "")
                val visitOrder = item.optInt("visit_order", item.optInt("order", i + 1))

                val clientEntity = ClientEntity(
                    id = clientId,
                    name = clientName,
                    aliasOrBusiness = alias,
                    phone = phone,
                    address = address,
                    latitude = if (lat != null && !lat.isNaN()) lat else null,
                    longitude = if (lng != null && !lng.isNaN()) lng else null,
                    notes = notes,
                    visitOrder = visitOrder,
                    isActive = true
                )
                clientDao.insertClient(clientEntity)

                // 2. Extraer datos del préstamo / factura
                val loanId = item.optLong("loan_id", item.optLong("loanId", item.optLong("id", clientId)))
                val amountBorrowed = item.optDouble("amount_borrowed", item.optDouble("amount", item.optDouble("principal", 1000000.0)))
                val interestRate = item.optDouble("interest_rate", item.optDouble("interest", 20.0))
                val totalToPay = item.optDouble("total_to_pay", item.optDouble("total_amount", amountBorrowed * (1.0 + (interestRate / 100.0))))
                val totalPaid = item.optDouble("total_paid", item.optDouble("amount_paid", 0.0))
                val remainingBalance = item.optDouble("remaining_balance", item.optDouble("balance", totalToPay - totalPaid))
                val totalQuotas = item.optInt("total_quotas", 24)
                val quotaAmount = item.optDouble("quota_amount", item.optDouble("daily_quota", totalToPay / totalQuotas))
                val paidQuotas = item.optInt("paid_quotas", (totalPaid / (if (quotaAmount > 0) quotaAmount else 1.0)).toInt())
                val frequency = item.optString("frequency", "DIARIO")

                val loanEntity = LoanEntity(
                    id = loanId,
                    clientId = clientId,
                    amountBorrowed = amountBorrowed,
                    interestRate = interestRate,
                    totalToPay = totalToPay,
                    totalPaid = totalPaid,
                    remainingBalance = remainingBalance.coerceAtLeast(0.0),
                    quotaAmount = quotaAmount,
                    totalQuotas = totalQuotas,
                    paidQuotas = paidQuotas,
                    frequency = frequency,
                    status = "ACTIVE"
                )
                loanDao.insertLoan(loanEntity)
                syncedCount++
            }

            Log.d("CobranzaRepository", "Sincronizados $syncedCount clientes/facturas desde Supabase a Room para ruta $routeCode.")
            Result.success(syncedCount)
        } catch (e: Exception) {
            Log.e("CobranzaRepository", "Error sincronizando ruta desde Supabase: ${e.message}", e)
            Result.failure(e)
        }
    }
}
