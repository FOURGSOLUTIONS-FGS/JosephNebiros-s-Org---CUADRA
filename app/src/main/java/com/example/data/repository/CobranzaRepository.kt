package com.example.data.repository

import com.example.data.db.AppDatabase
import com.example.data.model.ClientEntity
import com.example.data.model.ClientWithActiveLoan
import com.example.data.model.ExpenseEntity
import com.example.data.model.LoanEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.RoutePointEntity
import com.example.data.model.TrackingSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

    // Combine clients, active loans, and today payments for reactive daily route list
    fun getDailyRouteClientsWithLoans(): Flow<List<ClientWithActiveLoan>> {
        val (startOfDay, endOfDay) = getTodayStartAndEndTimestamps()
        val clientsFlow = clientDao.getAllActiveClients()
        val loansFlow = loanDao.getAllActiveLoans()
        val todayPaymentsFlow = paymentDao.getPaymentsForDay(startOfDay, endOfDay)

        return combine(clientsFlow, loansFlow, todayPaymentsFlow) { clients, loans, payments ->
            clients.map { client ->
                val activeLoan = loans.find { it.clientId == client.id }
                val todayPayment = if (activeLoan != null) {
                    payments.find { it.loanId == activeLoan.id }
                } else null
                ClientWithActiveLoan(
                    client = client,
                    activeLoan = activeLoan,
                    todayPayment = todayPayment,
                    isCollectedToday = todayPayment != null
                )
            }
        }
    }

    fun getAllClients(): Flow<List<ClientEntity>> = clientDao.getAllClients()

    suspend fun getClientById(id: Long): ClientEntity? = clientDao.getClientById(id)

    suspend fun saveClient(client: ClientEntity): Long = clientDao.insertClient(client)

    suspend fun updateClient(client: ClientEntity) = clientDao.updateClient(client)

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
        latitude: Double? = null,
        longitude: Double? = null
    ): Long {
        val payment = PaymentEntity(
            loanId = loanId,
            clientId = clientId,
            amount = amount,
            date = System.currentTimeMillis(),
            quotaNumber = quotaNumber,
            notes = notes,
            collectedLatitude = latitude,
            collectedLongitude = longitude,
            paymentMethod = paymentMethod
        )
        val paymentId = paymentDao.insertPayment(payment)
        loanDao.recordPaymentOnLoan(loanId, amount)
        return paymentId
    }

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
}
